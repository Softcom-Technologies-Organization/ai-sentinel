package pro.softcom.aisentinel.application.pii.reporting.service;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pro.softcom.aisentinel.application.confluence.exception.ConfluenceRequestFailedException;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorRunStat;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorSource;
import pro.softcom.aisentinel.domain.pii.scan.ScanErrorKeys;
import pro.softcom.aisentinel.domain.pii.scan.ScanErrorKind;
import pro.softcom.aisentinel.domain.pii.scan.TranslatableError;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the line between an outage that must pause the scan and a failure bound to
 * one item, which must not. Getting this wrong in either direction is costly: pausing
 * too eagerly means no scan ever reaches its end, pausing too late means shipping a
 * report that looks clean because a detector was down.
 */
class ScanErrorClassifierTest {

    @Nested
    @DisplayName("Outages that pause the scan")
    class PausingOutages {

        @Test
        @DisplayName("classify - a refused connection is a network outage")
        void Should_ReportNetworkOutage_When_ConnectionIsRefused() {
            assertThat(ScanErrorClassifier.classify(new ConnectException()))
                .isEqualTo(ScanErrorKind.NETWORK_UNAVAILABLE);
        }

        @Test
        @DisplayName("classify - an unresolvable host is a network outage")
        void Should_ReportNetworkOutage_When_HostCannotBeResolved() {
            assertThat(ScanErrorClassifier.classify(new UnknownHostException("confluence.example.org")))
                .isEqualTo(ScanErrorKind.NETWORK_UNAVAILABLE);
        }

        @Test
        @DisplayName("classify - an unroutable host is a network outage")
        void Should_ReportNetworkOutage_When_HostIsUnroutable() {
            assertThat(ScanErrorClassifier.classify(new NoRouteToHostException()))
                .isEqualTo(ScanErrorKind.NETWORK_UNAVAILABLE);
        }

        @Test
        @DisplayName("classify - a network outage wrapped by a client is still detected")
        void Should_ReportNetworkOutage_When_CauseIsWrapped() {
            Throwable wrapped = new IllegalStateException("confluence call failed",
                new IOException("transport", new ConnectException()));

            assertThat(ScanErrorClassifier.classify(wrapped))
                .isEqualTo(ScanErrorKind.NETWORK_UNAVAILABLE);
        }

        @Test
        @DisplayName("classify - a proxy refusing the tunnel is a network outage")
        void Should_ReportNetworkOutage_When_ProxyTunnelFails() {
            // A VPN client exposing a local proxy port keeps accepting connections once the
            // tunnel drops, so the failure arrives as a plain IOException on the CONNECT
            // handshake instead of a ConnectException. Observed verbatim on a real VPN drop.
            Throwable tunnelDown = new IOException("Tunnel failed, got: 503");

            assertThat(ScanErrorClassifier.classify(tunnelDown))
                .isEqualTo(ScanErrorKind.NETWORK_UNAVAILABLE);
        }

        @Test
        @DisplayName("classify - a wrapped proxy tunnel failure is still detected")
        void Should_ReportNetworkOutage_When_TunnelFailureIsWrapped() {
            Throwable wrapped = new IllegalStateException("attachment listing failed",
                new IOException("Tunnel failed, got: 407"));

            assertThat(ScanErrorClassifier.classify(wrapped))
                .isEqualTo(ScanErrorKind.NETWORK_UNAVAILABLE);
        }

        @Test
        @DisplayName("classify - a source refusing the caller is a network outage")
        void Should_ReportNetworkOutage_When_SourceRefusesTheCaller() {
            // Observed verbatim when the Confluence token lost its product access: every space
            // listing answered 403, and the scan used to read that as spaces holding no page.
            Throwable refused = new ConfluenceRequestFailedException(
                "Confluence refused to list the pages of space AS with status 403", 403);

            assertThat(ScanErrorClassifier.classify(refused))
                .isEqualTo(ScanErrorKind.NETWORK_UNAVAILABLE);
        }

