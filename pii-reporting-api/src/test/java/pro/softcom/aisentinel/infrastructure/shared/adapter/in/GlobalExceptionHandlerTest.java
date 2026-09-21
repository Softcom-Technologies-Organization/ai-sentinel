package pro.softcom.aisentinel.infrastructure.shared.adapter.in;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pro.softcom.aisentinel.application.confluence.exception.ConfluenceSpaceCacheException;
import pro.softcom.aisentinel.application.confluence.exception.ConfluenceSpaceNotFoundException;
import pro.softcom.aisentinel.application.pii.export.exception.ExportContextNotFoundException;
import pro.softcom.aisentinel.application.pii.export.exception.ExportException;
import pro.softcom.aisentinel.application.pii.export.exception.UnsupportedSourceTypeException;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorException;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.judge.JudgeScanInProgressException;
import pro.softcom.aisentinel.domain.pii.remediation.AttachmentRedactionUnsupportedException;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.remediation.IllegalStatusTransitionException;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationDisabledException;
import pro.softcom.aisentinel.domain.pii.remediation.SelectionOutdatedException;
import pro.softcom.aisentinel.domain.pii.scan.IllegalScanStatusTransitionException;
import pro.softcom.aisentinel.domain.pii.scan.Initiator;
import pro.softcom.aisentinel.domain.pii.scan.ScanNotFoundException;
import pro.softcom.aisentinel.domain.pii.security.CryptographicOperationException;
import pro.softcom.aisentinel.domain.pii.security.EncryptionException;
import pro.softcom.aisentinel.domain.pii.security.PiiAccessDeniedException;
import pro.softcom.aisentinel.infrastructure.config.SecurityConfig;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.ConfluenceApiException;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.ConfluenceAuthenticationException;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.ConfluenceConnectionException;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.ConfluenceDateParseException;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.ConfluenceNotFoundException;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.parser.ConfluenceDeserializationException;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.PiiDetectionException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ExceptionThrowingTestController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@DisplayName("GlobalExceptionHandler - Central exception to ProblemDetail mapping")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger handlerLogger;

    @BeforeEach
    void setUpLogCapture() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        handlerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDownLogCapture() {
        handlerLogger.detachAppender(logAppender);
    }

    // ========== Confluence sealed subclasses (5) ==========

    @Test
    @DisplayName("Should_Return401WithErrorKey_When_ConfluenceAuthenticationFails")
    void Should_Return401WithErrorKey_When_ConfluenceAuthenticationFails() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new ConfluenceAuthenticationException("Auth failed", 401));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorKey").value("error.confluence.auth.failed"));
    }

    @Test
    @DisplayName("Should_Return503WithErrorKey_When_ConfluenceConnectionFails")
    void Should_Return503WithErrorKey_When_ConfluenceConnectionFails() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new ConfluenceConnectionException("Connection refused", new IOException("refused")));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorKey").value("error.confluence.connection.failed"));
    }

    @Test
    @DisplayName("Should_Return404WithErrorKey_When_ConfluenceResourceNotFound")
    void Should_Return404WithErrorKey_When_ConfluenceResourceNotFound() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new ConfluenceNotFoundException("123", "Page"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value("error.confluence.resource.not_found"));
    }

    @Test
    @DisplayName("Should_Return502WithErrorKey_When_ConfluenceApiError")
    void Should_Return502WithErrorKey_When_ConfluenceApiError() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new ConfluenceApiException("API error", 500, "Internal Server Error"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorKey").value("error.confluence.api.error"));
    }

    @Test
    @DisplayName("Should_Return502WithErrorKey_When_ConfluenceDateParseFails")
    void Should_Return502WithErrorKey_When_ConfluenceDateParseFails() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new ConfluenceDateParseException("2024-99-99", new RuntimeException("parse error")));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorKey").value("error.confluence.date.parse_failed"));
    }

    // ========== Standalone Confluence exceptions (3) ==========

    @Test
    @DisplayName("Should_Return502WithErrorKey_When_ConfluenceDeserializationFails")
    void Should_Return502WithErrorKey_When_ConfluenceDeserializationFails() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new ConfluenceDeserializationException("Bad JSON", new RuntimeException("parse")));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorKey").value("error.confluence.deserialization.failed"));
    }

    @Test
    @DisplayName("Should_Return404WithErrorKey_When_ConfluenceSpaceNotFound")
    void Should_Return404WithErrorKey_When_ConfluenceSpaceNotFound() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new ConfluenceSpaceNotFoundException("TEST"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value("error.confluence.space.not_found"));
    }

    @Test
    @DisplayName("Should_Return503WithErrorKey_When_ConfluenceSpaceCacheError")
    void Should_Return503WithErrorKey_When_ConfluenceSpaceCacheError() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new ConfluenceSpaceCacheException("Cache refresh failed", "refresh"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorKey").value("error.confluence.space.cache_error"));
    }

    // ========== PiiDetectionException inner classes (3) ==========

    @Test
    @DisplayName("Should_Return503WithErrorKey_When_PiiDetectionConnectionFails")
    void Should_Return503WithErrorKey_When_PiiDetectionConnectionFails() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(PiiDetectionException.connectionError("Connection failed", new IOException("refused")));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorKey").value("error.pii.detection.connection_failed"));
    }

    @Test
    @DisplayName("Should_Return502WithErrorKey_When_PiiDetectionServiceError")
    void Should_Return502WithErrorKey_When_PiiDetectionServiceError() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(PiiDetectionException.serviceError("Service error"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorKey").value("error.pii.detection.service_error"));
    }

    @Test
    @DisplayName("Should_Return504WithErrorKey_When_PiiDetectionTimeout")
    void Should_Return504WithErrorKey_When_PiiDetectionTimeout() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(PiiDetectionException.timeoutError("Timeout after 30s"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.errorKey").value("error.pii.detection.timeout"));
    }

    // ========== PII / Encryption / Security (4) ==========

    @Test
    @DisplayName("Should_Return502WithErrorKey_When_PiiDetectorError")
    void Should_Return502WithErrorKey_When_PiiDetectorError() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(PiiDetectorException.serviceError("Detector failed", new RuntimeException("error")));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorKey").value("error.pii.detector.error"));
    }

    @Test
    @DisplayName("Should_Return500WithErrorKey_When_EncryptionFails")
    void Should_Return500WithErrorKey_When_EncryptionFails() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new EncryptionException("Encryption failed"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorKey").value("error.encryption.failed"));
    }

    @Test
    @DisplayName("Should_Return500WithErrorKey_When_CryptoOperationFails")
    void Should_Return500WithErrorKey_When_CryptoOperationFails() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new CryptographicOperationException("Crypto failed"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorKey").value("error.crypto.operation_failed"));
    }

    @Test
    @DisplayName("Should_Return403WithErrorKey_When_PiiAccessDenied")
    void Should_Return403WithErrorKey_When_PiiAccessDenied() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new PiiAccessDeniedException("Access denied"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorKey").value("error.access.denied"));
    }

    // ========== Scan exceptions (2) ==========

    @Test
    @DisplayName("Should_Return409WithErrorKey_When_InvalidScanStatusTransition")
    void Should_Return409WithErrorKey_When_InvalidScanStatusTransition() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new IllegalScanStatusTransitionException(ScanStatus.COMPLETED, ScanStatus.RUNNING, Initiator.USER));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey").value("error.scan.invalid_status_transition"));
    }

    @Test
    @DisplayName("Should_Return404WithErrorKey_When_ScanNotFound")
    void Should_Return404WithErrorKey_When_ScanNotFound() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new ScanNotFoundException("scan-123"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value("error.scan.not_found"));
    }

    // ========== Remediation exceptions (4) ==========

    @Test
    @DisplayName("Should_Return403WithErrorKey_When_RemediationDisabled")
    void Should_Return403WithErrorKey_When_RemediationDisabled() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new RemediationDisabledException("disabled"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorKey").value("error.remediation.disabled"));
    }

    @Test
    @DisplayName("Should_Return409WithErrorKey_When_IllegalFindingStatusTransition")
    void Should_Return409WithErrorKey_When_IllegalFindingStatusTransition() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new IllegalStatusTransitionException(
                FindingRemediationStatus.REDACTED, FindingRemediationStatus.PENDING));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey").value("error.remediation.invalid_status_transition"));
    }

    @Test
    @DisplayName("Should_Return422WithErrorKey_When_AttachmentRedactionUnsupported")
    void Should_Return422WithErrorKey_When_AttachmentRedactionUnsupported() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(
                new AttachmentRedactionUnsupportedException("attachments are not redactable"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorKey").value("error.remediation.attachment_not_supported"));
    }

    @Test
    @DisplayName("Should_Return409WithErrorKey_When_SelectionOutdated")
    void Should_Return409WithErrorKey_When_SelectionOutdated() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new SelectionOutdatedException("checksum mismatch"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey").value("error.remediation.selection_outdated"));
    }

    // ========== Export exceptions (3) ==========

    @Test
    @DisplayName("Should_Return500WithErrorKey_When_ExportFails")
    void Should_Return500WithErrorKey_When_ExportFails() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new ExportException("Export failed"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorKey").value("error.export.failed"));
    }

    @Test
    @DisplayName("Should_Return404WithErrorKey_When_ExportContextNotFound")
    void Should_Return404WithErrorKey_When_ExportContextNotFound() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new ExportContextNotFoundException("CONFLUENCE", "space-1"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value("error.export.context_not_found"));
    }

    @Test
    @DisplayName("Should_Return400WithErrorKey_When_UnsupportedSourceType")
    void Should_Return400WithErrorKey_When_UnsupportedSourceType() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new UnsupportedSourceTypeException("SHAREPOINT"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("error.export.unsupported_source_type"));
    }

    // ========== Validation / Generic (5) ==========

    @Test
    @DisplayName("Should_Return400WithErrorKey_When_IllegalArgument")
    void Should_Return400WithErrorKey_When_IllegalArgument() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new IllegalArgumentException("Invalid parameter"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("error.validation.invalid_argument"));
    }

    @Test
    void Should_Return409WithErrorKey_When_JudgeLaunchBlockedByScan() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new JudgeScanInProgressException());

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey").value("error.judge.scan_in_progress"));
    }

    @Test
    @DisplayName("Should_Return409WithErrorKey_When_IllegalState")
    void Should_Return409WithErrorKey_When_IllegalState() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new IllegalStateException("Invalid state"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey").value("error.state.invalid"));
    }

    @Test
    @DisplayName("Should_Return400WithErrorKey_When_MalformedJsonReceived")
    void Should_Return400WithErrorKey_When_MalformedJsonReceived() throws Exception {
        mockMvc.perform(post("/test/throw-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("error.validation.malformed_request"));
    }

    @Test
    @DisplayName("Should_Return400WithErrorKey_When_BeanValidationFails")
    void Should_Return400WithErrorKey_When_BeanValidationFails() throws Exception {
        mockMvc.perform(post("/test/throw-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("error.validation.constraint_violation"));
    }

    @Test
    @DisplayName("Should_Return500WithGenericKey_When_UnexpectedException")
    void Should_Return500WithGenericKey_When_UnexpectedException() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new RuntimeException("Something unexpected"));

        mockMvc.perform(get("/test/throw"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorKey").value("error.internal"));
    }

    // ========== Log level tests (2) ==========

    @Test
    @DisplayName("Should_LogAtWarnLevel_When_ClientError4xx")
    void Should_LogAtWarnLevel_When_ClientError4xx() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new PiiAccessDeniedException("Access denied for log test"));

        mockMvc.perform(get("/test/throw"));

        assertThat(logAppender.list)
                .filteredOn(event -> event.getFormattedMessage().contains("Access denied for log test"))
                .extracting(ILoggingEvent::getLevel)
                .containsExactly(Level.WARN);
    }

    @Test
    @DisplayName("Should_LogAtErrorLevel_When_ServerError5xx")
    void Should_LogAtErrorLevel_When_ServerError5xx() throws Exception {
        ExceptionThrowingTestController.exceptionToThrow.set(new EncryptionException("Encryption failed for log test"));

        mockMvc.perform(get("/test/throw"));

        assertThat(logAppender.list)
                .filteredOn(event -> event.getFormattedMessage().contains("Encryption failed for log test"))
                .extracting(ILoggingEvent::getLevel)
                .containsExactly(Level.ERROR);
    }
}
