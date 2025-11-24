package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.pagination;

import java.util.List;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.dto.ConfluenceSpaceDto;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.dto.ConfluenceSpacesResponseDto;

/**
 * Gère la logique de pagination pour les réponses de l'API Confluence.
 */
public class ConfluencePaginationHandler {

    public boolean shouldFetchNextSpaceBatch(
        ConfluenceSpacesResponseDto response,
        List<ConfluenceSpaceDto> currentBatch,
        int pageSize) {

        if (!hasNextLink(response)) {
            return false;
        }

        var effectiveLimit = calculateEffectivePageSize(response, currentBatch, pageSize);
        return !currentBatch.isEmpty() && currentBatch.size() >= effectiveLimit;
    }

    private boolean hasNextLink(ConfluenceSpacesResponseDto response) {
        return response.links() != null
            && response.links().next() != null
            && !response.links().next().isBlank();
    }

    public int calculateEffectivePageSize(
        ConfluenceSpacesResponseDto response,
        List<ConfluenceSpaceDto> currentBatch,
        int requestedPageSize) {

        if (response.limit() > 0) {
            return response.limit();
        }

        int batchSize = currentBatch.size();
        if (batchSize == 0) {
            return 1;
        }

        return Math.clamp(batchSize, 1, requestedPageSize);
    }
}
