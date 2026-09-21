package pro.softcom.aisentinel.infrastructure.pii.judge.adapter.out;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.pii.judge.port.out.JudgeJobStore;

/**
 * Crash recovery: judge jobs left {@code RUNNING} by a previous process are marked
 * {@code INTERRUPTED} at boot. Verdicts already recorded stay; a relaunch only judges what is
 * still pending.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JudgeJobBootRecovery {

    private final JudgeJobStore judgeJobStore;

    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedJobs() {
        int interrupted = judgeJobStore.markInterruptedOnBoot();
        if (interrupted > 0) {
            log.warn("[LLM_JUDGE] {} judge job(s) found RUNNING at boot and marked INTERRUPTED", interrupted);
        }
    }
}
