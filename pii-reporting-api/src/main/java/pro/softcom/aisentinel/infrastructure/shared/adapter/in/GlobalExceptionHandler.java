package pro.softcom.aisentinel.infrastructure.shared.adapter.in;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pro.softcom.aisentinel.application.confluence.exception.ConfluencePageNotFoundException;
import pro.softcom.aisentinel.application.confluence.exception.ConfluenceRequestFailedException;
import pro.softcom.aisentinel.application.confluence.exception.ConfluenceSpaceCacheException;
import pro.softcom.aisentinel.application.confluence.exception.ConfluenceSpaceNotFoundException;
import pro.softcom.aisentinel.application.pii.export.exception.ExportContextNotFoundException;
import pro.softcom.aisentinel.application.pii.export.exception.ExportException;
import pro.softcom.aisentinel.application.pii.export.exception.UnsupportedSourceTypeException;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorException;
import pro.softcom.aisentinel.domain.pii.remediation.AttachmentRedactionUnsupportedException;
import pro.softcom.aisentinel.domain.pii.remediation.IllegalStatusTransitionException;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationJobAlreadyRunningException;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationJobNotFoundException;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationDisabledException;
import pro.softcom.aisentinel.domain.pii.remediation.SelectionOutdatedException;
import pro.softcom.aisentinel.domain.pii.scan.IllegalScanStatusTransitionException;
import pro.softcom.aisentinel.domain.pii.scan.ScanNotFoundException;
import pro.softcom.aisentinel.domain.pii.scan.ScanSpaceStatsNotFoundException;
import pro.softcom.aisentinel.domain.pii.security.CryptographicOperationException;
import pro.softcom.aisentinel.domain.pii.security.EncryptionException;
import pro.softcom.aisentinel.domain.pii.security.PageSecretsNotFoundException;
import pro.softcom.aisentinel.domain.pii.security.PiiAccessDeniedException;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.ConfluenceApiException;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.ConfluenceAuthenticationException;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.ConfluenceConnectionException;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.ConfluenceDateParseException;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.ConfluenceNotFoundException;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.parser.ConfluenceDeserializationException;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.PiiDetectionException;

