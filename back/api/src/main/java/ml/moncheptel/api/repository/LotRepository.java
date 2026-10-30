package ml.moncheptel.api.repository;

import java.time.LocalDate;
import java.util.List;
import ml.moncheptel.api.modele.Lot;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LotRepository extends JpaRepository<Lot, Long> {

    List<Lot> findByProprietaireIdAndStatutOrderByDateEntreeDesc(Long eleveurId, StatutUnite statut);

    /**
     * « Lots actifs : 3 » et « Lots clotures : 1 » sur le tableau de bord.
     *
     * <h3>Pourquoi cette requete a remplace une methode derivee</h3>
     *
     * <p>Elle s'appelait {@code countByProprietaireIdAndStatut(eleveurId,
     * statut)} et ne prenait <b>aucune filiere</b>. Le tableau de bord
     * comptait donc tous les lots de l'eleveur, alors que la meme carte
     * affichait juste a cote un nombre de tetes filtre sur la filiere
     * choisie : « 2 lots actifs, 320 tetes » etait possible.
     *
     * <p>Le defaut ne se voyait pas dans le jeu de demonstration, ou les trois
     * lots sont de la volaille : le compteur donnait le bon chiffre par
     * coincidence. Il serait apparu chez le premier eleveur melangeant des
     * lots de volaille et un troupeau ovin suivi par lot.
     *
     * <p>{@code filiere} nulle : toutes filieres, comme
     * {@link #sommerEffectifActif}. Un seul chemin de requete pour l'onglet
     * d'une filiere et pour l'ecran « Toutes ».
     */
    @Query("""
           select count(l.id) from Lot l
           where l.proprietaire.id = :eleveurId
             and l.statut = :statut
             and (:filiere is null or l.typeElevage.filiere = :filiere)
           """)
    long compterParStatut(@Param("eleveurId") Long eleveurId,
                          @Param("filiere") Filiere filiere,
                          @Param("statut") StatutUnite statut);

    /**
     * « 320 tetes restantes ».
     *
     * <p>{@code filiere} nulle : toutes filieres confondues. Le tableau de
     * bord affiche les compteurs de l'onglet choisi, mais l'ecran « Toutes »
     * existe aussi — un seul chemin de requete pour les deux.
     */
    @Query("""
           select coalesce(sum(l.effectifRestant), 0) from Lot l
           where l.proprietaire.id = :eleveurId
             and l.statut = ml.moncheptel.api.modele.enumeration.StatutUnite.ACTIF
             and (:filiere is null or l.typeElevage.filiere = :filiere)
           """)
    long sommerEffectifActif(@Param("eleveurId") Long eleveurId,
                             @Param("filiere") Filiere filiere);

    /**
     * « 150 tetes sorties » — la somme des MOUVEMENTS de sortie des lots
     * (vente, mort, transfert), actifs ou termines.
     *
     * <p>Audit global G5 : le compteur etait {@code initial - restant} sur
     * les seuls lots CLOTURE. Les ventes partielles d'un lot actif n'y
     * entraient jamais, et un lot termine avec des tetes restantes y
     * comptait des tetes qui n'etaient sorties nulle part. Le journal des
     * mouvements, lui, ne ment pas.
     *
     * <p>{@code depuis} borne la periode sur la date du mouvement ; nulle, on
     * prend tout l'historique.
     */
    @Query("""
           select coalesce(sum(m.nombreTetes), 0)
           from MouvementCheptel m join Lot l on l.id = m.unite.id
           where l.proprietaire.id = :eleveurId
             and m.type in (ml.moncheptel.api.modele.enumeration.TypeMouvement.VENTE,
                            ml.moncheptel.api.modele.enumeration.TypeMouvement.MORT,
                            ml.moncheptel.api.modele.enumeration.TypeMouvement.TRANSFERT)
             and (:filiere is null or l.typeElevage.filiere = :filiere)
             and (:depuis is null or m.dateMouvement >= :depuis)
           """)
    long sommerTetesSorties(@Param("eleveurId") Long eleveurId,
                            @Param("filiere") Filiere filiere,
                            @Param("depuis") LocalDate depuis);

    /**
     * Lots dont le cycle se termine bientot.
     *
     * <p>Sert a prevenir l'eleveur avant la cloture : un poulet de chair vendu
     * trop tard mange sans grossir, et la marge part en aliment.
     */
    @Query("""
           select l from Lot l
           where l.statut = ml.moncheptel.api.modele.enumeration.StatutUnite.ACTIF
             and l.dateCloturePrevue is not null
             and l.dateCloturePrevue <= :limite
           """)
    List<Lot> chercherCyclesBientotTermines(@Param("limite") LocalDate limite);

    /**
     * Controle de coherence : effectif stocke contre journal des mouvements.
     * Doit toujours renvoyer une liste vide. Un test le verifie.
     */
    @Query("""
           select l from Lot l
           where l.effectifRestant <> (
               select coalesce(sum(case when m.type in (
                        ml.moncheptel.api.modele.enumeration.TypeMouvement.VENTE,
                        ml.moncheptel.api.modele.enumeration.TypeMouvement.MORT,
                        ml.moncheptel.api.modele.enumeration.TypeMouvement.TRANSFERT)
                    then -m.nombreTetes else m.nombreTetes end), 0)
               from MouvementCheptel m where m.unite.id = l.id)
           """)
    List<Lot> chercherIncoherences();
}