        @Test
        @DisplayName("classify - a wrapped source rejection is still detected")
        void Should_ReportNetworkOutage_When_SourceRejectionIsWrapped() {
            Throwable wrapped = new CompletionException(
                new ConfluenceRequestFailedException("listing rejected", 401));

            assertThat(ScanErrorClassifier.classify(wrapped))
                .isEqualTo(ScanErrorKind.NETWORK_UNAVAILABLE);
        }

        @Test
        @DisplayName("classify - an unavailable detection service is a detector outage")
        void Should_ReportDetectorOutage_When_DetectionServiceIsUnavailable() {
            StatusRuntimeException unavailable = new StatusRuntimeException(Status.UNAVAILABLE);

            assertThat(ScanErrorClassifier.classify(unavailable))
                .isEqualTo(ScanErrorKind.DETECTOR_UNAVAILABLE);
        }

        @Test
        @DisplayName("classify - a detection service missing the RPC is a detector outage")
        void Should_ReportDetectorOutage_When_DetectionRpcIsUnimplemented() {
            StatusRuntimeException unimplemented = new StatusRuntimeException(Status.UNIMPLEMENTED);

            assertThat(ScanErrorClassifier.classify(unimplemented))
                .isEqualTo(ScanErrorKind.DETECTOR_UNAVAILABLE);
        }

        @Test
        @DisplayName("every pausing kind names itself with a key the dashboard can translate")
        void Should_CarryTranslationKey_When_KindPausesScan() {
            assertThat(ScanErrorKind.NETWORK_UNAVAILABLE.pausesScan()).isTrue();
            assertThat(ScanErrorKind.DETECTOR_UNAVAILABLE.pausesScan()).isTrue();
            assertThat(ScanErrorKind.ITEM_FAILURE.pausesScan()).isFalse();

            assertThat(ScanErrorKind.NETWORK_UNAVAILABLE.toPauseError("VPN down"))
                .isEqualTo(new TranslatableError(ScanErrorKeys.PAUSED_NETWORK, Map.of("cause", "VPN down")));
            assertThat(ScanErrorKind.DETECTOR_UNAVAILABLE.toPauseError("LM Studio down"))
                .isEqualTo(new TranslatableError(ScanErrorKeys.PAUSED_DETECTOR, Map.of("cause", "LM Studio down")));
            // An item failure does not stop the scan, so it has nothing to announce.
            assertThat(ScanErrorKind.ITEM_FAILURE.toPauseError("page too large")).isNull();
        }
    }

    @Nested
    @DisplayName("Failures expected on a full scan, which must never pause it")
    class ItemFailures {

        @Test
        @DisplayName("classify - a detection outrunning its gRPC deadline is an item failure")
        void Should_ReportItemFailure_When_DetectionDeadlineIsExceeded() {
            StatusRuntimeException deadline = new StatusRuntimeException(Status.DEADLINE_EXCEEDED);

            assertThat(ScanErrorClassifier.classify(deadline))
                .isEqualTo(ScanErrorKind.ITEM_FAILURE);
        }

        @Test
        @DisplayName("classify - an oversized payload is an item failure")
        void Should_ReportItemFailure_When_PayloadExceedsChannelLimit() {
            StatusRuntimeException tooLarge = new StatusRuntimeException(Status.RESOURCE_EXHAUSTED);

            assertThat(ScanErrorClassifier.classify(tooLarge))
                .isEqualTo(ScanErrorKind.ITEM_FAILURE);
        }

        @Test
        @DisplayName("classify - a reactor timeout is an item failure")
        void Should_ReportItemFailure_When_ReactorTimeoutElapses() {
            assertThat(ScanErrorClassifier.classify(new TimeoutException()))
                .isEqualTo(ScanErrorKind.ITEM_FAILURE);
        }

