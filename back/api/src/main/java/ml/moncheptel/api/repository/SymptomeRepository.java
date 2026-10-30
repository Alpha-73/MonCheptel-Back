package ml.moncheptel.api.repository;

import java.util.Collection;
import java.util.List;
import ml.moncheptel.api.modele.Symptome;
import ml.moncheptel.api.modele.enumeration.Filiere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SymptomeRepository extends JpaRepository<Symptome, Long> {

    /**
     * Symptomes proposes a l'eleveur — ecran « Signalement - Symptomes ».
     *
     * <p>{@code ordre} n'est pas decoratif : la maquette presente d'abord les
     * symptomes les plus frequents. Trier par libelle mettrait « Torticolis »
     * avant « Diarrhee » et ferait defiler l'eleveur pour rien.
     */
    @Query("""
           select distinct s from Symptome s
           join MaladieSymptome ms on ms.symptome = s
           where ms.maladie.active = true
             and :filiere member of ms.maladie.filieres
           order by s.ordre asc, s.libelle asc
           """)
    List<Symptome> chercherParFiliere(@Param("filiere") Filiere filiere);

    List<Symptome> findAllByOrderByOrdreAscLibelleAsc();

    java.util.Optional<Symptome> findByLibelleIgnoreCase(String libelle);

    /** Rang du prochain symptome ajoute : il se range apres les existants. */
    @Query("select coalesce(max(s.ordre), 0) from Symptome s")
    int ordreMaximum();

    /** Verifie que les identifiants recus d'un client existent vraiment. */
    long countByIdIn(Collection<Long> ids);
}
