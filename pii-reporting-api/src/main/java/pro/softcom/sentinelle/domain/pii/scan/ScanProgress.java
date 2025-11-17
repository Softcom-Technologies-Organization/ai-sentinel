package pro.softcom.sentinelle.domain.pii.scan;

public record ScanProgress(
        int currentIndex,
        int analyzedOffset,
        int originalTotal,
        int remainingTotal
    ) {
    }
