package ml.moncheptel.api.repository;

import java.util.List;
import ml.moncheptel.api.modele.Alerte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AlerteRepository extends JpaRepository<Alerte, Long> {

    /**
     * Alertes a montrer a un eleveur : sa zone, et une filiere qu'il pratique.
     *
     * <p>Une alerte peut ne viser aucune filiere en particulier
     * ({@code filiere is null}) : elle concerne alors toutes les filieres DE
     * LA MALADIE, pas toute la zone. Un eleveur d'ovins n'a pas a lire une
     * alerte Newcastle, meme « toutes filieres ».
     */
    @Query("""
           select a from Alerte a
           join fetch a.maladie
           where a.statut = ml.moncheptel.api.modele.enumeration.StatutAlerte.ACTIVE
             and a.zone.id = :zoneId
             and (a.filiere in :filieres
                  or (a.filiere is null
                      and exists (select 1 from Maladie m join m.filieres mf
                                  where m = a.maladie and mf in :filieres)))
           order by a.dateDeclenchement desc
           """)
    List<Alerte> chercherPourEleveur(
            @Param("zoneId") Long zoneId,
            @Param("filieres") java.util.Collection<ml.moncheptel.api.modele.enumeration.Filiere> filieres);

    /** Alertes actives dont le dernier jour est passe. */
    @Query("""
           select a from Alerte a
           join fetch a.maladie
           join fetch a.zone
           where a.statut = ml.moncheptel.api.modele.enumeration.StatutAlerte.ACTIVE
             and a.dateFin is not null and a.dateFin < :aujourdhui
           """)
    List<Alerte> chercherEchues(@Param("aujourdhui") java.time.LocalDate aujourdhui);

    /** Tableau de l'administration. */
    @Query("""
           select a from Alerte a
           join fetch a.maladie
           join fetch a.zone
           order by case when a.statut = ml.moncheptel.api.modele.enumeration.StatutAlerte.ACTIVE
                         then 0 else 1 end asc,
                    a.dateDeclenchement desc
           """)
    List<Alerte> chercherToutesTriees();

    /**
     * Empeche le doublon : deux alertes actives pour la meme maladie dans la
     * meme zone notifieraient deux fois les memes eleveurs.
     */
    @Query("""
           select case when count(a.id) > 0 then true else false end from Alerte a
           where a.maladie.id = :maladieId
             and a.zone.id = :zoneId
             and a.statut = ml.moncheptel.api.modele.enumeration.StatutAlerte.ACTIVE
             and (a.filiere is null or :filiere is null or a.filiere = :filiere)
           """)
    boolean existeDejaActive(@Param("maladieId") Long maladieId, @Param("zoneId") Long zoneId,
                             @Param("filiere") ml.moncheptel.api.modele.enumeration.Filiere filiere);
}
