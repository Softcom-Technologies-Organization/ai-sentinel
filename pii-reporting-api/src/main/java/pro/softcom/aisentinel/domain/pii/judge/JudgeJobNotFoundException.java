package pro.softcom.aisentinel.domain.pii.judge;

public class JudgeJobNotFoundException extends RuntimeException {

    public JudgeJobNotFoundException(String jobId) {
        super("Judge job not found: " + jobId);
    }
}
