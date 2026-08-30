package pro.softcom.aisentinel.domain.pii.remediation;

public class ObfuscationJobNotFoundException extends RuntimeException {

    public ObfuscationJobNotFoundException(String jobId) {
        super("Obfuscation job not found: " + jobId);
    }
}
