package pro.softcom.sentinelle.application.pii.reporting.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pro.softcom.sentinelle.application.pii.reporting.port.out.PublishEventPort;
import pro.softcom.sentinelle.domain.pii.scan.SpaceScanCompleted;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanEventDispatcherTest {

    @Mock
    private PublishEventPort publishEventPort;

    @Captor
    private ArgumentCaptor<SpaceScanCompleted> eventCaptor;

    private ScanEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new ScanEventDispatcher(publishEventPort);
    }

    @Test
    @DisplayName("Should_ExecuteActionImmediately_When_NoTransactionActive")
    void Should_ExecuteActionImmediately_When_NoTransactionActive() {
        // Given
        Runnable action = mock(Runnable.class);

        // When
        dispatcher.scheduleAfterCommit(action);

        // Then
        verify(action).run();
    }

    @Test
    @DisplayName("Should_RegisterSynchronization_When_TransactionActive")
    void Should_RegisterSynchronization_When_TransactionActive() {
        // Given
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        Runnable action = mock(Runnable.class);

        try {
            // When
            dispatcher.scheduleAfterCommit(action);

            // Then - action should not be executed immediately
            verify(action, never()).run();

            // Simulate transaction commit
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            // Then - action should be executed after commit
            verify(action).run();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    @DisplayName("Should_HandleException_When_ActionFailsAfterCommit")
    void Should_HandleException_When_ActionFailsAfterCommit() {
        // Given
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        Runnable action = mock(Runnable.class);
        doThrow(new RuntimeException("Action failed")).when(action).run();

        try {
            // When
            dispatcher.scheduleAfterCommit(action);

            // Then - should not throw exception when registering
            assertThatCode(() -> {
                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(TransactionSynchronization::afterCommit);
            }).doesNotThrowAnyException();

            // Verify action was attempted
            verify(action).run();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "scan-123, space1",
            "scan-456, space2",
            "scan-789, space3"
    })
    @DisplayName("Should_PublishEventImmediately_When_NoTransactionActive")
    void Should_PublishEventImmediately_When_NoTransactionActive(String scanId, String spaceKey) {
        // When
        dispatcher.publishAfterCommit(scanId, spaceKey);

        // Then
        verify(publishEventPort).publishCompleteEvent(eventCaptor.capture());
        SpaceScanCompleted event = eventCaptor.getValue();
        assertThatCode(() -> {
            org.assertj.core.api.Assertions.assertThat(event.scanId()).isEqualTo(scanId);
            org.assertj.core.api.Assertions.assertThat(event.spaceKey()).isEqualTo(spaceKey);
        }).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "scan-123, space1",
            "scan-456, space2",
            "scan-789, space3"
    })
    @DisplayName("Should_PublishEventAfterCommit_When_TransactionActive")
    void Should_PublishEventAfterCommit_When_TransactionActive(String scanId, String spaceKey) {
        // Given
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            // When
            dispatcher.publishAfterCommit(scanId, spaceKey);

            // Then - event should not be published immediately
            verify(publishEventPort, never()).publishCompleteEvent(any());

            // Simulate transaction commit
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            // Then - event should be published after commit
            verify(publishEventPort).publishCompleteEvent(eventCaptor.capture());
            SpaceScanCompleted event = eventCaptor.getValue();
            assertThatCode(() -> {
                org.assertj.core.api.Assertions.assertThat(event.scanId()).isEqualTo(scanId);
                org.assertj.core.api.Assertions.assertThat(event.spaceKey()).isEqualTo(spaceKey);
            }).doesNotThrowAnyException();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    @DisplayName("Should_HandleException_When_PublishFailsAfterCommit")
    void Should_HandleException_When_PublishFailsAfterCommit() {
        // Given
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        String scanId = "scan-789";
        String spaceKey = "space3";
        doThrow(new RuntimeException("Publish failed")).when(publishEventPort).publishCompleteEvent(any());

        try {
            // When
            dispatcher.publishAfterCommit(scanId, spaceKey);

            // Then - should not throw exception when registering or executing
            assertThatCode(() -> {
                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(TransactionSynchronization::afterCommit);
            }).doesNotThrowAnyException();

            // Verify publish was attempted
            verify(publishEventPort).publishCompleteEvent(any());
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    @DisplayName("Should_ExecuteMultipleActions_When_RegisteredInTransaction")
    void Should_ExecuteMultipleActions_When_RegisteredInTransaction() {
        // Given
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        Runnable action1 = mock(Runnable.class);
        Runnable action2 = mock(Runnable.class);

        try {
            // When
            dispatcher.scheduleAfterCommit(action1);
            dispatcher.scheduleAfterCommit(action2);

            // Then - actions should not be executed immediately
            verify(action1, never()).run();
            verify(action2, never()).run();

            // Simulate transaction commit
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            // Then - both actions should be executed after commit
            verify(action1).run();
            verify(action2).run();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}
