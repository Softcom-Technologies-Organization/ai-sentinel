package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceUrlProvider;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config.ConfluenceConnectionConfig;

/**
 * Infrastructure adapter that exposes Confluence baseUrl and builds public URLs
 * through the application-level ConfluenceUrlProvider.
 */
@Component
public class ConfluenceUrlProviderAdapter implements ConfluenceUrlProvider {

    private final ConfluenceConnectionConfig confluenceConnectionConfig;

    public ConfluenceUrlProviderAdapter(@Qualifier("confluenceConfig") ConfluenceConnectionConfig confluenceConnectionConfig) {
        this.confluenceConnectionConfig = confluenceConnectionConfig;
    }

    @Override
    public String baseUrl() {
        return confluenceConnectionConfig.baseUrl();
    }

    @Override
    public String pageUrl(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return null;
        }
        String base = baseUrl();
        if (base == null || base.isBlank()) {
            return null;
        }
        base = base.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/pages/viewpage.action?pageId=" + pageId;
    }
}
