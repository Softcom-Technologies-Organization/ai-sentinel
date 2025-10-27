package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.http;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config.ConfluenceConnectionConfig;

/**
 * Constructeur d'URLs pour l'API REST Confluence.
 * Centralise la logique de construction des endpoints API.
 */
public class ConfluenceApiUrlBuilder {

    private static final String EXPAND_PARAM = "?expand=";
    
    private final ConfluenceConnectionConfig config;

    public ConfluenceApiUrlBuilder(ConfluenceConnectionConfig config) {
        this.config = config;
    }

    /**
     * Construit l'URI pour récupérer une page spécifique.
     */
    public URI buildPageUri(String pageId) {
        return URI.create(
            config.getRestApiUrl() + config.contentPath() + pageId + EXPAND_PARAM
                + config.defaultPageExpands());
    }

    /**
     * Construit l'URI pour récupérer les pages d'un espace avec pagination.
     */
    public URI buildSpacePagesUri(String spaceKey, int startIndex, int pageSize) {
        return URI.create(
            String.format("%s%s/%s/content?expand=version,body.storage&limit=%d&start=%d",
                config.getRestApiUrl(), config.spacePath(), spaceKey, pageSize, startIndex));
    }

    /**
     * Construit l'URI pour rechercher des pages avec une requête CQL.
     */
    public URI buildSearchUri(String cql) {
        var encodedCql = URLEncoder.encode(cql, StandardCharsets.UTF_8);
        return URI.create(
            config.getRestApiUrl() + config.searchContentPath() + "?cql=" + encodedCql
                + "&expand=body.storage,version");
    }

    /**
     * Construit l'URI pour récupérer un espace par clé ou ID.
     */
    public URI buildSpaceUri(String spaceKeyOrId) {
        return URI.create(
            config.getRestApiUrl() + config.spacePath() + "/" + spaceKeyOrId + EXPAND_PARAM
                + config.defaultSpaceExpands());
    }

    /**
     * Construit l'URI pour mettre à jour une page.
     */
    public URI buildUpdatePageUri(String pageId) {
        return URI.create(config.getRestApiUrl() + config.contentPath() + pageId);
    }

    /**
     * Construit l'URI pour récupérer tous les espaces avec pagination.
     */
    public URI buildAllSpacesUri(int startIndex, int pageSize) {
        return URI.create(
            config.getRestApiUrl() + config.spacePath() + EXPAND_PARAM
                + config.defaultSpaceExpands() + "&limit=" + pageSize + "&start=" + startIndex);
    }

    /**
     * Construit l'URI pour tester la connexion à l'API.
     */
    public URI buildConnectionTestUri() {
        return URI.create(config.getRestApiUrl() + config.spacePath());
    }

    /**
     * Construit l'URI pour rechercher des pages modifiées depuis une date donnée via CQL Content Search.
     * Utilise l'API /rest/api/content/search avec une requête CQL pour trouver les pages
     * d'un space modifiées après la date spécifiée.
     * 
     * @param spaceKey clé du space
     * @param sinceDate date à partir de laquelle rechercher (format ISO 8601)
     * @return URI de la requête CQL
     */
    public URI buildContentSearchModifiedSinceUri(String spaceKey, String sinceDate) {
        var cql = String.format("lastModified>=\"%s\" AND space=\"%s\"", sinceDate, spaceKey);
        var encodedCql = URLEncoder.encode(cql, StandardCharsets.UTF_8);
        return URI.create(
            config.getRestApiUrl() + config.searchContentPath() + "?cql=" + encodedCql
                + "&expand=version,history.lastUpdated");
    }
}
