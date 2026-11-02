package pro.softcom.aisentinel.application.pii.detection.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.softcom.aisentinel.application.pii.detection.port.in.ListLmStudioModelsPort;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;
import pro.softcom.aisentinel.domain.pii.judge.JudgeEndpoint;
import pro.softcom.aisentinel.domain.pii.scan.LmStudioModelListing;

/**
 * Use case listing the Ministral-PII models LM Studio has on disk.
 * The listing itself runs in the detector service, which owns the network path
 * to LM Studio; this use case only resolves which endpoint to ask about.
 */
public class ListLmStudioModelsUseCase implements ListLmStudioModelsPort {

    private static final Logger log = LoggerFactory.getLogger(ListLmStudioModelsUseCase.class);

    private final PiiDetectorClient piiDetectorClient;
    private final PiiDetectionConfigRepository configRepository;
    private final JudgeEndpoint judgeEndpoint;

    public ListLmStudioModelsUseCase(PiiDetectorClient piiDetectorClient,
                                     PiiDetectionConfigRepository configRepository,
                                     JudgeEndpoint judgeEndpoint) {
        this.piiDetectorClient = piiDetectorClient;
        this.configRepository = configRepository;
        this.judgeEndpoint = judgeEndpoint;
    }

    @Override
    public LmStudioModelListing listModels(String lmStudioHost, Integer lmStudioPort, boolean allModels) {
        JudgeEndpoint endpoint = allModels
                ? judgeEndpoint.resolve(lmStudioHost, lmStudioPort)
                : new JudgeEndpoint(lmStudioHost, lmStudioPort);
        String host = endpoint.host();
        Integer port = endpoint.port();
        if (host == null || host.isBlank() || port == null) {
            PiiDetectionConfig config = configRepository.findConfig();
            host = host == null || host.isBlank() ? config.lmStudioHost() : host;
            port = port == null ? config.lmStudioPort() : port;
        }
        log.debug("Listing LM Studio models at {}:{} (allModels={})", host, port, allModels);
        return piiDetectorClient.listLmStudioModels(host, port, allModels);
    }
}
