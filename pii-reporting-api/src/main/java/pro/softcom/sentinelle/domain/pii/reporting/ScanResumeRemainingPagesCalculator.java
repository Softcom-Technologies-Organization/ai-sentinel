package pro.softcom.sentinelle.domain.pii.reporting;

import java.util.List;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.pii.ScanStatus;

/**
 * Décide du plan de reprise d’un scan : quelles pages restent à traiter et
 * quel est l’offset déjà analysé.
 * Métier pur (indépendant de toute techno/rx/IO).
 */
public final class ScanResumeRemainingPagesCalculator {

    private ScanResumeRemainingPagesCalculator() {
        //private constructor to hide the implicit public one
    }

    public static ScanRemainingPages computeRemainPages(List<ConfluencePage> pages, ScanCheckpoint checkpoint) {
        List<ConfluencePage> safePages = pages == null ? List.of() : pages;
        int originalTotal = safePages.size();
        int analyzedOffset = computeAnalyzedOffset(safePages, checkpoint);
        List<ConfluencePage> remaining = computeRemainingPages(safePages, checkpoint);
        return new ScanRemainingPages(originalTotal, analyzedOffset, remaining);
    }

    private static int computeAnalyzedOffset(List<ConfluencePage> pages, ScanCheckpoint checkpoint) {
        if (checkpoint == null) {
            return 0;
        }
        String lastPageId = checkpoint.lastProcessedPageId();
        boolean hadInProgressAttachment =
            checkpoint.scanStatus() == ScanStatus.RUNNING && !isBlank(checkpoint.lastProcessedAttachmentName());

        int analyzedOffset = 0;
        if (!isBlank(lastPageId)) {
            int lastPageIndex = indexOfPage(pages, lastPageId);
            analyzedOffset = lastPageIndex >= 0 ? lastPageIndex + 1 : 0;
        }
        if (hadInProgressAttachment) {
            analyzedOffset = Math.max(0, analyzedOffset - 1);
        }
        return analyzedOffset;
    }

    public static List<ConfluencePage> computeRemainingPages(List<ConfluencePage> pages,
                                                             ScanCheckpoint checkpoint) {
        if (pages.isEmpty()) {
            return List.of();
        }
        if (checkpoint == null) {
            return pages;
        }
        if (checkpoint.scanStatus() == ScanStatus.COMPLETED) {
            return List.of();
        }

        String lastProcessedPageId = checkpoint.lastProcessedPageId();
        if (isBlank(lastProcessedPageId)) {
            return pages;
        }

        int lastProcessedIndex = indexOfPage(pages, lastProcessedPageId);
        if (lastProcessedIndex < 0) {
            return pages;
        }

        boolean hadInProgressAttachment = !isBlank(checkpoint.lastProcessedAttachmentName());
        int start = hadInProgressAttachment ? lastProcessedIndex : lastProcessedIndex + 1;
        if (start >= pages.size()) {
            return List.of();
        }
        return pages.subList(start, pages.size());
    }

    private static int indexOfPage(List<ConfluencePage> pages, String pageId) {
        if (pageId == null) {
            return -1;
        }
        for (int i = 0; i < pages.size(); i++) {
            ConfluencePage p = pages.get(i);
            if (p != null && pageId.equals(p.id())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
