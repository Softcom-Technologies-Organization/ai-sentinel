package pro.softcom.aisentinel.application.pii.reporting;

import pro.softcom.aisentinel.domain.pii.reporting.DashboardFacets;
import pro.softcom.aisentinel.domain.pii.reporting.FacetCount;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;
import pro.softcom.aisentinel.domain.pii.reporting.SpaceSummary;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Framework-free helper that applies the dashboard filter, search and sort over the full
 * list of spaces and computes contextual facet counts.
 *
 * <p>Semantics:
 * <ul>
 *   <li>OR within an axis, AND across axes; an empty axis matches all.</li>
 *   <li>Search is applied after filters on space name (fallback key), case-insensitive contains.</li>
 *   <li>Facets are contextual: each axis is computed over spaces matching the OTHER axes plus the
 *       search, i.e. excluding the axis's own selection.</li>
 * </ul>
 */
public final class DashboardSpaceFilter {

    private static final String HIGH = "HIGH";
    private static final String MEDIUM = "MEDIUM";
    private static final String LOW = "LOW";
    private static final String SORT_BY_NAME = "name";

    private DashboardSpaceFilter() {
    }

    /**
     * Result of applying the dashboard criteria to the full space list.
     *
     * @param spaces filtered + searched + sorted spaces
     * @param facets contextual facet counts
     */
    public record Result(List<SpaceSummary> spaces, DashboardFacets facets) {
    }

    /**
     * Filters, searches and sorts the spaces and computes the contextual facets.
     *
     * @param allSpaces the full list of spaces (all Confluence spaces, left-joined with scan data)
     * @param criteria  the filter criteria (never null)
     * @return the filtered/sorted list and the facets
     */
    public static Result apply(List<SpaceSummary> allSpaces, DashboardFilterCriteria criteria) {
        List<SpaceSummary> searched = allSpaces.stream()
            .filter(matchesAllAxes(criteria))
            .filter(matchesSearch(criteria.search()))
            .toList();

        List<SpaceSummary> sorted = sort(searched, criteria.sort(), criteria.order());
        DashboardFacets facets = computeFacets(allSpaces, criteria);
        return new Result(sorted, facets);
    }

    // ---------------------------------------------------------------------
    // Filtering
    // ---------------------------------------------------------------------

    private static Predicate<SpaceSummary> matchesAllAxes(DashboardFilterCriteria criteria) {
        return space -> matchesPiiTypes(space, criteria.piiTypes())
            && matchesSeverities(space, criteria.severities())
            && matchesStatuses(space, criteria.statuses());
    }

    private static boolean matchesPiiTypes(SpaceSummary space, List<String> selected) {
        if (selected.isEmpty()) {
            return true;
        }
        return selected.stream().anyMatch(type -> typeCount(space, type) > 0);
    }

    private static boolean matchesSeverities(SpaceSummary space, List<String> selected) {
        if (selected.isEmpty()) {
            return true;
        }
        return selected.stream().anyMatch(severity -> severityBucket(space, severity) > 0);
    }

    private static boolean matchesStatuses(SpaceSummary space, List<String> selected) {
        if (selected.isEmpty()) {
            return true;
        }
        return selected.contains(UiStatusMapper.toUiStatus(space.status()));
    }

    private static Predicate<SpaceSummary> matchesSearch(String search) {
        if (search == null || search.isBlank()) {
            return space -> true;
        }
        String needle = search.toLowerCase();
        return space -> searchableText(space).contains(needle);
    }

    private static String searchableText(SpaceSummary space) {
        String label = space.spaceName() != null ? space.spaceName() : space.spaceKey();
        return label == null ? "" : label.toLowerCase();
    }

    // ---------------------------------------------------------------------
    // Sorting
    // ---------------------------------------------------------------------

    private static List<SpaceSummary> sort(List<SpaceSummary> spaces, String sort, String order) {
        if (isPiiTypeSort(sort)) {
            return sortByPiiType(spaces, piiTypeCode(sort), order);
        }
        Comparator<SpaceSummary> comparator = comparatorFor(sort);
        return spaces.stream()
            .sorted(directional(comparator, sort, order))
            .toList();
    }

    private static Comparator<SpaceSummary> comparatorFor(String sort) {
        return switch (effectiveSort(sort)) {
            case SORT_BY_NAME -> Comparator.comparing(DashboardSpaceFilter::searchableText);
            case "lastScan" -> Comparator.comparing(SpaceSummary::lastEventAt,
                Comparator.nullsFirst(Comparator.naturalOrder()));
            case "severityScore" -> Comparator.comparingLong(DashboardSpaceFilter::severityScore);
            default -> Comparator.comparingInt(space -> space.severityCounts().total());
        };
    }

    private static Comparator<SpaceSummary> directional(Comparator<SpaceSummary> ascending,
                                                        String sort, String order) {
        return descendingRequested(sort, order) ? ascending.reversed() : ascending;
    }

    private static boolean descendingRequested(String sort, String order) {
        if (order != null && !order.isBlank()) {
            return "desc".equalsIgnoreCase(order);
        }
        return !SORT_BY_NAME.equals(effectiveSort(sort));
    }

