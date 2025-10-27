package pro.softcom.sentinelle.infrastructure.confluence.adapter.in;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pro.softcom.sentinelle.application.confluence.port.in.ConfluenceUseCase;
import pro.softcom.sentinelle.application.confluence.port.in.GetSpaceUpdateInfoUseCase;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;

/**
 * Tests for the ConfluenceController class.
 * These tests verify that the controller correctly processes requests and returns responses.
 */
@WebMvcTest(ConfluenceController.class)
class ConfluenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfluenceUseCase confluenceUseCase;

    @MockitoBean
    private GetSpaceUpdateInfoUseCase getSpaceUpdateInfoUseCase;

    /**
     * Tests that the controller correctly returns all pages in a space.
     */
    @Test
    void getAllPagesInSpace_ReturnsListOfPages() throws Exception {
        // Arrange
        String spaceKey = "TEST";

        // Create test data
        List<ConfluencePage> pages = List.of(
            ConfluencePage.builder()
                .id("page1")
                .title("Page 1")
                .spaceKey(spaceKey)
                .content(new ConfluencePage.HtmlContent("<p>Content 1</p>"))
                .build(),
            ConfluencePage.builder()
                .id("page2")
                .title("Page 2")
                .spaceKey(spaceKey)
                .content(new ConfluencePage.HtmlContent("<p>Content 2</p>"))
                .build(),
            ConfluencePage.builder()
                .id("page3")
                .title("Page 3")
                .spaceKey(spaceKey)
                .content(new ConfluencePage.HtmlContent("<p>Content 3</p>"))
                .build()
        );

        // Mock the space existence check
        ConfluenceSpace space = new ConfluenceSpace(
            "space-id-1",
            spaceKey,
            "Test Space",
            "http://test.com",
            "A test space",
            ConfluenceSpace.SpaceType.GLOBAL,
            ConfluenceSpace.SpaceStatus.CURRENT,
            null
        );

        when(confluenceUseCase.getSpace(spaceKey))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(space)));

        // Mock the service to return the test pages
        when(confluenceUseCase.getAllPagesInSpace(spaceKey))
            .thenReturn(CompletableFuture.completedFuture(pages));

        // Act
        var mvcResult = mockMvc.perform(get("/api/v1/confluence/spaces/{spaceKey}/pages", spaceKey)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Assert
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value("page1"))
                .andExpect(jsonPath("$[0].title").value("Page 1"))
                .andExpect(jsonPath("$[1].id").value("page2"))
                .andExpect(jsonPath("$[1].title").value("Page 2"))
                .andExpect(jsonPath("$[2].id").value("page3"))
                .andExpect(jsonPath("$[2].title").value("Page 3"));
    }

    /**
     * Tests that the controller returns a 404 when the space does not exist.
     */
    @Test
    void getAllPagesInSpace_ReturnsNotFoundWhenSpaceDoesNotExist() throws Exception {
        // Arrange
        String spaceKey = "NONEXISTENT";

        // Mock the space existence check to return empty
        when(confluenceUseCase.getSpace(spaceKey))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        // Act
        var mvcResult = mockMvc.perform(get("/api/v1/confluence/spaces/{spaceKey}/pages", spaceKey)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Assert
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isNotFound());
    }

    /**
     * Tests that the controller correctly returns a page by ID.
     */
    @Test
    void getPage_ReturnsPage() throws Exception {
        // Arrange
        String pageId = "page1";

        // Create test data
        ConfluencePage page = ConfluencePage.builder()
            .id(pageId)
            .title("Test Page")
            .spaceKey("TEST")
            .content(new ConfluencePage.HtmlContent("<p>Test content</p>"))
            .build();

        // Mock the service to return the test page
        when(confluenceUseCase.getPage(pageId))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(page)));

        // Act
        var mvcResult = mockMvc.perform(get("/api/v1/confluence/pages/{pageId}", pageId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Assert
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pageId))
                .andExpect(jsonPath("$.title").value("Test Page"))
                .andExpect(jsonPath("$.spaceKey").value("TEST"));
    }

    /**
     * Tests that the controller returns a 404 when the page does not exist.
     */
    @Test
    void getPage_ReturnsNotFoundWhenPageDoesNotExist() throws Exception {
        // Arrange
        String pageId = "nonexistent";

        // Mock the service to return empty
        when(confluenceUseCase.getPage(pageId))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        // Act
        var mvcResult = mockMvc.perform(get("/api/v1/confluence/pages/{pageId}", pageId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Assert
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isNotFound());
        }

    @Test
    void checkHealth_ReturnsUpStatus() throws Exception {
        when(confluenceUseCase.testConnection())
            .thenReturn(CompletableFuture.completedFuture(true));

        var mvcResult = mockMvc.perform(get("/api/v1/confluence/health"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void checkHealth_ReturnsDownStatus() throws Exception {
        when(confluenceUseCase.testConnection())
            .thenReturn(CompletableFuture.completedFuture(false));

        var mvcResult = mockMvc.perform(get("/api/v1/confluence/health"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void getSpace_ReturnsSpace() throws Exception {
        String spaceKey = "TEST";
        ConfluenceSpace space = new ConfluenceSpace(
            "space-id-1",
            spaceKey,
            "Test Space",
            "http://test.com",
            "A test space",
            ConfluenceSpace.SpaceType.GLOBAL,
            ConfluenceSpace.SpaceStatus.CURRENT,
            null
        );

        when(confluenceUseCase.getSpace(spaceKey))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(space)));

        var mvcResult = mockMvc.perform(get("/api/v1/confluence/spaces/{spaceKey}", spaceKey))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value(spaceKey))
            .andExpect(jsonPath("$.name").value("Test Space"));
    }

    @Test
    void getSpace_ReturnsNotFound() throws Exception {
        String spaceKey = "UNKNOWN";
        when(confluenceUseCase.getSpace(spaceKey))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        var mvcResult = mockMvc.perform(get("/api/v1/confluence/spaces/{spaceKey}", spaceKey))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isNotFound());
    }

    @Test
    void getAllSpaces_ReturnsList() throws Exception {
        List<ConfluenceSpace> spaces = List.of(
            new ConfluenceSpace("id-1", "KEY1", "Space 1", "http://s1", "desc", ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, null),
            new ConfluenceSpace("id-2", "KEY2", "Space 2", "http://s2", "desc", ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, null)
        );
        when(confluenceUseCase.getAllSpaces())
            .thenReturn(CompletableFuture.completedFuture(spaces));

        var mvcResult = mockMvc.perform(get("/api/v1/confluence/spaces"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].key").value("KEY1"))
            .andExpect(jsonPath("$[1].key").value("KEY2"));
    }

    @Test
    void getAllSpaces_Returns500_WhenServiceFails() throws Exception {
        when(confluenceUseCase.getAllSpaces())
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

        var mvcResult = mockMvc.perform(get("/api/v1/confluence/spaces"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void searchPages_ReturnsLimitedResults() throws Exception {
        String spaceKey = "TEST";
        List<ConfluencePage> pages = List.of(
            ConfluencePage.builder().id("p1").title("T1").spaceKey(spaceKey).content(new ConfluencePage.HtmlContent("<p>1</p>")).build(),
            ConfluencePage.builder().id("p2").title("T2").spaceKey(spaceKey).content(new ConfluencePage.HtmlContent("<p>2</p>")).build(),
            ConfluencePage.builder().id("p3").title("T3").spaceKey(spaceKey).content(new ConfluencePage.HtmlContent("<p>3</p>")).build()
        );
        when(confluenceUseCase.searchPages(spaceKey, "term"))
            .thenReturn(CompletableFuture.completedFuture(pages));

        var mvcResult = mockMvc.perform(get("/api/v1/confluence/spaces/{spaceKey}/search", spaceKey)
                .param("query", "term")
                .param("limit", "2"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.query").value("term"))
            .andExpect(jsonPath("$.results", hasSize(2)))
            .andExpect(jsonPath("$.totalResults").value(2));
    }
}
