package pro.softcom.sentinelle.domain.pii.scan;

public record ScanProgress(
        int currentIndex,
        int analyzedOffset,
        int originalTotal,
        int remainingTotal
    ) {
        public int analyzedCount() {
            return analyzedOffset + (currentIndex - 1);
        }

        public int completedCount() {
            return analyzedOffset + currentIndex;
        }
    }