/**
 * Central exception handler mapping all backend exceptions to RFC 9457 ProblemDetail responses.
 * Each handler sets an errorKey property used by the frontend for translation.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConfluenceAuthenticationException.class)
    ProblemDetail handleConfluenceAuth(ConfluenceAuthenticationException ex) {
        log.warn("[ERROR_HANDLER] Confluence authentication failed: {}", ex.getMessage());
        return problemWith(HttpStatus.UNAUTHORIZED, "Confluence Authentication Failed",
                "error.confluence.auth.failed");
    }

    @ExceptionHandler(ConfluenceConnectionException.class)
    ProblemDetail handleConfluenceConnection(ConfluenceConnectionException ex) {
        log.error("[ERROR_HANDLER] Confluence connection failed: {}", ex.getMessage());
        return problemWith(HttpStatus.SERVICE_UNAVAILABLE, "Confluence Connection Failed",
                "error.confluence.connection.failed");
    }

    @ExceptionHandler(ConfluenceNotFoundException.class)
    ProblemDetail handleConfluenceNotFound(ConfluenceNotFoundException ex) {
        log.warn("[ERROR_HANDLER] Confluence resource not found: {}", ex.getMessage());
        return problemWith(HttpStatus.NOT_FOUND, "Confluence Resource Not Found",
                "error.confluence.resource.not_found");
    }

    @ExceptionHandler(ConfluenceApiException.class)
    ProblemDetail handleConfluenceApi(ConfluenceApiException ex) {
        log.error("[ERROR_HANDLER] Confluence API error: {}", ex.getMessage());
        return problemWith(HttpStatus.BAD_GATEWAY, "Confluence API Error",
                "error.confluence.api.error");
    }

    @ExceptionHandler(ConfluenceRequestFailedException.class)
    ProblemDetail handleConfluenceRequestFailed(ConfluenceRequestFailedException ex) {
        log.error("[ERROR_HANDLER] Confluence rejected the request: {}", ex.getMessage());
        return problemWith(HttpStatus.BAD_GATEWAY, "Confluence API Error",
                "error.confluence.api.error");
    }

    @ExceptionHandler(ConfluenceDateParseException.class)
    ProblemDetail handleConfluenceDateParse(ConfluenceDateParseException ex) {
        log.error("[ERROR_HANDLER] Confluence date parse failed: {}", ex.getMessage());
        return problemWith(HttpStatus.BAD_GATEWAY, "Confluence Date Parse Failed",
                "error.confluence.date.parse_failed");
    }

    @ExceptionHandler(ConfluencePageNotFoundException.class)
    ProblemDetail handleConfluencePageNotFound(ConfluencePageNotFoundException ex) {
        log.warn("[ERROR_HANDLER] Confluence page not found: {}", ex.getMessage());
        return problemWith(HttpStatus.NOT_FOUND, "Confluence Page Not Found",
                "error.confluence.resource.not_found");
    }

    @ExceptionHandler(ConfluenceDeserializationException.class)
    ProblemDetail handleConfluenceDeserialization(ConfluenceDeserializationException ex) {
        log.error("[ERROR_HANDLER] Confluence deserialization failed: {}", ex.getMessage());
        return problemWith(HttpStatus.BAD_GATEWAY, "Confluence Deserialization Failed",
                "error.confluence.deserialization.failed");
    }

    @ExceptionHandler(ConfluenceSpaceNotFoundException.class)
    ProblemDetail handleConfluenceSpaceNotFound(ConfluenceSpaceNotFoundException ex) {
        log.warn("[ERROR_HANDLER] Confluence space not found: {}", ex.getMessage());
        return problemWith(HttpStatus.NOT_FOUND, "Confluence Space Not Found",
                "error.confluence.space.not_found");
    }

    @ExceptionHandler(ConfluenceSpaceCacheException.class)
    ProblemDetail handleConfluenceSpaceCache(ConfluenceSpaceCacheException ex) {
        log.error("[ERROR_HANDLER] Confluence space cache error: {}", ex.getMessage());
        return problemWith(HttpStatus.SERVICE_UNAVAILABLE, "Confluence Space Cache Error",
                "error.confluence.space.cache_error");
    }

    @ExceptionHandler(PiiDetectionException.PiiDetectionConnectionException.class)
    ProblemDetail handlePiiDetectionConnection(PiiDetectionException.PiiDetectionConnectionException ex) {
        log.error("[ERROR_HANDLER] PII detection connection failed: {}", ex.getMessage());
        return problemWith(HttpStatus.SERVICE_UNAVAILABLE, "PII Detection Connection Failed",
                "error.pii.detection.connection_failed");
    }

    @ExceptionHandler(PiiDetectionException.PiiDetectionServiceException.class)
    ProblemDetail handlePiiDetectionService(PiiDetectionException.PiiDetectionServiceException ex) {
        log.error("[ERROR_HANDLER] PII detection service error: {}", ex.getMessage());
        return problemWith(HttpStatus.BAD_GATEWAY, "PII Detection Service Error",
                "error.pii.detection.service_error");
    }

    @ExceptionHandler(PiiDetectionException.PiiDetectionTimeoutException.class)
    ProblemDetail handlePiiDetectionTimeout(PiiDetectionException.PiiDetectionTimeoutException ex) {
        log.error("[ERROR_HANDLER] PII detection timeout: {}", ex.getMessage());
        return problemWith(HttpStatus.GATEWAY_TIMEOUT, "PII Detection Timeout",
                "error.pii.detection.timeout");
    }

    @ExceptionHandler(PiiDetectorException.class)
    ProblemDetail handlePiiDetector(PiiDetectorException ex) {
        log.error("[ERROR_HANDLER] PII detector error: {}", ex.getMessage());
        return problemWith(HttpStatus.BAD_GATEWAY, "PII Detector Error",
                "error.pii.detector.error");
    }

    @ExceptionHandler(EncryptionException.class)
    ProblemDetail handleEncryption(EncryptionException ex) {
        log.error("[ERROR_HANDLER] Encryption failed: {}", ex.getMessage());
        return problemWith(HttpStatus.INTERNAL_SERVER_ERROR, "Encryption Failed",
                "error.encryption.failed");
    }

    @ExceptionHandler(CryptographicOperationException.class)
    ProblemDetail handleCryptoOperation(CryptographicOperationException ex) {
        log.error("[ERROR_HANDLER] Cryptographic operation failed: {}", ex.getMessage());
        return problemWith(HttpStatus.INTERNAL_SERVER_ERROR, "Cryptographic Operation Failed",
                "error.crypto.operation_failed");
    }

    @ExceptionHandler(PiiAccessDeniedException.class)
    ProblemDetail handlePiiAccessDenied(PiiAccessDeniedException ex) {
        log.warn("[ERROR_HANDLER] PII access denied: {}", ex.getMessage());
        return problemWith(HttpStatus.FORBIDDEN, "PII Access Denied",
                "error.access.denied");
    }

    @ExceptionHandler(PageSecretsNotFoundException.class)
    ProblemDetail handlePageSecretsNotFound(PageSecretsNotFoundException ex) {
        log.warn("[ERROR_HANDLER] Page secrets not found: {}", ex.getMessage());
        return problemWith(HttpStatus.NOT_FOUND, "Page Secrets Not Found",
                "error.pii.page_secrets_not_found");
    }

    @ExceptionHandler(IllegalScanStatusTransitionException.class)
    ProblemDetail handleIllegalScanStatusTransition(IllegalScanStatusTransitionException ex) {
        log.warn("[ERROR_HANDLER] Illegal scan status transition: {}", ex.getMessage());
        return problemWith(HttpStatus.CONFLICT, "Invalid Scan Status Transition",
                "error.scan.invalid_status_transition");
    }

    @ExceptionHandler(ScanNotFoundException.class)
    ProblemDetail handleScanNotFound(ScanNotFoundException ex) {
        log.warn("[ERROR_HANDLER] Scan not found: {}", ex.getMessage());
        return problemWith(HttpStatus.NOT_FOUND, "Scan Not Found",
                "error.scan.not_found");
    }

    @ExceptionHandler(ScanSpaceStatsNotFoundException.class)
    ProblemDetail handleScanSpaceStatsNotFound(ScanSpaceStatsNotFoundException ex) {
        log.warn("[ERROR_HANDLER] Scan space stats not found: {}", ex.getMessage());
        return problemWith(HttpStatus.NOT_FOUND, "Scan Space Stats Not Found",
                "error.scan.stats_not_found");
    }

    @ExceptionHandler(RemediationDisabledException.class)
    ProblemDetail handleRemediationDisabled(RemediationDisabledException ex) {
        log.warn("[ERROR_HANDLER] Remediation disabled: {}", ex.getMessage());
        return problemWith(HttpStatus.FORBIDDEN, "Remediation Disabled",
                "error.remediation.disabled");
    }

    @ExceptionHandler(IllegalStatusTransitionException.class)
    ProblemDetail handleIllegalFindingStatusTransition(IllegalStatusTransitionException ex) {
        log.warn("[ERROR_HANDLER] Illegal finding status transition: {}", ex.getMessage());
        return problemWith(HttpStatus.CONFLICT, "Invalid Finding Status Transition",
                "error.remediation.invalid_status_transition");
    }

    @ExceptionHandler(AttachmentRedactionUnsupportedException.class)
    ProblemDetail handleAttachmentRedactionUnsupported(AttachmentRedactionUnsupportedException ex) {
        log.warn("[ERROR_HANDLER] Attachment redaction unsupported: {}", ex.getMessage());
        return problemWith(HttpStatus.UNPROCESSABLE_ENTITY, "Attachment Redaction Unsupported",
                "error.remediation.attachment_not_supported");
    }

    @ExceptionHandler(SelectionOutdatedException.class)
    ProblemDetail handleSelectionOutdated(SelectionOutdatedException ex) {
        log.warn("[ERROR_HANDLER] Remediation selection outdated: {}", ex.getMessage());
        return problemWith(HttpStatus.CONFLICT, "Selection Outdated",
                "error.remediation.selection_outdated");
    }

    @ExceptionHandler(ObfuscationJobAlreadyRunningException.class)
    ProblemDetail handleObfuscationJobAlreadyRunning(ObfuscationJobAlreadyRunningException ex) {
        log.warn("[ERROR_HANDLER] Obfuscation job already running: {}", ex.getMessage());
        return problemWith(HttpStatus.CONFLICT, "Obfuscation Job Already Running",
                "error.remediation.job_already_running");
    }

    @ExceptionHandler(ObfuscationJobNotFoundException.class)
    ProblemDetail handleObfuscationJobNotFound(ObfuscationJobNotFoundException ex) {
        log.warn("[ERROR_HANDLER] Obfuscation job not found: {}", ex.getMessage());
        return problemWith(HttpStatus.NOT_FOUND, "Obfuscation Job Not Found",
                "error.remediation.job_not_found");
    }

    @ExceptionHandler(ExportException.class)
    ProblemDetail handleExport(ExportException ex) {
        log.error("[ERROR_HANDLER] Export failed: {}", ex.getMessage());
        return problemWith(HttpStatus.INTERNAL_SERVER_ERROR, "Export Failed",
                "error.export.failed");
    }

    @ExceptionHandler(ExportContextNotFoundException.class)
    ProblemDetail handleExportContextNotFound(ExportContextNotFoundException ex) {
        log.warn("[ERROR_HANDLER] Export context not found: {}", ex.getMessage());
        return problemWith(HttpStatus.NOT_FOUND, "Export Context Not Found",
                "error.export.context_not_found");
    }

    @ExceptionHandler(UnsupportedSourceTypeException.class)
    ProblemDetail handleUnsupportedSourceType(UnsupportedSourceTypeException ex) {
        log.warn("[ERROR_HANDLER] Unsupported source type: {}", ex.getMessage());
        return problemWith(HttpStatus.BAD_REQUEST, "Unsupported Source Type",
                "error.export.unsupported_source_type");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("[ERROR_HANDLER] Illegal argument: {}", ex.getMessage());
        return problemWith(HttpStatus.BAD_REQUEST, "Invalid Argument",
                "error.validation.invalid_argument");
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException ex) {
        log.warn("[ERROR_HANDLER] Illegal state: {}", ex.getMessage());
        return problemWith(HttpStatus.CONFLICT, "Invalid State",
                "error.state.invalid");
    }

    @Override
    protected ResponseEntity<@NonNull Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        log.warn("[ERROR_HANDLER] Bean validation failed: {}", ex.getMessage());
        ProblemDetail pd = problemWith(HttpStatus.BAD_REQUEST, "Validation Failed",
                "error.validation.constraint_violation");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @Override
    protected ResponseEntity<@NonNull Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        log.warn("[ERROR_HANDLER] Malformed request: {}", ex.getMessage());
        ProblemDetail pd = problemWith(HttpStatus.BAD_REQUEST, "Malformed Request",
                "error.validation.malformed_request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleAll(Exception ex) {
        log.error("[ERROR_HANDLER] Unexpected error: {}", ex.getMessage(), ex);
        return problemWith(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "error.internal");
    }

    private ProblemDetail problemWith(HttpStatus status, String title, String errorKey) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        problemDetail.setProperty("errorKey", errorKey);
        return problemDetail;
    }
}
