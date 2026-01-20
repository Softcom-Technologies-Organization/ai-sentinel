package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.mapper;

public final class ConfluenceUrlBuilder {
    // Global root URL initialized by Spring at startup via ConfluenceUrlInitializer
    private static volatile String globalRootUrl;

    private ConfluenceUrlBuilder() {
        // Intentionally empty to prevent instantiation
    }

    public static String spaceOverviewUrl(String spaceKey) {
        String base = globalRootUrl;
        if (isBlank(spaceKey) || isBlank(base)) return null;
        return base + "/spaces/" + urlEncode(spaceKey) + "/overview";
    }

    public static void setGlobalRootUrl(String rootUrl) {
        globalRootUrl = normalize(rootUrl);
    }

    private static String normalize(String url) {
        if (url == null || url.isBlank()) return null;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
