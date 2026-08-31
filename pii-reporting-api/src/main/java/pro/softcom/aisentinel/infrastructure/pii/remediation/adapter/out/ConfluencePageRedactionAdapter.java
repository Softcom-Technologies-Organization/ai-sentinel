package pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceClient;
import pro.softcom.aisentinel.application.pii.remediation.port.out.SourcePageRedactionPort;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage.HtmlContent;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage.PageMetadata;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.ConfluenceApiException;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out.StorageContentRedactor.RedactionGuardConfig;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out.StorageContentRedactor.RedactionResult;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out.StorageContentRedactor.ValueOutcome;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Redacts PII values inside Confluence pages: reads the current storage body and
 * version through the existing {@link ConfluenceClient}, delegates the markup-safe
 * rewriting to {@link StorageContentRedactor} and writes back with
 * {@code version.number = current + 1} (the increment is confined to this adapter;
 * the shared page mapper serialises the version untouched).
 *
 * <p>A version-conflict 409 on the PUT triggers exactly one re-read and retry, after
 * which the page is reported {@code STALE}. Transient 5xx/429 responses are already
 * retried inside the client. Errors never expose page content or PII values.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfluencePageRedactionAdapter implements SourcePageRedactionPort {

    private static final int HTTP_CONFLICT = 409;

    private final ConfluenceClient confluenceClient;
    private final StorageContentRedactor storageContentRedactor;

    @Override
    public PageRedactionResult redactPage(String pageId, List<ValueReplacement> replacements) {
        try {
            return redactWithSingleRetry(pageId, replacements);
        } catch (VersionConflictException _) {
            log.warn("[PII_REMEDIATION] Page {} still conflicting after one retry, reported stale", pageId);
            return PageRedactionResult.stale();
        } catch (Exception e) {
            log.error("[PII_REMEDIATION] Redaction of page {} failed: {}", pageId, describeFailure(e));
            return PageRedactionResult.failed();
        }
    }

    /**
     * Describes a redaction failure for logging. The HTTP status of a {@link ConfluenceApiException}
     * is included because it is the primary diagnostic signal (400 payload vs 403 permission vs 404)
     * and, unlike the Confluence response body, cannot carry page content or PII values.
     */
    private static String describeFailure(Exception e) {
        if (e instanceof ConfluenceApiException apiException) {
            return "ConfluenceApiException(status=" + apiException.getStatusCode() + ")";
        }
        return e.getClass().getSimpleName();
    }

    private PageRedactionResult redactWithSingleRetry(String pageId, List<ValueReplacement> replacements) {
        try {
            return redactOnce(pageId, replacements);
        } catch (VersionConflictException _) {
            log.info("[PII_REMEDIATION] Version conflict on page {}, re-reading and retrying once", pageId);
            return redactOnce(pageId, replacements);
        }
    }

    private PageRedactionResult redactOnce(String pageId, List<ValueReplacement> replacements) {
        ConfluencePage page = fetchPage(pageId);
        RedactionResult result = storageContentRedactor.redact(page.content().body(),
                toRedactorReplacements(replacements), RedactionGuardConfig.defaults());
        List<ValueRedactionStatus> valueStatuses = toValueStatuses(result.outcomes());
        if (valueStatuses.stream().noneMatch(status -> status == ValueRedactionStatus.REDACTED)) {
            return new PageRedactionResult(PageRedactionStatus.NO_MATCHES, valueStatuses);
        }
        updatePage(page, result.redactedXhtml());
        return new PageRedactionResult(PageRedactionStatus.UPDATED, valueStatuses);
    }

    private ConfluencePage fetchPage(String pageId) {
        ConfluencePage page = join(confluenceClient.getPage(pageId))
                .orElseThrow(() -> new IllegalStateException("Confluence page not readable: " + pageId));
        if (page.content() == null || page.metadata() == null) {
            throw new IllegalStateException("Confluence page missing storage body or version: " + pageId);
        }
        return page;
    }

    private void updatePage(ConfluencePage page, String redactedBody) {
        ConfluencePage updated = ConfluencePage.builder()
                .id(page.id())
                .title(page.title())
                .spaceKey(page.spaceKey())
                .content(new HtmlContent(redactedBody))
                .metadata(withIncrementedVersion(page.metadata()))
                .labels(page.labels())
                .customProperties(page.customProperties())
                .build();
        join(confluenceClient.updatePage(updated));
    }

    private static PageMetadata withIncrementedVersion(PageMetadata metadata) {
        return new PageMetadata(metadata.createdBy(), metadata.createdDate(),
                metadata.lastModifiedBy(), metadata.lastModifiedDate(),
                metadata.version() + 1, metadata.status());
    }

    private static List<StorageContentRedactor.ValueReplacement> toRedactorReplacements(
            List<ValueReplacement> replacements) {
        return replacements.stream()
                .map(replacement -> new StorageContentRedactor.ValueReplacement(
                        replacement.plaintextValue(), replacement.token()))
                .toList();
    }

    private static List<ValueRedactionStatus> toValueStatuses(List<ValueOutcome> outcomes) {
        return outcomes.stream()
                .map(outcome -> outcome.found()
                        ? ValueRedactionStatus.REDACTED
                        : ValueRedactionStatus.VALUE_NOT_FOUND)
                .toList();
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            throw asBusinessException(e.getCause() == null ? e : e.getCause());
        }
    }

    private static RuntimeException asBusinessException(Throwable cause) {
        if (cause instanceof ConfluenceApiException apiException
                && apiException.getStatusCode() == HTTP_CONFLICT) {
            return new VersionConflictException();
        }
        return cause instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException("Confluence call failed", cause);
    }

    private static final class VersionConflictException extends RuntimeException {
    }
}
