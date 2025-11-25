package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper;

import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.SeverityCountsDto;

/**
 * Maps domain SeverityCounts to REST API SeverityCountsDto.
 * 
 * <p>Business purpose: Transforms internal domain severity statistics into a format
 * suitable for REST API responses, handling null-safety and providing sensible defaults.
 * 
 * <p>Design: Stateless mapper component that can be injected into other mappers requiring
 * severity count transformations.
 */
@Component
public class SeverityCountsMapper {
    
    /**
     * Converts domain SeverityCounts to DTO.
     * 
     * <p>Business rule: Returns zero counts when domain object is null, representing
     * a space with no detected PIIs or unavailable severity data.
     * 
     * @param counts The domain SeverityCounts to convert (may be null)
     * @return SeverityCountsDto with mapped values, or zero counts if input is null
     */
    public SeverityCountsDto toDto(SeverityCounts counts) {
        if (counts == null) {
            return SeverityCountsDto.zero();
        }
        return new SeverityCountsDto(
            counts.high(),
            counts.medium(),
            counts.low(),
            counts.total()
        );
    }
}
