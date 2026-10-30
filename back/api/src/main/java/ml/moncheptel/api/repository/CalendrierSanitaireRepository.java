package ml.moncheptel.api.repository;

import java.util.List;
import ml.moncheptel.api.modele.CalendrierSanitaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CalendrierSanitaireRepository extends JpaRepository<CalendrierSanitaire, Long> {

    /** Protocole d'un type d'elevage, dans l'ordre du cycle. */
    List<CalendrierSanitaire> findByTypeElevageIdOrderByJourDuCycleAsc(Long typeElevageId);
}
