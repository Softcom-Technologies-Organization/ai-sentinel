package pro.softcom.sentinelle.application.confluence.port.out;

import java.util.List;
import java.util.Optional;

import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;

public interface ConfluenceSpaceRepository {

    List<ConfluenceSpace> findAll();

    Optional<ConfluenceSpace> findByKey(String key);

    void saveAll(List<ConfluenceSpace> spaces);
}
