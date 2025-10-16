package pro.softcom.sentinelle.application.pii.reporting.service;

import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;

/**
 * Résultat de l'extraction de texte d'une pièce jointe Confluence.
 * Encapsule les informations de la pièce jointe et le contenu textuel extrait.
 */
public record AttachmentTextExtracted(AttachmentInfo attachment, String extractedText) {
}
