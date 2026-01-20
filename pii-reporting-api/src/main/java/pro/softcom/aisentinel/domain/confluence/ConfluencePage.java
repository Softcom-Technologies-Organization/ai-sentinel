package pro.softcom.aisentinel.domain.confluence;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    
    public record PageMetadata(
        String createdBy,
        LocalDateTime createdDate,
        String lastModifiedBy,
        LocalDateTime lastModifiedDate,
        int version,
        String status
    ) {}
}
