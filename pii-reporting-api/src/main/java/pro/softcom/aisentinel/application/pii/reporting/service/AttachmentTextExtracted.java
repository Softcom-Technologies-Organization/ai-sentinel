package pro.softcom.aisentinel.application.pii.reporting.service;

import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;

/**
 * Result of text extraction from an attachment.
 */
public record AttachmentTextExtracted(AttachmentInfo attachment, String extractedText) {
}
