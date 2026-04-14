package pro.softcom.aisentinel.domain.pii.reporting;

/**
 * Contract for entities that carry a PII type identifier.
 *
 * <p>Implemented by domain records that need PII severity classification
 * (e.g., {@link DetectedPersonallyIdentifiableInformation}).
 */
public interface PiiTyped {

    String piiType();
}
