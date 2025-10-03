package pro.softcom.sentinelle.domain.confluence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * Représente une page Confluence.
 */
@Builder
public record ConfluencePage(
    String id,
    String title,
    String spaceKey,
    PageContent content,
    PageMetadata metadata,
    List<String> labels,
    Map<String, Object> customProperties
) {
    
    /**
     * Contenu de la page avec support de différents formats
     */
    public sealed interface PageContent permits HtmlContent, WikiContent, MarkdownContent {
        String format();
        String body();
    }
    
    public record HtmlContent(String body) implements PageContent {
        @Override
        public String format() {
            return "storage";
        }
    }
    
    public record WikiContent(String body) implements PageContent {
        @Override
        public String format() {
            return "wiki";
        }
    }
    
    public record MarkdownContent(String body) implements PageContent {
        @Override
        public String format() {
            return "markdown";
        }
    }
    
    /**
     * Métadonnées de la page
     */
    public record PageMetadata(
        String createdBy,
        LocalDateTime createdDate,
        String lastModifiedBy,
        LocalDateTime lastModifiedDate,
        int version,
        String status
    ) {}
}
