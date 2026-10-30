package ml.moncheptel.api.repository;

import java.time.LocalDate;
import java.util.List;
import ml.moncheptel.api.modele.OperationFinanciere;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.repository.projection.EvolutionProjection;
import ml.moncheptel.api.repository.projection.RepartitionProjection;
import ml.moncheptel.api.repository.projection.SoldeProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Acces aux operations, et surtout aux agregats.
 *
 * <p>Toutes les sommes se font EN BASE. Charger cinq operations pour les
 * additionner en Java marcherait aujourd'hui ; avec trois ans d'historique,
 * l'ecran des rapports ramenerait des milliers de lignes pour afficher trois
 * nombres.
 */
@Repository
public interface OperationFinanciereRepository extends JpaRepository<OperationFinanciere, Long> {

    /** Journal — ecran « Depenses - Liste ». */
    @Query("""
           select o from OperationFinanciere o
           left join fetch o.unite
           where o.eleveur.id = :eleveurId
             and (:filiere is null or o.filiere = :filiere)
             and o.dateOperation between :debut and :fin
           order by o.dateOperation desc, o.id desc
           """)
    List<OperationFinanciere> chercherJournal(@Param("eleveurId") Long eleveurId,
                                              @Param("filiere") Filiere filiere,
                                              @Param("debut") LocalDate debut,
                                              @Param("fin") LocalDate fin);

    /**
     * Solde d'une periode. Produit le « + 74 000 FCFA » du tableau de bord.
     *
     * <p>Le {@code coalesce} n'est pas cosmetique : sans lui, un eleveur qui
     * n'a rien saisi ce mois-ci recevrait {@code null} et l'ecran afficherait
     * un tiret la ou il devrait afficher zero.
     */
    @Query("""
           select
             coalesce(sum(case when o.type = ml.moncheptel.api.modele.enumeration.TypeOperation.REVENU
                          then o.montant else 0 end), 0) as revenus,
             coalesce(sum(case when o.type = ml.moncheptel.api.modele.enumeration.TypeOperation.DEPENSE
                          then o.montant else 0 end), 0) as depenses,
             coalesce(sum(case when o.type = ml.moncheptel.api.modele.enumeration.TypeOperation.REVENU
                          then o.montant else -o.montant end), 0) as solde
           from OperationFinanciere o
           where o.eleveur.id = :eleveurId
             and (:filiere is null or o.filiere = :filiere)
             and o.dateOperation between :debut and :fin
           """)
    SoldeProjection calculerSolde(@Param("eleveurId") Long eleveurId,
                                  @Param("filiere") Filiere filiere,
                                  @Param("debut") LocalDate debut,
                                  @Param("fin") LocalDate fin);

    /**
     * Rentabilite d'une unite depuis sa mise en place — ecran « Rentabilite ».
     *
     * <p>Ne compte QUE les operations rattachees a cette unite. Les depenses
     * partagees — le sac d'aliment achete en gros — en sont exclues par
     * construction : les repartir entre les lots demanderait une cle de
     * repartition que personne n'a saisie, et inventer cette cle rendrait la
     * rentabilite par lot fausse sans qu'on puisse le voir.
     */
    @Query("""
           select
             coalesce(sum(case when o.type = ml.moncheptel.api.modele.enumeration.TypeOperation.REVENU
                          then o.montant else 0 end), 0) as revenus,
             coalesce(sum(case when o.type = ml.moncheptel.api.modele.enumeration.TypeOperation.DEPENSE
                          then o.montant else 0 end), 0) as depenses,
             coalesce(sum(case when o.type = ml.moncheptel.api.modele.enumeration.TypeOperation.REVENU
                          then o.montant else -o.montant end), 0) as solde
           from OperationFinanciere o
           where o.unite.id = :uniteId
           """)
    SoldeProjection calculerRentabiliteUnite(@Param("uniteId") Long uniteId);

    /** « Repartition des depenses » : aliment 52 000, soins 21 000, main-d'oeuvre 35 000. */
    @Query("""
           select o.categorie as categorie, sum(o.montant) as total
           from OperationFinanciere o
           where o.eleveur.id = :eleveurId
             and o.type = ml.moncheptel.api.modele.enumeration.TypeOperation.DEPENSE
             and (:filiere is null or o.filiere = :filiere)
             and o.dateOperation between :debut and :fin
           group by o.categorie
           order by sum(o.montant) desc
           """)
    List<RepartitionProjection> repartirDepenses(@Param("eleveurId") Long eleveurId,
                                                 @Param("filiere") Filiere filiere,
                                                 @Param("debut") LocalDate debut,
                                                 @Param("fin") LocalDate fin);

    /**
     * Courbe d'evolution mensuelle — ecran « Rapports ».
     *
     * <p>{@code year()} et {@code month()} plutot que {@code date_format} :
     * cette derniere n'existe que sur MySQL et ferait echouer le demarrage
     * des tests, qui tournent sur H2. La mise en forme « 2026-09 » se fait
     * dans le service.
     */
    @Query("""
           select year(o.dateOperation) as annee,
                  month(o.dateOperation) as mois,
                  coalesce(sum(case when o.type = ml.moncheptel.api.modele.enumeration.TypeOperation.REVENU
                               then o.montant else 0 end), 0) as revenus,
                  coalesce(sum(case when o.type = ml.moncheptel.api.modele.enumeration.TypeOperation.DEPENSE
                               then o.montant else 0 end), 0) as depenses
           from OperationFinanciere o
           where o.eleveur.id = :eleveurId
             and (:filiere is null or o.filiere = :filiere)
             and o.dateOperation between :debut and :fin
           group by year(o.dateOperation), month(o.dateOperation)
           order by year(o.dateOperation) asc, month(o.dateOperation) asc
           """)
    List<EvolutionProjection> evolutionMensuelle(@Param("eleveurId") Long eleveurId,
                                                 @Param("filiere") Filiere filiere,
                                                 @Param("debut") LocalDate debut,
                                                 @Param("fin") LocalDate fin);

    /** Empeche de compter deux fois une vente deja saisie comme mouvement. */
    boolean existsByMouvementId(Long mouvementId);

    /** Le revenu engendre par une vente : supprime quand la vente est annulee. */
    java.util.Optional<ml.moncheptel.api.modele.OperationFinanciere> findByMouvementId(Long mouvementId);

    /**
     * Combien d'ecritures sont rattachees a cet elevage.
     *
     * <p>Garde-fou de la suppression : une unite qui porte des operations ne
     * se supprime pas, sous peine de fausser un solde mensuel deja consulte.
     * La cle etrangere est en ON DELETE SET NULL, donc la base ne s'y
     * opposerait pas — c'est au service de refuser.
     */
    long countByUniteId(Long uniteId);
}
