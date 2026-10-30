package ml.moncheptel.api.repository;

import java.util.List;
import java.util.Optional;
import ml.moncheptel.api.modele.Maladie;
import ml.moncheptel.api.modele.enumeration.Filiere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaladieRepository extends JpaRepository<Maladie, Long> {

    Optional<Maladie> findByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCase(String nom);

    /** Catalogue de l'administration — ecran « Admin - Maladies ». */
    List<Maladie> findByActiveTrueOrderByNomAsc();

    /**
     * Maladies d'une filiere.
     *
     * <p>{@code filieres} est une {@code @ElementCollection} d'enumerations :
     * le {@code member of} se traduit par une jointure sur la table de
     * collection. Sans ce filtre, un signalement de volaille remontait une
     * maladie bovine — c'est le defaut qui a rendu la premiere version du
     * moteur inutilisable.
     */
    @Query("""
           select m from Maladie m
           where m.active = true
             and :filiere member of m.filieres
           order by m.nom asc
           """)
    List<Maladie> chercherParFiliere(@Param("filiere") Filiere filiere);

    /** Charge les poids en meme temps : evite le N+1 sur la fiche maladie. */
    @Query("""
           select distinct m from Maladie m
           left join fetch m.symptomes ms
           left join fetch ms.symptome
           where m.id = :id
           """)
    Optional<Maladie> chercherAvecSymptomes(@Param("id") Long id);
}
