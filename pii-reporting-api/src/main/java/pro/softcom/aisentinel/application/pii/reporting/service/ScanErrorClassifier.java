package pro.softcom.aisentinel.application.pii.reporting.service;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorRunStat;
import pro.softcom.aisentinel.domain.pii.scan.ScanErrorKind;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

/**
 * Tells an outage that dooms every remaining item apart from a failure bound to
 * the single item being analysed.
 *
 * <p><strong>Business rule:</strong> the classification is deliberately
 * conservative — anything not proven to be an infrastructure outage is treated as
 * an item failure. A wrong "outage" verdict pauses a scan that could have
 * completed, which is worse than letting a few items be reported as failed: a
 * full scan is expected to hit a handful of oversized attachments and detections
 * that outrun their timeout.
 *
 * <p>Two shapes of unavailability are covered, because a detector can fail
 * without the call failing: when the remote model endpoint is down, the detection
 * service still answers successfully, only with a failed entry in its per-detector
 * run stats.
 */
public final class ScanErrorClassifier {

    /** JDK message prefix when an HTTP proxy refuses the CONNECT handshake. */
    private static final String PROXY_TUNNEL_FAILURE_PREFIX = "Tunnel failed";

    private ScanErrorClassifier() {
        // static utility
    }

    /**
     * Classifies the exception raised while analysing one item.
     *
     * @param throwable the failure, possibly wrapping the real cause
     * @return the kind driving whether the scan pauses or carries on
     */
    public static ScanErrorKind classify(Throwable throwable) {
        if (findInCauseChain(throwable, ConnectException.class) != null
            || findInCauseChain(throwable, UnknownHostException.class) != null
            || findInCauseChain(throwable, NoRouteToHostException.class) != null
            || isProxyTunnelFailure(throwable)) {
            return ScanErrorKind.NETWORK_UNAVAILABLE;
        }

        StatusRuntimeException grpcFailure = findInCauseChain(throwable, StatusRuntimeException.class);
        if (grpcFailure != null && isDetectionServiceDown(grpcFailure.getStatus().getCode())) {
            return ScanErrorKind.DETECTOR_UNAVAILABLE;
        }

        return ScanErrorKind.ITEM_FAILURE;
    }

    /**
     * Reports the detector that could not run despite a successful analysis call.
     *
     * <p>Such a detector contributes no finding, which reads exactly like clean
     * content, so the scan must pause rather than keep producing results the
     * operator would read as complete.
     *
     * @param detection the analysis result to inspect, may be null
     * @return the failure reason naming the detector, or empty when all ran
     */
    public static Optional<String> unavailableDetector(ContentPiiDetection detection) {
        if (detection == null || detection.detectorRunStats().isEmpty()) {
            return Optional.empty();
        }
        return detection.detectorRunStats().stream()
            .filter(DetectorRunStat::failed)
            .findFirst()
            .map(stat -> "Detector %s could not run: %s".formatted(stat.source(), stat.error()));
    }

    /**
     * Walks the cause chain looking for a given exception type.
     *
     * <p>Needed because both the Confluence client and the gRPC detector client
     * wrap their transport failures in their own exception types, so the fact
     * that decides the classification is never the top-level throwable.
     *
     * @param throwable    the exception to walk, may be null
     * @param searchedType the type to look for
     * @param <T>          the searched exception type
     * @return the first matching exception found, or null
     */
    public static <T extends Throwable> T findInCauseChain(Throwable throwable, Class<T> searchedType) {
        // Visited set rather than a self-reference check: a cause chain can loop back over
        // several exceptions (A caused by B caused by A), and spinning here would hang the
        // scan thread that is trying to classify the failure.
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        while (current != null && visited.add(current)) {
            if (searchedType.isInstance(current)) {
                return searchedType.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * Whether the HTTP proxy refused to open a tunnel to the data source.
     *
     * <p>When the data source is reached through a proxy — a VPN client exposing a
     * local port, typically — losing the tunnel does NOT surface as a
     * {@link ConnectException}: the proxy is still listening locally and accepts the
     * connection, then fails the CONNECT handshake. The JDK reports that as a plain
     * {@code IOException("Tunnel failed, got: <status>")}, which would otherwise be
     * classified as a single-item failure and let the scan run to completion over an
     * unreachable source.
     *
     * <p>Matching on the message is the only signal available: the JDK raises the
     * generic {@code IOException} type for this case, with no dedicated subclass.
     */
    private static boolean isProxyTunnelFailure(Throwable throwable) {
        IOException transportFailure = findInCauseChain(throwable, IOException.class);
        return transportFailure != null
            && transportFailure.getMessage() != null
            && transportFailure.getMessage().startsWith(PROXY_TUNNEL_FAILURE_PREFIX);
    }

    /**
     * Whether a gRPC status means the detection service itself is down, as opposed
     * to it rejecting this particular content.
     *
     * <p>{@code DEADLINE_EXCEEDED} (the LLM outran its timeout on this content) and
     * {@code RESOURCE_EXHAUSTED} (payload above the channel limit, i.e. an oversized
     * attachment) are explicitly item failures: they are expected on a full scan and
     * pausing on them would stop every scan before its end.
     */
    private static boolean isDetectionServiceDown(Status.Code code) {
        return code == Status.Code.UNAVAILABLE || code == Status.Code.UNIMPLEMENTED;
    }
}
