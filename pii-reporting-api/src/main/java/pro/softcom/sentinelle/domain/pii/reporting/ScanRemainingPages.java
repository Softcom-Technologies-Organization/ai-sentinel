package pro.softcom.sentinelle.domain.pii.reporting;

import java.util.List;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;

public record ScanRemainingPages(int originalTotal,
                                 int analyzedOffset,
                                 List<ConfluencePage> remaining) { }
