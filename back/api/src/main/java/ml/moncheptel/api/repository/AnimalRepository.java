package ml.moncheptel.api.repository;

import java.util.List;
import ml.moncheptel.api.modele.Animal;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnimalRepository extends JpaRepository<Animal, Long> {

    @Query("""
           select a from Animal a
           join fetch a.typeElevage t
           where a.proprietaire.id = :eleveurId
             and (:filiere is null or t.filiere = :filiere)
             and (:statut is null or a.statut = :statut)
           order by a.code
           """)
    List<Animal> chercherParFiliere(@Param("eleveurId") Long eleveurId,
                                    @Param("filiere") Filiere filiere,
                                    @Param("statut") StatutUnite statut);

    /** « Bovins restants : 3 ». */
    @Query("""
           select count(a) from Animal a
           where a.proprietaire.id = :eleveurId
             and a.typeElevage.filiere = :filiere
             and a.statut = ml.moncheptel.api.modele.enumeration.StatutUnite.ACTIF
           """)
    long compterActifs(@Param("eleveurId") Long eleveurId, @Param("filiere") Filiere filiere);

    /** « Sortis du cheptel : 1 » — vendus, morts ou transferes. */
    @Query("""
           select count(a) from Animal a
           where a.proprietaire.id = :eleveurId
             and a.typeElevage.filiere = :filiere
             and a.statut <> ml.moncheptel.api.modele.enumeration.StatutUnite.ACTIF
           """)
    long compterSortis(@Param("eleveurId") Long eleveurId, @Param("filiere") Filiere filiere);

    /** Tous les animaux actifs, toutes filieres — pour le compteur global. */
    @Query("""
           select count(a) from Animal a
           where a.proprietaire.id = :eleveurId
             and a.statut = ml.moncheptel.api.modele.enumeration.StatutUnite.ACTIF
           """)
    long compterTousActifs(@Param("eleveurId") Long eleveurId);
}
