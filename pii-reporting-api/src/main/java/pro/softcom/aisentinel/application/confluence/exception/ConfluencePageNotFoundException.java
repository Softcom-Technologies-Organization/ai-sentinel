package pro.softcom.aisentinel.application.confluence.exception;

import lombok.Getter;

@Getter
public class ConfluencePageNotFoundException extends RuntimeException {
    private final String pageId;

    public ConfluencePageNotFoundException(String pageId) {
        super("Confluence page not found: " + pageId);
        this.pageId = pageId;
    }
}
