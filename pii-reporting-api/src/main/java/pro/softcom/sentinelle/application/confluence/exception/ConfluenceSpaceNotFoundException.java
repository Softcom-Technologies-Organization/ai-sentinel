package pro.softcom.sentinelle.application.confluence.exception;

import lombok.Getter;

@Getter
public class ConfluenceSpaceNotFoundException extends RuntimeException {
    private final String spaceKey;

    public ConfluenceSpaceNotFoundException(String spaceKey) {
        super("Confluence space not found: " + spaceKey);
        this.spaceKey = spaceKey;
    }
}
