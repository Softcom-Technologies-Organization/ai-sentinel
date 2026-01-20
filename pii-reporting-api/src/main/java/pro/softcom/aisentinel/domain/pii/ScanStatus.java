package pro.softcom.aisentinel.domain.pii;

import lombok.Getter;

@Getter
public enum ScanStatus {
    NOT_STARTED("Non démarré"),
    RUNNING("En cours"),
    COMPLETED("Terminé"),
    FAILED("Échoué"),
    PAUSED("En pause");

    private final String label;

    ScanStatus(String label) {
        this.label = label;
    }
}
