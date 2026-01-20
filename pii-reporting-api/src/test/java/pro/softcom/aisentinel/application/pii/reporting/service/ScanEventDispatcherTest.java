package pro.softcom.aisentinel.application.pii.reporting.service;

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
import pro.softcom.aisentinel.application.pii.reporting.port.out.PublishEventPort;
import pro.softcom.aisentinel.domain.pii.scan.SpaceScanCompleted;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScanEventDispatcherTest {

    @Mock
    private PublishEventPort publishEventPort;

    @Captor
    private ArgumentCaptor<SpaceScanCompleted> eventCaptor;

    private ScanEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        // default AfterCommitExecutionPort executes immediately (no active transaction)
        dispatcher = new ScanEventDispatcher(publishEventPort, Runnable::run);
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
        // Given: stub port that defers execution until we manually trigger it
        final Runnable[] stored = new Runnable[1];
        ScanEventDispatcher txDispatcher = new ScanEventDispatcher(publishEventPort, action -> stored[0] = action);
        Runnable action = mock(Runnable.class);

        // When
        txDispatcher.scheduleAfterCommit(action);

        // Then - action should not be executed immediately
        verify(action, never()).run();

        // Simulate transaction commit
        stored[0].run();

        // Then - action should be executed after commit
        verify(action).run();
    }

    @Test
    @DisplayName("Should_HandleException_When_ActionFailsAfterCommit")
    void Should_HandleException_When_ActionFailsAfterCommit() {
        // Given: stub port that defers execution until manual trigger
        final Runnable[] stored = new Runnable[1];
        ScanEventDispatcher txDispatcher = new ScanEventDispatcher(publishEventPort, action -> stored[0] = action);
        Runnable action = mock(Runnable.class);
        doThrow(new RuntimeException("Action failed")).when(action).run();

        // When
        txDispatcher.scheduleAfterCommit(action);

        // Then - should not throw when executing deferred action
        assertThatCode(stored[0]::run).doesNotThrowAnyException();

        // Verify action was attempted
        verify(action).run();
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
        // Given: dispatcher with deferred execution behavior
        final Runnable[] stored = new Runnable[1];
        ScanEventDispatcher txDispatcher = new ScanEventDispatcher(publishEventPort, action -> stored[0] = action);

        // When
        txDispatcher.publishAfterCommit(scanId, spaceKey);

        // Then - event should not be published immediately
        verify(publishEventPort, never()).publishCompleteEvent(any());

        // Simulate transaction commit
        stored[0].run();

        // Then - event should be published after commit
        verify(publishEventPort).publishCompleteEvent(eventCaptor.capture());
        SpaceScanCompleted event = eventCaptor.getValue();
        assertThatCode(() -> {
            org.assertj.core.api.Assertions.assertThat(event.scanId()).isEqualTo(scanId);
            org.assertj.core.api.Assertions.assertThat(event.spaceKey()).isEqualTo(spaceKey);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should_HandleException_When_PublishFailsAfterCommit")
    void Should_HandleException_When_PublishFailsAfterCommit() {
        // Given: dispatcher with deferred execution behavior
        final Runnable[] stored = new Runnable[1];
        ScanEventDispatcher txDispatcher = new ScanEventDispatcher(publishEventPort, action -> stored[0] = action);
        String scanId = "scan-789";
        String spaceKey = "space3";
        doThrow(new RuntimeException("Publish failed")).when(publishEventPort).publishCompleteEvent(any());

        // When
        txDispatcher.publishAfterCommit(scanId, spaceKey);

        // Then - should not throw when executing deferred publish
        assertThatCode(stored[0]::run).doesNotThrowAnyException();

        // Verify publish was attempted
        verify(publishEventPort).publishCompleteEvent(any());
    }

    @Test
    @DisplayName("Should_ExecuteMultipleActions_When_RegisteredInTransaction")
    void Should_ExecuteMultipleActions_When_RegisteredInTransaction() {
        // Given: dispatcher configured to defer actions until we manually trigger them (simulates TX commit)
        final java.util.List<Runnable> stored = new java.util.ArrayList<>();
        ScanEventDispatcher txDispatcher = new ScanEventDispatcher(publishEventPort, stored::add);
        Runnable action1 = mock(Runnable.class);
        Runnable action2 = mock(Runnable.class);

        // When
        txDispatcher.scheduleAfterCommit(action1);
        txDispatcher.scheduleAfterCommit(action2);

        // Then - actions should not be executed immediately
        verify(action1, never()).run();
        verify(action2, never()).run();

        // Simulate transaction commit by executing all stored callbacks
        stored.forEach(Runnable::run);

        // Then - both actions should be executed after commit
        verify(action1).run();
        verify(action2).run();
    }
}
