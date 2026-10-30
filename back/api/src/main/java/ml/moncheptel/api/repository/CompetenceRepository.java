package ml.moncheptel.api.repository;

import java.util.List;
import ml.moncheptel.api.modele.Competence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompetenceRepository extends JpaRepository<Competence, Long> {

    List<Competence> findByVeterinaireIdOrderByMaladieNomAsc(Long veterinaireId);

    /** Garde-fou : une competence declaree deux fois ferait doublon a l'orientation. */
    boolean existsByVeterinaireIdAndMaladieId(Long veterinaireId, Long maladieId);

    void deleteByVeterinaireIdAndMaladieId(Long veterinaireId, Long maladieId);
}
