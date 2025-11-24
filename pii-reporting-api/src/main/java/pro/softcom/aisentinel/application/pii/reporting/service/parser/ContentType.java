package pro.softcom.aisentinel.application.pii.reporting.service.parser;

/**
 * Types of content that can be parsed for PII context extraction.
 */
public enum ContentType {
    /**
     * Plain text content with line breaks (\n).
     */
    PLAIN_TEXT,
    
    /**
     * HTML content with block-level tags creating visual line breaks.
     */
    HTML,
    
    /**
     * Automatic detection based on content analysis.
     */
    AUTO
}
