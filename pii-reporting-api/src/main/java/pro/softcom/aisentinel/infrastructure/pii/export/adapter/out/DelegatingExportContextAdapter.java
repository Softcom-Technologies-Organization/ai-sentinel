package pro.softcom.aisentinel.infrastructure.pii.export.adapter.out;

import lombok.RequiredArgsConstructor;
import pro.softcom.aisentinel.application.pii.export.exception.UnsupportedSourceTypeException;
import pro.softcom.aisentinel.application.pii.export.port.out.ReadExportContextPort;
import pro.softcom.aisentinel.domain.pii.export.ExportContext;
import pro.softcom.aisentinel.domain.pii.export.SourceType;

import java.util.Map;

/**
 * Routes export context requests to the appropriate source-specific adapter.
 * Acts as a dispatcher based on the SourceType, delegating to the matching implementation.
 */
@RequiredArgsConstructor
public class DelegatingExportContextAdapter implements ReadExportContextPort {

    private final Map<SourceType, ReadExportContextPort> delegates;

    @Override
    public ExportContext findContext(SourceType sourceType, String sourceIdentifier) {
        ReadExportContextPort delegate = delegates.get(sourceType);
        if (delegate == null) {
            throw new UnsupportedSourceTypeException(sourceType.getValue());
        }
        return delegate.findContext(sourceType, sourceIdentifier);
    }
}
