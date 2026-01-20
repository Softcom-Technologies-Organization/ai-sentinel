package pro.softcom.aisentinel.application.confluence.port.out;

import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;

import java.util.List;
import java.util.Optional;

public interface ConfluenceSpaceRepository {

    List<ConfluenceSpace> findAll();

    Optional<ConfluenceSpace> findByKey(String key);

    void saveAll(List<ConfluenceSpace> spaces);
}
