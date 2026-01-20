package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto;

/**
 * DTO representing aggregated PII severity counts for REST API responses.
 * 
 * <p>Business purpose: Provides clients with severity statistics showing the distribution
 * of detected PIIs across HIGH, MEDIUM, and LOW severity levels during a scan.
 * 
 * <p>Design: Immutable record with validation ensuring data consistency. The total field
 * is computed automatically to guarantee accuracy and prevent client-side calculation errors.
 * 
 * @param high   Count of HIGH severity PIIs (≥ 0)
 * @param medium Count of MEDIUM severity PIIs (≥ 0)
 * @param low    Count of LOW severity PIIs (≥ 0)
 * @param total  Total count across all severity levels (high + medium + low)
 */
public record SeverityCountsDto(
    int high,
    int medium,
    int low,
    int total
) {
    /**
     * Canonical constructor with validation.
     * 
     * <p>Business rule: Counts cannot be negative as they represent actual detection counts.
     * 
     * @throws IllegalArgumentException if any count is negative
     */
    public SeverityCountsDto {
        if (high < 0 || medium < 0 || low < 0) {
            throw new IllegalArgumentException("Severity counts cannot be negative");
        }
    }
    
    /**
     * Factory method creating a zero-count instance.
     * 
     * <p>Business purpose: Represents a space with no detected PIIs, used as default
     * when severity data is unavailable or a scan is in initial state.
     * 
     * @return SeverityCountsDto with all counts set to zero
     */
    public static SeverityCountsDto zero() {
        return new SeverityCountsDto(0, 0, 0, 0);
    }
}
