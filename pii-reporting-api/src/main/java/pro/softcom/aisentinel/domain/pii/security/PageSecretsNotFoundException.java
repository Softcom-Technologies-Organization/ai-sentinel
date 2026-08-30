package pro.softcom.aisentinel.domain.pii.security;

public class PageSecretsNotFoundException extends RuntimeException {

    public PageSecretsNotFoundException(String scanId, String pageId) {
        super("No revealable secrets found for scan " + scanId + " and page " + pageId);
    }
}
