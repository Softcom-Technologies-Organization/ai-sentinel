package pro.softcom.sentinelle.domain.confluence;

import java.time.Instant;

public record ModifiedPageInfo(
    String pageId,
    String title,
    Instant lastModified
) {
}
