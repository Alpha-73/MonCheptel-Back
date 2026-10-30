package ml.moncheptel.api.repository;

import java.time.LocalDate;
import java.util.List;
import ml.moncheptel.api.modele.RappelSanitaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RappelSanitaireRepository extends JpaRepository<RappelSanitaire, Long> {

    List<RappelSanitaire> findByUniteIdOrderByDateEcheanceAsc(Long uniteId);

    /**
     * Rappels a afficher sur le tableau de bord d'un eleveur.
     *
     * <p>« Rappel vaccination — LOT-014, echeance dans 3 jours ». On prend une
     * fenetre plutot qu'une date exacte : un rappel echu hier interesse
     * encore, et il vaut mieux le montrer en retard que pas du tout.
     */
    @Query("""
           select r from RappelSanitaire r
           join fetch r.unite u
           where u.proprietaire.id = :eleveurId
             and u.statut = ml.moncheptel.api.modele.enumeration.StatutUnite.ACTIF
             and r.statut in (
                 ml.moncheptel.api.modele.enumeration.StatutRappel.A_VENIR,
                 ml.moncheptel.api.modele.enumeration.StatutRappel.ECHU)
             and r.dateEcheance >= :plancher
             and r.dateEcheance <= :limite
           order by r.dateEcheance asc
           """)
    List<RappelSanitaire> chercherAAfficher(@Param("eleveurId") Long eleveurId,
                                            @Param("plancher") LocalDate plancher,
                                            @Param("limite") LocalDate limite);

    /** Rappels a reevaluer chaque jour par la tache planifiee. */
    @Query("""
           select r from RappelSanitaire r
           where r.statut = ml.moncheptel.api.modele.enumeration.StatutRappel.A_VENIR
             and r.dateEcheance < :aujourdHui
           """)
    List<RappelSanitaire> chercherEchus(@Param("aujourdHui") LocalDate aujourdHui);

    /** Rappels arrivant a echeance a une date donnee : ceux a notifier. */
    @Query("""
           select r from RappelSanitaire r
           join fetch r.unite u
           join fetch u.proprietaire
           where r.statut = ml.moncheptel.api.modele.enumeration.StatutRappel.A_VENIR
             and r.dateEcheance = :date
           """)
    List<RappelSanitaire> chercherPourNotification(@Param("date") LocalDate date);
}
