package ml.moncheptel.api.repository;

import java.util.List;
import ml.moncheptel.api.modele.Veterinaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VeterinaireRepository extends JpaRepository<Veterinaire, Long> {

    /** Annuaire de l'ecran « Veterinaires - Liste », zone de l'eleveur d'abord. */
    @Query("""
           select distinct v from Veterinaire v
           left join fetch v.zoneIntervention
           where v.actif = true and v.disponible = true
             and (:zoneId is null or v.zoneIntervention.id = :zoneId)
           order by v.nom
           """)
    List<Veterinaire> chercherAnnuaire(@Param("zoneId") Long zoneId);

    /**
     * Veterinaires competents pour cette maladie, dans cette zone.
     *
     * <p>Le coeur de l'orientation automatique. Le tri par disponibilite puis
     * par nom rend la liste stable ; c'est OrientationService qui choisit
     * ensuite, parmi les disponibles, le moins charge en cas ouverts — sans
     * quoi le premier par ordre alphabetique recevait tout (audit
     * veterinaire M11).
     */
    @Query("""
           select distinct v from Veterinaire v
           join v.competences c
           where v.actif = true
             and c.maladie.id = :maladieId
             and (:zoneId is null or v.zoneIntervention.id = :zoneId)
           order by v.disponible desc, v.nom asc
           """)
    List<Veterinaire> chercherCompetents(@Param("maladieId") Long maladieId,
                                         @Param("zoneId") Long zoneId);

    /**
     * Les veterinaires en service d'une zone, toutes competences confondues :
     * le recours quand aucun specialiste de la maladie ne couvre la zone.
     */
    @Query("""
           select v from Veterinaire v
           where v.actif = true and v.zoneIntervention.id = :zoneId
           order by v.disponible desc, v.nom asc
           """)
    List<Veterinaire> chercherActifsDansZone(@Param("zoneId") Long zoneId);

    /**
     * Les veterinaires en service d'une zone qui traitent au moins une
     * maladie de cette filiere : le recours de niveau 2 quand aucun
     * specialiste de la maladie ne couvre la zone (audit veterinaire M2). Un
     * veterinaire de volailles est mieux place qu'un veterinaire de bovins
     * pour un lot de poulets.
     */
    @Query("""
           select distinct v from Veterinaire v
           join v.competences c
           join c.maladie m
           join m.filieres f
           where v.actif = true and v.zoneIntervention.id = :zoneId
             and f = :filiere
           order by v.nom asc
           """)
    List<Veterinaire> chercherActifsDansZoneParFiliere(
            @Param("zoneId") Long zoneId,
            @Param("filiere") ml.moncheptel.api.modele.enumeration.Filiere filiere);

    /** « Veterinaires competents : 3 » sur la fiche d'une maladie. */
    @Query("""
           select count(distinct v) from Veterinaire v
           join v.competences c
           where v.actif = true and c.maladie.id = :maladieId
             and (:zoneId is null or v.zoneIntervention.id = :zoneId)
           """)
    long compterCompetents(@Param("maladieId") Long maladieId, @Param("zoneId") Long zoneId);
}
