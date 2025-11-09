package pro.softcom.sentinelle.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pro.softcom.sentinelle.SentinelleApplication;
import pro.softcom.sentinelle.application.confluence.port.out.AttachmentTextExtractor;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentDownloader;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceUrlProvider;
import pro.softcom.sentinelle.application.pii.export.port.in.ExportDetectionReportPort;
import pro.softcom.sentinelle.application.pii.export.port.out.ReadExportContextPort;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceScanUseCase;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.domain.confluence.DataOwners;
import pro.softcom.sentinelle.domain.pii.export.ExportContext;
import pro.softcom.sentinelle.domain.pii.export.SourceType;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.DetectionEventRepository;

/**
 * Test d'intégration complet pour vérifier le flux :
 * Mock Confluence → Scan Global → Mock gRPC Detection → Persistence BD → Export Excel → Vérification Contenu
 * Ce test n'a pas besoin du service gRPC Python ni de Confluence car ils sont mockés.
 */
@Testcontainers
@SpringBootTest(classes = SentinelleApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
    ExcelExportFullScanIntegrationTest.TestConfluenceBeans.class,
    TestPiiDetectionClientConfiguration.class
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Excel Export Full Scan Integration Test")
class ExcelExportFullScanIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private StreamConfluenceScanUseCase streamConfluenceScanUseCase;

    @Autowired
    private ExportDetectionReportPort exportDetectionReportPort;

    @Autowired
    private DetectionEventRepository detectionEventRepository;

    @Autowired
    private ConfluenceClient confluenceClient;

    @Autowired
    private ConfluenceAttachmentClient confluenceAttachmentClient;

    @Autowired
    private ConfluenceUrlProvider confluenceUrlProvider;

    @Value("${pii-reporting-api.findings-export-directory}")
    private String exportDirectory;

    @BeforeEach
    void cleanupExportDirectory() throws IOException {
        Path exportDir = Path.of(exportDirectory);
        if (Files.exists(exportDir)) {
            try (Stream<Path> files = Files.list(exportDir)) {
                files.filter(path -> path.toString().endsWith(".xlsx"))
                     .forEach(path -> {
                         try {
                             Files.deleteIfExists(path);
                         } catch (IOException _) {
                             // Ignore cleanup errors
                         }
                     });
            }
        } else {
            Files.createDirectories(exportDir);
        }
    }

    @Test
    @DisplayName("Should_GenerateExcelWithAllDetections_When_FullScanCompleted")
    void Should_GenerateExcelWithAllDetections_When_FullScanCompleted() throws Exception {
        // ============================================
        // PHASE 1: ARRANGE - Mock Confluence Content
        // ============================================
        
        var space = createTestSpace("TEST", "Test Space for Excel Export");
        var page1 = createPageWithEmailsAndPhones("page-1", "TEST");
        var page2 = createPageWithAVS("page-2", "TEST");
        var page3 = createPageWithSecurityData("page-3", "TEST");
        
        when(confluenceClient.getAllSpaces())
            .thenReturn(CompletableFuture.completedFuture(List.of(space)));
        when(confluenceClient.getAllPagesInSpace("TEST"))
            .thenReturn(CompletableFuture.completedFuture(List.of(page1, page2, page3)));
        when(confluenceAttachmentClient.getPageAttachments(anyString()))
            .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(confluenceUrlProvider.baseUrl())
            .thenReturn("http://test.confluence.local");
        
        // ============================================
        // PHASE 2: ACT - Run Full Scan
        // ============================================
        
        AtomicReference<String> scanIdRef = new AtomicReference<>();
        
        streamConfluenceScanUseCase.streamAllSpaces()
            .doOnNext(event -> scanIdRef.compareAndSet(null, event.scanId()))
            .blockLast(Duration.ofSeconds(30));
        
        String scanId = scanIdRef.get();
        assertThat(scanId)
            .as("Scan ID should be generated")
            .isNotBlank();
        
        // ============================================
        // PHASE 3: ACT - Export to Excel
        // ============================================
        
        exportDetectionReportPort.export(scanId, SourceType.CONFLUENCE, "TEST");
        
        // ============================================
        // PHASE 4: ASSERT - Verify Excel File Exists
        // ============================================
        
        Path exportDir = Path.of(exportDirectory);
        List<Path> excelFiles;
        try (Stream<Path> files = Files.list(exportDir)) {
            excelFiles = files
                .filter(path -> path.toString().endsWith(".xlsx"))
                .toList();
        }
        
        assertThat(excelFiles)
            .as("An Excel file should be generated")
            .hasSize(1);
        
        Path excelFile = excelFiles.get(0);
        assertThat(excelFile)
            .as("Excel file should exist on disk")
            .exists();
        
        // ============================================
        // PHASE 5: ASSERT - Verify Excel Content
        // ============================================
        
        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(excelFile))) {
            verifySummarySheet(workbook, scanId);
            verifyDetectionsSheet(workbook);
        }
    }

    private void verifySummarySheet(Workbook workbook, String scanId) {
        Sheet summarySheet = workbook.getSheet("Space Summary");
        assertThat(summarySheet)
            .as("Space Summary sheet should exist")
            .isNotNull();
        
        SoftAssertions softly = new SoftAssertions();
        
        // Row 0: Name
        softly.assertThat(getCellValue(summarySheet, 0, 0))
            .as("Summary row 0 col 0 should be 'Name'")
            .isEqualTo("Name");
        softly.assertThat(getCellValue(summarySheet, 0, 1))
            .as("Summary row 0 col 1 should be space name")
            .isEqualTo("Test Space for Excel Export");
        
        // Row 1: URL
        softly.assertThat(getCellValue(summarySheet, 1, 0))
            .as("Summary row 1 col 0 should be 'URL'")
            .isEqualTo("URL");
        softly.assertThat(getCellValue(summarySheet, 1, 1))
            .as("Summary row 1 col 1 should contain base URL")
            .contains("test.confluence.local");
        
        // Row 2: ScanID
        softly.assertThat(getCellValue(summarySheet, 2, 0))
            .as("Summary row 2 col 0 should be 'ScanID'")
            .isEqualTo("ScanID");
        softly.assertThat(getCellValue(summarySheet, 2, 1))
            .as("Summary row 2 col 1 should be the scan ID")
            .isEqualTo(scanId);
        
        softly.assertAll();
    }

    private void verifyDetectionsSheet(Workbook workbook) {
        Sheet detectionsSheet = workbook.getSheet("Detection Report");
        assertThat(detectionsSheet)
            .as("Detection Report sheet should exist")
            .isNotNull();
        
        // Vérifier les headers
        Row headerRow = detectionsSheet.getRow(0);
        assertThat(headerRow)
            .as("Header row should exist")
            .isNotNull();
        
        SoftAssertions softly = new SoftAssertions();
        
        softly.assertThat(getCellValue(headerRow, 0))
            .as("Column 0 header")
            .isEqualTo("emittedAt");
        softly.assertThat(getCellValue(headerRow, 1))
            .as("Column 1 header")
            .isEqualTo("Page Title");
        softly.assertThat(getCellValue(headerRow, 2))
            .as("Column 2 header")
            .isEqualTo("Page Url");
        softly.assertThat(getCellValue(headerRow, 5))
            .as("Column 5 header")
            .isEqualTo("Confidence Score");
        softly.assertThat(getCellValue(headerRow, 6))
            .as("Column 6 header")
            .isEqualTo("PII Type");
        softly.assertThat(getCellValue(headerRow, 7))
            .as("Column 7 header")
            .isEqualTo("PII Context");
        
        // Compter les détections (sans compter le header)
        int lastRowNum = detectionsSheet.getLastRowNum();
        int totalDetections = lastRowNum; // Row 0 est le header
        
        softly.assertThat(totalDetections)
            .as("Should have at least one detection row")
            .isGreaterThan(0);
        
        // Collecter tous les types PII détectés
        List<String> detectedTypes = new ArrayList<>();
        List<String> pageTitles = new ArrayList<>();
        
        for (int i = 1; i <= lastRowNum; i++) {
            Row row = detectionsSheet.getRow(i);
            if (row != null) {
                String pageTitle = getCellValue(row, 1);
                String piiType = getCellValue(row, 6);
                
                if (pageTitle != null) {
                    pageTitles.add(pageTitle);
                }
                if (piiType != null) {
                    detectedTypes.add(piiType);
                }
                
                // Vérifier que le confidence score est valide
                Cell scoreCell = row.getCell(5);
                if (scoreCell != null && scoreCell.getCellType() == CellType.NUMERIC) {
                    double score = scoreCell.getNumericCellValue();
                    softly.assertThat(score)
                        .as("Confidence score at row " + i + " should be between 0 and 1")
                        .isBetween(0.0, 1.0);
                }
            }
        }
        
        // Vérifier qu'on a des détections de différents types
        // Note: L'Excel utilise les labels français avec majuscules
        softly.assertThat(detectedTypes)
            .as("Should detect EMAIL type")
            .contains("Email");
        
        softly.assertThat(detectedTypes)
            .as("Should detect PHONE type")
            .contains("Téléphone");
        
        softly.assertThat(detectedTypes)
            .as("Should detect AVS type")
            .contains("AVS");
        
        // Vérifier que toutes les pages ont été scannées
        softly.assertThat(pageTitles)
            .as("Should have detections from all pages")
            .isNotEmpty();
        
        softly.assertAll();
    }

    private String getCellValue(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) {
            return null;
        }
        return getCellValue(row, colIdx);
    }

    private String getCellValue(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) {
            return null;
        }
        
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                } else {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    private ConfluenceSpace createTestSpace(String key, String name) {
        return new ConfluenceSpace(
            "id-" + key,
            key,
            name,
            "http://test.confluence.local/spaces/" + key,
            "Test space for integration testing",
            ConfluenceSpace.SpaceType.GLOBAL,
            ConfluenceSpace.SpaceStatus.CURRENT,
            new DataOwners.NotLoaded(),
            null
        );
    }

    private ConfluencePage createPageWithEmailsAndPhones(String id, String spaceKey) {
        return ConfluencePage.builder()
            .id(id)
            .title("Contact Information")
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent("""
                <html>
                <body>
                <h1>Contacts d'équipe</h1>
                <p>Email principal: john.doe@example.com</p>
                <p>Email support: support@company.ch</p>
                <p>Téléphone: +41 22 123 45 67</p>
                <p>Mobile: +41 79 456 78 90</p>
                <p>Contact: marie.dupont@company.com</p>
                </body>
                </html>
                """))
            .metadata(new ConfluencePage.PageMetadata(
                "testuser",
                LocalDateTime.now().minusDays(1),
                "testuser",
                LocalDateTime.now().minusDays(1),
                1,
                "current"
            ))
            .labels(List.of())
            .customProperties(null)
            .build();
    }

    private ConfluencePage createPageWithAVS(String id, String spaceKey) {
        return ConfluencePage.builder()
            .id(id)
            .title("Employee Records")
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent("""
                <html>
                <body>
                <h1>Données Employés</h1>
                <p>Employé 1 - AVS: 756.1234.5678.90</p>
                <p>Contact: employee1@company.com</p>
                <p>Employé 2 - AVS: 756.9876.5432.10</p>
                <p>Contact: employee2@company.com</p>
                </body>
                </html>
                """))
            .metadata(new ConfluencePage.PageMetadata(
                "testuser",
                LocalDateTime.now().minusDays(2),
                "testuser",
                LocalDateTime.now().minusDays(2),
                1,
                "current"
            ))
            .labels(List.of())
            .customProperties(null)
            .build();
    }

    private ConfluencePage createPageWithSecurityData(String id, String spaceKey) {
        return ConfluencePage.builder()
            .id(id)
            .title("System Configuration")
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent("""
                <html>
                <body>
                <h1>Configuration Système</h1>
                <p>API Key: sk-1234567890abcdef</p>
                <p>Database Password: SecurePass123!</p>
                <p>Admin URL: https://api.internal.company.com</p>
                <p>Server IP: 192.168.1.100</p>
                </body>
                </html>
                """))
            .metadata(new ConfluencePage.PageMetadata(
                "testuser",
                LocalDateTime.now().minusDays(3),
                "testuser",
                LocalDateTime.now().minusDays(3),
                1,
                "current"
            ))
            .labels(List.of())
            .customProperties(null)
            .build();
    }

    @TestConfiguration
    static class TestConfluenceBeans {

        @Bean
        @Primary
        ConfluenceClient confluenceClient() {
            return Mockito.mock(ConfluenceClient.class);
        }

        @Bean
        @Primary
        ConfluenceAttachmentClient confluenceAttachmentClient() {
            return Mockito.mock(ConfluenceAttachmentClient.class);
        }

        @Bean
        @Primary
        ConfluenceAttachmentDownloader confluenceAttachmentDownloader() {
            return Mockito.mock(ConfluenceAttachmentDownloader.class);
        }

        @Bean
        @Primary
        ConfluenceUrlProvider confluenceUrlProvider() {
            return Mockito.mock(ConfluenceUrlProvider.class);
        }

        @Bean
        @Primary
        AttachmentTextExtractor attachmentTextExtractor() {
            return Mockito.mock(AttachmentTextExtractor.class);
        }

        @Bean
        @Primary
        ReadExportContextPort readExportContextPort() {
            ReadExportContextPort mock = Mockito.mock(ReadExportContextPort.class);
            
            // Mock the export context for TEST space
            ExportContext testSpaceContext = ExportContext.builder()
                .reportName("Test Space for Excel Export")
                .reportIdentifier("TEST")
                .sourceUrl("http://test.confluence.local/spaces/TEST")
                .contacts(List.of())
                .additionalMetadata(java.util.Map.of())
                .build();
            
            when(mock.findContext(SourceType.CONFLUENCE, "TEST"))
                .thenReturn(testSpaceContext);
            
            return mock;
        }
    }
}
