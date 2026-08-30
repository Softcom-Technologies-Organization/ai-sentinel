package pro.softcom.aisentinel.domain.pii.scan;

public class ScanSpaceStatsNotFoundException extends RuntimeException {

    public ScanSpaceStatsNotFoundException(String spaceKey) {
        super("No scan statistics found for space: " + spaceKey);
    }
}
