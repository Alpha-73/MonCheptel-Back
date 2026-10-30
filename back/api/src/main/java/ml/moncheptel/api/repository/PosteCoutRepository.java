package ml.moncheptel.api.repository;

import java.util.List;
import ml.moncheptel.api.modele.PosteCout;
import ml.moncheptel.api.modele.enumeration.PhaseCout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PosteCoutRepository extends JpaRepository<PosteCout, Long> {

    List<PosteCout> findByTypeElevageIdOrderByPhaseAscOrdreAsc(Long typeElevageId);

    List<PosteCout> findByTypeElevageIdAndPhase(Long typeElevageId, PhaseCout phase);
}
