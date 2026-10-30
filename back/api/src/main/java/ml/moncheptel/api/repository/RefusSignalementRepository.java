package ml.moncheptel.api.repository;

import java.util.Collection;
import java.util.List;
import ml.moncheptel.api.modele.RefusSignalement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefusSignalementRepository extends JpaRepository<RefusSignalement, Long> {

    /** Historique d'un cas, du plus recent au plus ancien. */
    List<RefusSignalement> findBySignalementIdOrderByDateRefusDesc(Long signalementId);

    /** Historique de plusieurs cas en une requete (listes de la console). */
    List<RefusSignalement> findBySignalementIdInOrderByDateRefusDesc(Collection<Long> signalementIds);

    /** Les veterinaires qui ont refuse ce cas : ils ne lui sont plus proposes. */
    @Query("""
           select r.veterinaireId from RefusSignalement r
           where r.signalementId = :signalementId and r.veterinaireId is not null
           """)
    List<Long> veterinairesAyantRefuse(@Param("signalementId") Long signalementId);
}
