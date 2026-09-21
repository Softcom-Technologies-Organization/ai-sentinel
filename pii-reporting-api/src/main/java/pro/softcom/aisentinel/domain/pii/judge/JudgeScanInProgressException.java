package pro.softcom.aisentinel.domain.pii.judge;

public class JudgeScanInProgressException extends RuntimeException {

    public JudgeScanInProgressException() {
        super("Judge evaluation is unavailable until the current scan has finished");
    }
}
