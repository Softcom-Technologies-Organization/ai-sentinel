package pro.softcom.sentinelle.infrastructure.confluence.adapter.out;

import lombok.Getter;

@Getter
public final class ConfluenceApiException extends ConfluenceException {
    public ConfluenceApiException(String message, int statusCode, String confluenceMessage) {
        super(message, statusCode, confluenceMessage);
    }
}
