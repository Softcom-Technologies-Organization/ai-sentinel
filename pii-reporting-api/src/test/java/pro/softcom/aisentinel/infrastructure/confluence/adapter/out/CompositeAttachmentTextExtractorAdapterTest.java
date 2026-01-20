package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompositeAttachmentTextExtractorAdapterTest {

    @Mock
    private AttachmentTextExtractionStrategy ex1;
    @Mock
    private AttachmentTextExtractionStrategy ex2;
    @Mock
    private AttachmentTextExtractionStrategy ex3;

    @Test
    void should_return_empty_when_info_or_bytes_invalid() {
        CompositeAttachmentTextExtractorAdapter service = new CompositeAttachmentTextExtractorAdapter(List.of(ex1));
        AttachmentInfo info = new AttachmentInfo("a.txt", "txt", "text/plain", "url");

        assertSoftly(soft -> {
            soft.assertThat(service.extractText(null, "hi".getBytes(StandardCharsets.UTF_8))).as("null info").isEmpty();
            soft.assertThat(service.extractText(info, null)).as("null bytes").isEmpty();
            soft.assertThat(service.extractText(info, new byte[0])).as("empty bytes").isEmpty();
        });

        // no interactions should occur due to early return
        verifyNoInteractions(ex1);
    }

    @Test
    void should_return_empty_when_no_extractors_configured_or_null_list() {
        AttachmentInfo info = new AttachmentInfo("a.txt", "txt", "text/plain", "url");
        byte[] bytes = "content".getBytes(StandardCharsets.UTF_8);

        // empty list
        CompositeAttachmentTextExtractorAdapter serviceEmpty = new CompositeAttachmentTextExtractorAdapter(List.of());
        assertThat(serviceEmpty.extractText(info, bytes)).isEmpty();

        // null list (constructor replaces with empty list)
        CompositeAttachmentTextExtractorAdapter serviceNull = new CompositeAttachmentTextExtractorAdapter(null);
        assertThat(serviceNull.extractText(info, bytes)).isEmpty();
    }

    @Test
    void should_skip_non_supporting_and_return_empty_if_none_support() {
        CompositeAttachmentTextExtractorAdapter service = new CompositeAttachmentTextExtractorAdapter(List.of(ex1, ex2));
        AttachmentInfo info = new AttachmentInfo("a.txt", "txt", "text/plain", "url");
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);

        when(ex1.supports(info)).thenReturn(false);
        when(ex2.supports(info)).thenReturn(false);

        Optional<String> out = service.extractText(info, bytes);
        assertThat(out).isEmpty();

        verify(ex1).supports(info);
        verify(ex2).supports(info);
        verify(ex1, never()).extract(any(), any());
        verify(ex2, never()).extract(any(), any());
    }

    @Test
    void should_continue_when_first_supporting_returns_empty_and_return_next_present() {
        CompositeAttachmentTextExtractorAdapter service = new CompositeAttachmentTextExtractorAdapter(List.of(ex1, ex2));
        AttachmentInfo info = new AttachmentInfo("a.txt", "txt", "text/plain", "url");
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);

        when(ex1.supports(info)).thenReturn(true);
        when(ex1.extract(info, bytes)).thenReturn(Optional.empty());
        when(ex2.supports(info)).thenReturn(true);
        when(ex2.extract(info, bytes)).thenReturn(Optional.of("OK"));

        Optional<String> out = service.extractText(info, bytes);
        assertThat(out).contains("OK");

        verify(ex1).supports(info);
        verify(ex1).extract(info, bytes);
        verify(ex2).supports(info);
        verify(ex2).extract(info, bytes);
    }

    @Test
    void should_return_first_present_and_stop_afterwards() {
        CompositeAttachmentTextExtractorAdapter service = new CompositeAttachmentTextExtractorAdapter(List.of(ex1, ex2, ex3));
        AttachmentInfo info = new AttachmentInfo("a.txt", "txt", "text/plain", "url");
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);

        when(ex1.supports(info)).thenReturn(true);
        when(ex1.extract(info, bytes)).thenReturn(Optional.of("FIRST"));

        Optional<String> out = service.extractText(info, bytes);
        assertThat(out).contains("FIRST");

        verify(ex1).supports(info);
        verify(ex1).extract(info, bytes);
        // Ensure later extractors were never invoked
        verifyNoInteractions(ex2, ex3);
    }

    @Test
    void should_swallow_exception_from_extractor_and_try_next() {
        CompositeAttachmentTextExtractorAdapter service = new CompositeAttachmentTextExtractorAdapter(List.of(ex1, ex2));
        AttachmentInfo info = new AttachmentInfo("a.txt", "txt", "text/plain", "url");
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);

        when(ex1.supports(info)).thenReturn(true);
        when(ex1.extract(info, bytes)).thenThrow(new RuntimeException("boom"));
        when(ex2.supports(info)).thenReturn(true);
        when(ex2.extract(info, bytes)).thenReturn(Optional.of("RECOVERED"));

        Optional<String> out = service.extractText(info, bytes);
        assertThat(out).contains("RECOVERED");

        verify(ex1).supports(info);
        verify(ex1).extract(info, bytes);
        verify(ex2).supports(info);
        verify(ex2).extract(info, bytes);
    }
}