    /** Sort criterion applied when the caller does not ask for one. */
    private static String effectiveSort(String sort) {
        return sort == null || sort.isBlank() ? SORT_BY_NAME : sort;
    }

    private static List<SpaceSummary> sortByPiiType(List<SpaceSummary> spaces, String code, String order) {
        Comparator<SpaceSummary> ascending = Comparator.comparingInt(space -> typeCount(space, code));
        Comparator<SpaceSummary> directed = "asc".equalsIgnoreCase(order) ? ascending : ascending.reversed();
        return spaces.stream()
            .filter(space -> typeCount(space, code) > 0)
            .sorted(directed)
            .toList();
    }

    private static boolean isPiiTypeSort(String sort) {
        return sort != null && sort.startsWith("piiType:");
    }

    private static String piiTypeCode(String sort) {
        return sort.substring("piiType:".length());
    }

    private static long severityScore(SpaceSummary space) {
        SeverityCounts counts = space.severityCounts();
        return counts.high() * 1_000_000L + counts.medium() * 1_000L + counts.low();
    }

    // ---------------------------------------------------------------------
    // Facets
    // ---------------------------------------------------------------------

    private static DashboardFacets computeFacets(List<SpaceSummary> allSpaces, DashboardFilterCriteria criteria) {
        Map<String, FacetCount> piiTypes = piiTypeFacets(contextExcluding(allSpaces, criteria, "piiTypes"));
        Map<String, FacetCount> severities = severityFacets(contextExcluding(allSpaces, criteria, "severities"));
        Map<String, FacetCount> statuses = statusFacets(contextExcluding(allSpaces, criteria, "statuses"));
        return new DashboardFacets(piiTypes, severities, statuses);
    }

    /**
     * Spaces matching all axes except the named one, plus the search.
     */
    private static List<SpaceSummary> contextExcluding(List<SpaceSummary> allSpaces,
                                                       DashboardFilterCriteria criteria, String axisToDrop) {
        List<String> piiTypes = "piiTypes".equals(axisToDrop) ? List.of() : criteria.piiTypes();
        List<String> severities = "severities".equals(axisToDrop) ? List.of() : criteria.severities();
        List<String> statuses = "statuses".equals(axisToDrop) ? List.of() : criteria.statuses();
        return allSpaces.stream()
            .filter(space -> matchesPiiTypes(space, piiTypes))
            .filter(space -> matchesSeverities(space, severities))
            .filter(space -> matchesStatuses(space, statuses))
            .filter(matchesSearch(criteria.search()))
            .toList();
    }

    private static Map<String, FacetCount> piiTypeFacets(List<SpaceSummary> context) {
        Map<String, FacetCount> facets = new LinkedHashMap<>();
        for (SpaceSummary space : context) {
            for (Map.Entry<String, Integer> entry : space.piiTypeCounts().entrySet()) {
                accumulate(facets, entry.getKey(), entry.getValue() == null ? 0 : entry.getValue());
            }
        }
        return facets;
    }

    private static Map<String, FacetCount> severityFacets(List<SpaceSummary> context) {
        Map<String, FacetCount> facets = new LinkedHashMap<>();
        addSeverityFacet(facets, context, HIGH, SeverityCounts::high);
        addSeverityFacet(facets, context, MEDIUM, SeverityCounts::medium);
        addSeverityFacet(facets, context, LOW, SeverityCounts::low);
        return facets;
    }

    private static void addSeverityFacet(Map<String, FacetCount> facets, List<SpaceSummary> context,
                                         String bucket, ToIntFunction<SeverityCounts> extractor) {
        int spaceCount = 0;
        int total = 0;
        for (SpaceSummary space : context) {
            int value = extractor.applyAsInt(space.severityCounts());
            if (value > 0) {
                spaceCount++;
                total += value;
            }
        }
        if (spaceCount > 0) {
            facets.put(bucket, new FacetCount(spaceCount, total));
        }
    }

    private static Map<String, FacetCount> statusFacets(List<SpaceSummary> context) {
        Map<String, FacetCount> facets = new LinkedHashMap<>();
        for (SpaceSummary space : context) {
            String uiStatus = UiStatusMapper.toUiStatus(space.status());
            accumulate(facets, uiStatus, space.severityCounts().total());
        }
        return facets;
    }

    private static void accumulate(Map<String, FacetCount> facets, String key, int occurrences) {
        FacetCount current = facets.getOrDefault(key, new FacetCount(0, 0));
        facets.put(key, new FacetCount(current.spaceCount() + 1, current.totalOccurrences() + occurrences));
    }

    // ---------------------------------------------------------------------
    // Shared accessors
    // ---------------------------------------------------------------------

    private static int typeCount(SpaceSummary space, String type) {
        Integer count = space.piiTypeCounts().get(type);
        return count == null ? 0 : count;
    }

    private static int severityBucket(SpaceSummary space, String severity) {
        SeverityCounts counts = space.severityCounts();
        return switch (severity) {
            case HIGH -> counts.high();
            case MEDIUM -> counts.medium();
            case LOW -> counts.low();
            default -> 0;
        };
    }
}
