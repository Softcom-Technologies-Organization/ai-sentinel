package pro.softcom.aisentinel.application.confluence.exception;

/**
 * A Confluence call never reached the server.
 *
 * <p>Business purpose: the transport failure alone does not say which Confluence
 * the scan was talking to — a proxy refusal reads {@code Tunnel failed, got: 502},
 * a dead host reads as the bare host name. Both end up in the operator's "scan
 * paused" notification, where a cause that names no source is not actionable.
 *
 * <p>Deliberately not an {@link java.io.IOException}: the scan classifies an
 * outage by looking for the transport failure in the cause chain, and an
 * {@code IOException} wrapper would be found before the real one, hiding the
 * proxy tunnel failure it is meant to recognise.
 */
public class ConfluenceUnreachableException extends RuntimeException {

    public ConfluenceUnreachableException(String message, Throwable cause) {
        super(message, cause);
    }
}
