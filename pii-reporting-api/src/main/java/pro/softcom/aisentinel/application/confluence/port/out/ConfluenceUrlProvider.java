package pro.softcom.aisentinel.application.confluence.port.out;

/**
 * Application-level provider for building Confluence URLs.
 * Keeps the application layer agnostic of infrastructure configuration classes.
 */
public interface ConfluenceUrlProvider {
    String baseUrl();
    /**
     * Build a public page URL for the given Confluence page identifier.
     * Returns null when pageId or baseUrl is blank.
     */
    String pageUrl(String pageId);
}
