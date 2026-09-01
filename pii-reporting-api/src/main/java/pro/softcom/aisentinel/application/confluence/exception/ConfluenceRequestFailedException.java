package pro.softcom.aisentinel.application.confluence.exception;

import lombok.Getter;

/**
 * A Confluence call answered with a status the caller cannot work with.
 *
 * <p>Lives beside the {@code ConfluenceClient} port rather than with the HTTP
 * adapter's own exceptions, because the scan has to tell an outage that dooms
 * every remaining space from a failure bound to the one being opened, and the
 * application layer is not allowed to see infrastructure types.
 *
 * <p>The status code rides along untranslated: it is the only fact separating a
 * space that no longer exists from a source refusing the caller altogether.
 */
@Getter
public class ConfluenceRequestFailedException extends RuntimeException {

    private final int statusCode;

    public ConfluenceRequestFailedException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
