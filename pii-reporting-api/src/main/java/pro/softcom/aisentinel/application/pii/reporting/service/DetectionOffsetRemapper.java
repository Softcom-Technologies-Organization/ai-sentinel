package pro.softcom.aisentinel.application.pii.reporting.service;

import pro.softcom.aisentinel.domain.confluence.extraction.ExtractedContent;
import pro.softcom.aisentinel.domain.confluence.extraction.ExtractedContent.Segment;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DiscardedSensitiveData;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.SensitiveData;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class DetectionOffsetRemapper {

    private DetectionOffsetRemapper() {
    }

    public static ContentPiiDetection remap(ContentPiiDetection detection, ExtractedContent content) {
        if (detection == null || (content.segments().size() <= 1
                && content.analysisText().equals(content.contextText()))) {
            return detection;
        }
        List<SensitiveData> found = detection.sensitiveDataFound() == null ? List.of()
                : detection.sensitiveDataFound().stream()
                    .flatMap(data -> remap(data, content).stream()).toList();
        List<DiscardedSensitiveData> discarded = detection.discardedByPostfilter().stream()
                .flatMap(item -> remap(item.data(), content)
                        .map(data -> new DiscardedSensitiveData(data, item.judgeVerdict(),
                                item.judgeConfidence(), item.judgeReason())).stream())
                .toList();
        return new ContentPiiDetection(detection.pageId(), detection.pageTitle(), detection.spaceKey(),
                detection.analysisDate(), found,
                found.stream().collect(Collectors.toMap(SensitiveData::type, _ -> 1, Integer::sum)),
                discarded, detection.detectorRunStats());
    }

    private static Optional<SensitiveData> remap(SensitiveData data, ExtractedContent content) {
        int start = data.startingPosition();
        int end = data.endingPosition();
        if (start < 0 || end <= start || end > content.analysisText().length()) {
            return Optional.empty();
        }
        return containingSegment(content.segments(), start)
                .filter(segment -> end <= segment.analysisStart() + segment.length())
                .map(segment -> withStart(data, segment.contextStart() + start - segment.analysisStart()));
    }

    private static SensitiveData withStart(SensitiveData data, int start) {
        int end = start + data.endingPosition() - data.startingPosition();
        return new SensitiveData(data.type(), data.typeLabel(), data.value(), data.context(), start, end,
                data.confidenceScore(), data.selector(), data.source());
    }

    private static Optional<Segment> containingSegment(List<Segment> segments, int start) {
        int low = 0;
        int high = segments.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            Segment segment = segments.get(middle);
            if (start < segment.analysisStart()) {
                high = middle - 1;
            } else if (start >= segment.analysisStart() + segment.length()) {
                low = middle + 1;
            } else {
                return Optional.of(segment);
            }
        }
        return Optional.empty();
    }
}
