package pro.softcom.aisentinel.domain.pii.judge;

public record JudgeEndpoint(String host, Integer port) {

    public JudgeEndpoint {
        host = host == null || host.isBlank() ? null : host;
        if (port != null && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException("Judge port must be between 1 and 65535");
        }
    }

    public JudgeEndpoint resolve(String fallbackHost, Integer fallbackPort) {
        return new JudgeEndpoint(host == null ? fallbackHost : host, port == null ? fallbackPort : port);
    }
}
