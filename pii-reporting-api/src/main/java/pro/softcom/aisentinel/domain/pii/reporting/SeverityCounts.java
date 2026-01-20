package pro.softcom.aisentinel.domain.pii.reporting;

/**
 * Immutable record representing PII severity counts.
 * 
 * <p>Used to aggregate and track the number of PIIs detected at each severity level
 * during a scan or for reporting purposes.
 * 
 * @param high   Number of HIGH severity PIIs
 * @param medium Number of MEDIUM severity PIIs
 * @param low    Number of LOW severity PIIs
 */
public record SeverityCounts(
    int high,
    int medium,
    int low
) {
    /**
     * Calculates the total number of PIIs across all severity levels.
     * 
     * @return Sum of high, medium, and low counts
     */
    public int total() {
        return high + medium + low;
    }
    
    /**
     * Factory method to create a SeverityCounts with all zeros.
     * 
     * @return SeverityCounts with high=0, medium=0, low=0
     */
    public static SeverityCounts zero() {
        return new SeverityCounts(0, 0, 0);
    }
}
