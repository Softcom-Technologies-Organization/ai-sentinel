package pro.softcom.aisentinel.application.pii.reporting.service;

import pro.softcom.aisentinel.domain.pii.scan.ScanErrorKind;
import pro.softcom.aisentinel.domain.pii.scan.TranslatableError;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live state of one scan run, carrying the decision to pause it.
 *
 * <p>Business purpose: an outage detected while analysing one page must stop the
 * whole run — the remaining pages of that space, and the spaces queued behind it.
 * This state is what carries that verdict across the reactive pipeline, which
 * otherwise handles each page independently.
 *
 * <p>Scoped to a single run and shared by the pages analysed concurrently, hence
 * the atomic reference. The first cause recorded wins: it is the one that
 * actually stopped the scan, and later pages failing for the same outage would
 * only overwrite it with a less relevant message.
 */
public final class ScanRunState {

    private final String scanId;
    private final AtomicReference<TranslatableError> pauseError = new AtomicReference<>();
    private final AtomicBoolean pausePersisted = new AtomicBoolean(false);

    public ScanRunState(String scanId) {
        this.scanId = scanId;
    }

    public String scanId() {
        return scanId;
    }

    /**
     * Records that an outage requires pausing this run, unless one already did.
     *
     * @param kind  the outage kind, ignored when it does not pause the scan
     * @param cause short technical reason, streamed to the dashboard
     */
    public void requestPause(ScanErrorKind kind, String cause) {
        if (kind == null || !kind.pausesScan()) {
            return;
        }
        pauseError.compareAndSet(null, kind.toPauseError(cause));
    }

    /**
     * Whether this run must stop emitting new work.
     *
     * @return true once an outage was recorded
     */
    public boolean mustPause() {
        return pauseError.get() != null;
    }

    /**
     * The outage that stopped this run, as the dashboard will word it.
     *
     * <p>It carries the key naming the outage, which is what the frontend
     * translates to show a cause-specific notification — so the kind itself never
     * needs to be kept.
     *
     * @return the recorded error, empty while the run is healthy
     */
    public Optional<TranslatableError> pauseError() {
        return Optional.ofNullable(pauseError.get());
    }

    /**
     * Claims the right to write the PAUSED status for this run, once.
     *
     * <p>An outage can be spotted on several paths — while analysing a page, or while
     * listing the pages of a space that never started — and each must be able to
     * trigger the pause. Making the claim exclusive keeps them from all issuing the
     * same UPDATE, and lets the callers stay ignorant of each other.
     *
     * @return true for the first caller of a paused run, false afterwards and for a healthy run
     */
    public boolean claimPausePersistence() {
        return mustPause() && pausePersisted.compareAndSet(false, true);
    }
}
