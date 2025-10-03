package pro.softcom.sentinelle.application.pii.reporting.service;

/**
 * Calculates scan progress percentages for reporting purposes.
 * Business intent: Provides consistent progress tracking across the scanning workflow.
 */
public class ScanProgressCalculator {

    /**
     * Calculates progress percentage based on analyzed pages.
     *
     * @param analyzedPages number of pages already analyzed
     * @param totalPages    total number of pages to analyze
     * @return progress percentage between 0.0 and 100.0
     */
    public double calculateProgress(int analyzedPages, int totalPages) {
        if (totalPages <= 0) {
            return 100.0;
        }
        int safeAnalyzed = Math.max(0, analyzedPages);
        double percent = (double) safeAnalyzed / (double) totalPages * 100.0;
        return clampProgress(percent);
    }

    private double clampProgress(double percent) {
        if (percent < 0) {
            return 0.0;
        }
        if (percent > 100) {
            return 100.0;
        }
        return percent;
    }
}