        @Test
        @DisplayName("classify - a slow server is an item failure, not an outage")
        void Should_ReportItemFailure_When_SocketReadTimesOut() {
            // A read timeout means the server answered too slowly for this item, which a
            // single huge page can cause; treating it as an outage would pause healthy scans.
            assertThat(ScanErrorClassifier.classify(new SocketTimeoutException()))
                .isEqualTo(ScanErrorKind.ITEM_FAILURE);
        }

        @Test
        @DisplayName("classify - a space that no longer exists is an item failure")
        void Should_ReportItemFailure_When_SpaceIsGone() {
            // The space cache outlives the spaces it holds, so a scan over every space walks
            // into deleted ones. The spaces queued behind are readable: pausing here would
            // stop a scan that one stale cache entry cannot doom.
            Throwable spaceGone = new ConfluenceRequestFailedException(
                "Confluence refused to list the pages of space OLD with status 404", 404);

            assertThat(ScanErrorClassifier.classify(spaceGone))
                .isEqualTo(ScanErrorKind.ITEM_FAILURE);
        }

        @Test
        @DisplayName("classify - an unrecognised failure defaults to an item failure")
        void Should_ReportItemFailure_When_CauseIsUnrecognised() {
            assertThat(ScanErrorClassifier.classify(new IllegalArgumentException("bad content")))
                .isEqualTo(ScanErrorKind.ITEM_FAILURE);
        }

        @Test
        @DisplayName("classify - a null throwable defaults to an item failure")
        void Should_ReportItemFailure_When_ThrowableIsNull() {
            assertThat(ScanErrorClassifier.classify(null)).isEqualTo(ScanErrorKind.ITEM_FAILURE);
        }

        @Test
        @DisplayName("classify - a looping cause chain terminates instead of hanging the scan thread")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void Should_Terminate_When_CauseChainLoops() {
            // A chain can loop back over several exceptions, not just reference itself.
            // Walking it without tracking visited nodes would spin forever on the thread
            // that is trying to classify the failure.
            Throwable first = new IllegalStateException("first");
            Throwable second = new IllegalStateException("second");
            first.initCause(second);
            second.initCause(first);

            assertThat(ScanErrorClassifier.classify(first)).isEqualTo(ScanErrorKind.ITEM_FAILURE);
        }
    }

    @Nested
    @DisplayName("Detector that failed although the analysis call succeeded")
    class SilentDetectorFailure {

        @Test
        @DisplayName("unavailableDetector - names the detector that could not run")
        void Should_NameDetector_When_ItReportedAFailure() {
            ContentPiiDetection detection = detectionWith(
                new DetectorRunStat(DetectorSource.REGEX, 12, 3, 0, ""),
                new DetectorRunStat(DetectorSource.MINISTRAL, 0, 0, 0, "endpoint unreachable"));

            assertThat(ScanErrorClassifier.unavailableDetector(detection))
                .hasValueSatisfying(cause -> assertThat(cause)
                    .contains("MINISTRAL")
                    .contains("endpoint unreachable"));
        }

        @Test
        @DisplayName("unavailableDetector - stays empty when every detector ran")
        void Should_StayEmpty_When_EveryDetectorRan() {
            ContentPiiDetection detection = detectionWith(
                new DetectorRunStat(DetectorSource.REGEX, 12, 3, 0, ""),
                new DetectorRunStat(DetectorSource.MINISTRAL, 900, 0, 0, null));

            assertThat(ScanErrorClassifier.unavailableDetector(detection)).isEmpty();
        }

        @Test
        @DisplayName("unavailableDetector - stays empty when the service reports no stats")
        void Should_StayEmpty_When_NoStatsAreReported() {
            assertThat(ScanErrorClassifier.unavailableDetector(detectionWith())).isEmpty();
            assertThat(ScanErrorClassifier.unavailableDetector(null)).isEmpty();
        }

        private ContentPiiDetection detectionWith(DetectorRunStat... stats) {
            return ContentPiiDetection.builder()
                .pageId("p-1")
                .detectorRunStats(List.of(stats))
                .build();
        }
    }
}
