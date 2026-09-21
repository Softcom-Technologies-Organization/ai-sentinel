package pro.softcom.aisentinel.domain.pii.judge;

public class JudgeJobAlreadyRunningException extends RuntimeException {

    public JudgeJobAlreadyRunningException(String spaceKey) {
        super("A judge job is already running for space " + spaceKey);
    }
}
