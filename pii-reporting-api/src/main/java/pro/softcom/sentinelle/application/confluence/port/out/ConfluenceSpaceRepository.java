package pro.softcom.sentinelle.application.confluence.port.out;

import java.util.List;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;

public interface ConfluenceSpaceRepository {

    List<ConfluenceSpace> findAll();

    void saveAll(List<ConfluenceSpace> spaces);
}
