package ml.moncheptel.api.mapper;

import ml.moncheptel.api.dto.reponse.ZoneReponse;
import ml.moncheptel.api.modele.Zone;
import org.springframework.stereotype.Component;

@Component
public class ZoneMapper {

    public ZoneReponse versReponse(Zone zone) {
        if (zone == null) {
            return null;
        }
        return new ZoneReponse(zone.getId(), zone.getNom(), zone.getRegion());
    }
}
