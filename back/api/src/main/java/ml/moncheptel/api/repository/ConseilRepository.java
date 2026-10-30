package ml.moncheptel.api.repository;

import java.util.List;
import java.util.Optional;
import ml.moncheptel.api.modele.Conseil;
import ml.moncheptel.api.modele.enumeration.CategorieConseil;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutConseil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConseilRepository extends JpaRepository<Conseil, Long> {

    /**
     * Fil des conseils — ecran « Conseils ». Publies uniquement : un brouillon
     * ou un conseil retire ne doit jamais fuir cote eleveur, et ce filtre est
     * la seule chose qui l'en empeche.
     */
    @Query("""
           select c from Conseil c
           left join fetch c.auteur
           where c.statut = ml.moncheptel.api.modele.enumeration.StatutConseil.PUBLIE
             and (:filiere is null or c.filiere = :filiere)
             and (:categorie is null or c.categorie = :categorie)
           order by c.datePublication desc
           """)
    Page<Conseil> chercherPublies(@Param("filiere") Filiere filiere,
                                  @Param("categorie") CategorieConseil categorie,
                                  Pageable pageable);

    @Query("""
           select c from Conseil c
           left join fetch c.auteur
           where c.id = :id
             and c.statut = ml.moncheptel.api.modele.enumeration.StatutConseil.PUBLIE
           """)
    Optional<Conseil> chercherPublie(@Param("id") Long id);

    /** File de moderation — « 3 conseils en attente » du tableau admin. */
    @Query("""
           select c from Conseil c
           join fetch c.auteur
           where c.statut = ml.moncheptel.api.modele.enumeration.StatutConseil.EN_MODERATION
           order by c.dateSoumission asc
           """)
    List<Conseil> chercherEnModeration();

    long countByStatut(StatutConseil statut);

    /**
     * Ecran « Conseils » de l'administration : tout sauf les brouillons, qui
     * appartiennent encore a leur auteur. {@code left join} : un conseil publie
     * directement par l'administration n'a pas d'auteur veterinaire.
     */
    @Query("""
           select c from Conseil c
           left join fetch c.auteur
           where c.statut <> ml.moncheptel.api.modele.enumeration.StatutConseil.BROUILLON
             and (:statut is null or c.statut = :statut)
           order by c.dateCreation desc
           """)
    List<Conseil> chercherPourAdministration(@Param("statut") StatutConseil statut);

    long countByAuteurIdAndStatut(Long auteurId, StatutConseil statut);

    /** Les conseils d'un veterinaire, tous statuts : c'est son espace. */
    List<Conseil> findByAuteurIdOrderByDateCreationDesc(Long auteurId);

    /**
     * Incremente le compteur de lectures sans charger ni verrouiller le
     * conseil. Deux lecteurs simultanes comptent deux lectures ; avec un
     * {@code get} puis un {@code set}, l'un des deux serait perdu.
     */
    @Modifying
    @Query("update Conseil c set c.nombreLectures = c.nombreLectures + 1 where c.id = :id")
    void incrementerLectures(@Param("id") Long id);
}
