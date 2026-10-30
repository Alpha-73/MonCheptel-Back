package ml.moncheptel.api.repository;

import java.util.List;
import java.util.Optional;
import ml.moncheptel.api.modele.TypeElevage;
import ml.moncheptel.api.modele.enumeration.Filiere;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TypeElevageRepository extends JpaRepository<TypeElevage, Long> {

    List<TypeElevage> findByActifTrueOrderByFiliereAscLibelleAsc();

    List<TypeElevage> findByFiliereAndActifTrue(Filiere filiere);

    /**
     * Charge le type AVEC ses postes de cout, en une requete.
     *
     * <p>Le simulateur les parcourt tous : sans ce chargement groupe, chaque
     * poste declencherait sa propre requete — huit allers-retours pour chiffrer
     * un cycle, multiplies par quatre filieres a l'ecran « Investir ».
     */
    @EntityGraph(attributePaths = "postesCout")
    @Query("select t from TypeElevage t where t.id = :id")
    Optional<TypeElevage> chercherAvecCouts(@Param("id") Long id);

    @EntityGraph(attributePaths = "postesCout")
    @Query("select distinct t from TypeElevage t where t.actif = true "
         + "order by t.filiere asc, t.libelle asc")
    List<TypeElevage> chercherTousAvecCouts();

    @EntityGraph(attributePaths = "postesCout")
    @Query("select distinct t from TypeElevage t "
         + "where t.actif = true and t.filiere = :filiere order by t.libelle asc")
    List<TypeElevage> chercherParFiliereAvecCouts(@Param("filiere") Filiere filiere);

    /** Administration : les types retires aussi, pour pouvoir les remettre en service. */
    @EntityGraph(attributePaths = "postesCout")
    @Query("select distinct t from TypeElevage t "
         + "where (:filiere is null or t.filiere = :filiere) "
         + "order by t.actif desc, t.filiere asc, t.libelle asc")
    List<TypeElevage> chercherPourAdministration(@Param("filiere") Filiere filiere);

    /** Un libelle ne sert qu'une fois par filiere, sans tenir compte de la casse. */
    @Query("select case when count(t) > 0 then true else false end from TypeElevage t where t.filiere = :filiere "
         + "and lower(t.libelle) = lower(:libelle) and (:exclu is null or t.id <> :exclu)")
    boolean libelleDejaPris(@Param("filiere") Filiere filiere, @Param("libelle") String libelle,
                            @Param("exclu") Long exclu);
}
