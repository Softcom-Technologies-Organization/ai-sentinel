package pro.softcom.aisentinel.application.pii.reporting.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;

import java.net.SocketException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests that ScanCheckpointService correctly detects and logs interruption-caused errors.
 * Bug fix: Ensure interruption errors are logged as INFO, not WARN.
 */
@ExtendWith(MockitoExtension.class)
class ScanCheckpointServiceInterruptionErrorDetectionTest {

    @Mock
    private ScanCheckpointRepository scanCheckpointRepository;

    @InjectMocks
    private ScanCheckpointService scanCheckpointService;

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(ScanCheckpointService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    @Test
    void Should_LogInfo_When_ExceptionMessageContainsInterrupt() {
        // Given: Exception with "interrupt" in message
        ConfluenceContentScanResult result = createValidScanResult();
        RuntimeException exception = new RuntimeException("Connection interrupted by user");
        
        doThrow(exception).when(scanCheckpointRepository).save(any());

        // When: Persisting checkpoint
        scanCheckpointService.persistCheckpoint(result);

        // Then: Should log as INFO, not WARN
        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
            .anyMatch(tuple -> 
                tuple.equals(org.assertj.core.groups.Tuple.tuple(
                    Level.INFO, 
                    "[CHECKPOINT] Checkpoint persistence interrupted (SSE disconnection): Connection interrupted by user"
                )));
        
        // And: Should NOT log as WARN
        assertThat(listAppender.list)
            .filteredOn(event -> event.getLevel() == Level.WARN)
            .isEmpty();
    }

    @Test
    void Should_LogInfo_When_CauseIsSocketExceptionWithInterrupt() {
        // Given: SQLException wrapping SocketException with "interrupt" message
        ConfluenceContentScanResult result = createValidScanResult();
        SocketException socketException = new SocketException("Closed by interrupt");
        SQLException sqlException = new SQLException("I/O error occurred while sending to the backend", socketException);
        RuntimeException exception = new RuntimeException("JDBC exception", sqlException);
        
        doThrow(exception).when(scanCheckpointRepository).save(any());

        // When: Persisting checkpoint
        scanCheckpointService.persistCheckpoint(result);

        // Then: Should log as INFO
        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getLevel)
            .contains(Level.INFO);
        
        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .anyMatch(msg -> msg.contains("interrupted (SSE disconnection)"));
        
        // And: Should NOT log as WARN
        assertThat(listAppender.list)
            .filteredOn(event -> event.getLevel() == Level.WARN)
            .isEmpty();
    }

    @Test
    void Should_LogInfo_When_CauseIsInterruptedException() {
        // Given: Exception wrapping InterruptedException
        ConfluenceContentScanResult result = createValidScanResult();
        InterruptedException interruptedException = new InterruptedException();
        RuntimeException exception = new RuntimeException("Operation interrupted", interruptedException);
        
        doThrow(exception).when(scanCheckpointRepository).save(any());

        // When: Persisting checkpoint
        scanCheckpointService.persistCheckpoint(result);

        // Then: Should log as INFO
        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getLevel)
            .contains(Level.INFO);
        
        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .anyMatch(msg -> msg.contains("interrupted (SSE disconnection)"));
        
        // And: Should NOT log as WARN
        assertThat(listAppender.list)
            .filteredOn(event -> event.getLevel() == Level.WARN)
            .isEmpty();
    }

    @Test
    void Should_LogWarn_When_ExceptionIsNotInterruptionRelated() {
        // Given: Regular exception not related to interruption
        ConfluenceContentScanResult result = createValidScanResult();
        RuntimeException exception = new RuntimeException("Database connection timeout");
        
        doThrow(exception).when(scanCheckpointRepository).save(any());

        // When: Persisting checkpoint
        scanCheckpointService.persistCheckpoint(result);

        // Then: Should log as WARN
        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getLevel)
            .contains(Level.WARN);
        
        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .anyMatch(msg -> msg.contains("Unable to persist checkpoint"));
        
        // And: Should NOT log as INFO for this error
        assertThat(listAppender.list)
            .filteredOn(event -> 
                event.getLevel() == Level.INFO && 
                event.getFormattedMessage().contains("interrupted"))
            .isEmpty();
    }

    @Test
    void Should_NotInteractWithRepository_When_ScanResultInvalid() {
        // Given: Invalid scan result (no spaceKey)
        ConfluenceContentScanResult result = ConfluenceContentScanResult.builder()
            .scanId("scan-123")
            .eventType("pageComplete")
            .build();

        // When: Persisting invalid checkpoint
        scanCheckpointService.persistCheckpoint(result);

        // Then: Should not call repository
        verify(scanCheckpointRepository, never()).save(any());
        
        // And: Should not log any errors
        assertThat(listAppender.list)
            .filteredOn(event -> event.getLevel() == Level.WARN || event.getLevel() == Level.ERROR)
            .isEmpty();
    }

    private ConfluenceContentScanResult createValidScanResult() {
        return ConfluenceContentScanResult.builder()
            .scanId("scan-123")
            .spaceKey("TEST")
            .eventType("pageComplete")
            .pageId("page-456")
            .build();
    }
}
