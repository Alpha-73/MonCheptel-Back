package ml.moncheptel.api.repository;

import java.util.Collection;
import java.util.List;
import ml.moncheptel.api.modele.MaladieSymptome;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.repository.projection.ScoreMaladieProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Table d'association ponderee maladie/symptome : le referentiel du moteur de
 * diagnostic.
 */
@Repository
public interface MaladieSymptomeRepository extends JpaRepository<MaladieSymptome, Long> {

    List<MaladieSymptome> findByMaladieId(Long maladieId);

    /**
     * Le coeur du diagnostic, en une requete.
     *
     * <p>Rend, pour chaque maladie active de la filiere expliquant au moins un
     * symptome coche : la somme des poids correspondants et le nombre de
     * correspondances. Les maladies qui n'expliquent rien ne sortent pas —
     * c'est une jointure, pas un parcours du catalogue.
     *
     * <p>Le classement se fait ensuite dans le service. La base sait
     * additionner, elle n'a pas a connaitre la formule : si un veterinaire
     * revise la ponderation, c'est une methode Java qu'on relit, pas du JPQL.
     */
    @Query("""
           select ms.maladie.id as maladieId,
                  sum(ms.poids) as poidsCumule,
                  count(ms.id) as correspondances
           from MaladieSymptome ms
           where ms.symptome.id in :symptomeIds
             and ms.maladie.active = true
             and :filiere member of ms.maladie.filieres
           group by ms.maladie.id
           order by sum(ms.poids) desc
           """)
    List<ScoreMaladieProjection> noterCandidates(
            @Param("symptomeIds") Collection<Long> symptomeIds,
            @Param("filiere") Filiere filiere);

    boolean existsByMaladieIdAndSymptomeId(Long maladieId, Long symptomeId);

    void deleteByMaladieId(Long maladieId);
}
