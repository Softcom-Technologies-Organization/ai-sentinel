package pro.softcom.aisentinel.domain.pii.reporting;

import java.util.List;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;

public record ScanRemainingPages(int originalTotal,
                                 int analyzedOffset,
                                 List<ConfluencePage> remaining) { }
