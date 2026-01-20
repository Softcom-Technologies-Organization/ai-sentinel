package pro.softcom.aisentinel.domain.pii.reporting;

import pro.softcom.aisentinel.domain.confluence.ConfluencePage;

import java.util.List;

public record ScanRemainingPages(int originalTotal,
                                 int analyzedOffset,
                                 List<ConfluencePage> remaining) { }
