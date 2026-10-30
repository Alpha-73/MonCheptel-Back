package ml.moncheptel.api.repository;

import java.time.LocalDate;
import java.util.List;
import ml.moncheptel.api.modele.EvenementSanitaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EvenementSanitaireRepository extends JpaRepository<EvenementSanitaire, Long> {

    /** Carnet sanitaire d'une unite. */
    List<EvenementSanitaire> findByUniteIdOrderByDateEvenementDescIdDesc(Long uniteId);

    /** Actes poses par un veterinaire — son historique d'interventions. */
    List<EvenementSanitaire> findByVeterinaireIdOrderByDateEvenementDesc(Long veterinaireId);

    /**
     * Vaccinations d'une unite sur une fenetre de dates.
     *
     * <p>Sert a solder automatiquement un rappel : si la vaccination Newcastle
     * a ete enregistree autour de l'echeance, le rappel disparait du tableau
     * de bord sans que l'eleveur ait a le cocher.
     */
    @Query("""
           select e from EvenementSanitaire e
           where e.unite.id = :uniteId
             and e.type = :type
             and e.dateEvenement between :debut and :fin
           """)
    List<EvenementSanitaire> chercherAutourDe(
            @Param("uniteId") Long uniteId,
            @Param("type") ml.moncheptel.api.modele.enumeration.TypeEvenement type,
            @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
}
