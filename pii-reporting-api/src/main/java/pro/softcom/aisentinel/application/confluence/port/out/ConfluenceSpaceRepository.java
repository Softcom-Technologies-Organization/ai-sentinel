package pro.softcom.aisentinel.application.confluence.port.out;

import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;

import java.util.List;
import java.util.Optional;

/**
 * Output port for persisting and querying the local cache of Confluence spaces.
 *
 * <p>Implementations typically back a relational store used to avoid hitting the Confluence API
 * for stable metadata (space keys, names, URLs).
 */
public interface ConfluenceSpaceRepository {

    /** Returns all cached spaces in no guaranteed order. */
    List<ConfluenceSpace> findAll();

    /** Fetches a cached space by its unique Confluence key, empty when absent. */
    Optional<ConfluenceSpace> findByKey(String key);

    /** Persists the given spaces, replacing any existing entries with the same key. */
    void saveAll(List<ConfluenceSpace> spaces);
}
