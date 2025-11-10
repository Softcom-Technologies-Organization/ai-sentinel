package pro.softcom.sentinelle.application.pii.reporting.service;

import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;

/**
 * Result of text extraction from an attachment.
 */
public record AttachmentTextExtracted(AttachmentInfo attachment, String extractedText) {
}
