package ml.moncheptel.api.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import ml.moncheptel.api.modele.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Les agregats des deux ecrans de pilotage de l'administration.
 *
 * <h2>Pourquoi un repository a part</h2>
 *
 * <p>Ces requetes ne portent pas sur une entite mais sur la PLATEFORME : elles
 * comptent des eleveurs, des animaux, des signalements et des conseils dans la
 * meme reponse. Les repartir dans les repositories de chaque entite
 * obligerait le service a en injecter huit, et personne ne saurait plus ou
 * chercher « le nombre d'animaux enregistres ».
 *
 * <p>Il est declare sur {@link Utilisateur} parce que Spring Data exige une
 * entite racine ; la plupart des requetes la designent explicitement de toute
 * facon.
 *
 * <h2>Ce qui N'EST PAS ici, et pourquoi</h2>
 *
 * <p>La maquette affiche « + 86 ce mois » a cote du nombre d'utilisateurs, et
 * « + 5 pts » a cote du taux de retention. Le premier se calcule — une date
 * d'inscription suffit. <b>Le second non</b> : comparer a il y a un mois
 * demande un releve pris il y a un mois, et rien ne les conserve. Il faudrait
 * une table d'instantanes alimentee chaque nuit.
 *
 * <p>Ces ecarts ne sont donc pas rendus par l'API. Mieux vaut un chiffre
 * absent qu'un chiffre invente a la soutenance.
 */
@Repository
public interface StatistiqueRepository extends JpaRepository<Utilisateur, Long> {

    // ── comptes ─────────────────────────────────────────────────────────────

    @Query("select count(e) from Eleveur e")
    long compterEleveurs();

    @Query("select count(v) from Veterinaire v")
    long compterVeterinaires();

    @Query("select count(a) from Administrateur a")
    long compterAdministrateurs();

    @Query("select count(u) from Utilisateur u where u.dateInscription >= :depuis")
    long compterInscritsDepuis(@Param("depuis") LocalDate depuis);

    /** Les eleveurs seuls : le tableau de bord pose ce chiffre sous « Éleveurs inscrits ». */
    @Query("select count(e) from Eleveur e where e.dateInscription >= :depuis")
    long compterEleveursInscritsDepuis(@Param("depuis") LocalDate depuis);

    /**
     * Combien de comptes se sont connectes depuis cette date.
     *
     * <p>C'est la definition retenue pour le « taux de retention 30 j » de la
     * maquette, qui n'en donnait aucune : la part des comptes ayant ouvert
     * l'application au moins une fois dans les trente derniers jours. C'est la
     * mesure habituelle, et c'est la seule que les donnees disponibles
     * permettent.
     *
     * <p>A dire telle quelle a la soutenance : un taux de retention n'a de
     * sens qu'accompagne de sa definition.
     */
    @Query("select count(u) from Utilisateur u where u.derniereConnexion >= :depuis")
    long compterActifsDepuis(@Param("depuis") Instant depuis);

    /** Les veterinaires en service : le tableau de bord ne compte pas les comptes desactives. */
    @Query("select count(v) from Veterinaire v where v.actif = true")
    long compterVeterinairesActifs();

    /** Les zones d'intervention distinctes couvertes par un veterinaire en service. */
    @Query("""
           select count(distinct v.zoneIntervention.id) from Veterinaire v
           where v.zoneIntervention is not null and v.actif = true
           """)
    long compterZonesCouvertes();

    /** Nom de zone et nombre d'eleveurs, du plus peuple au moins peuple. */
    @Query("""
           select z.nom, count(e.id) from Eleveur e join e.zone z
           group by z.id, z.nom
           order by count(e.id) desc
           """)
    List<Object[]> compterEleveursParZone();

    /**
     * Nombre d'eleveurs par filiere pratiquee.
     *
     * <p>Un eleveur qui pratique quatre filieres compte dans les quatre : la
     * somme de ces nombres DEPASSE donc le nombre d'eleveurs. Ce n'est pas une
     * erreur, et l'ecran ne doit pas presenter ces valeurs comme des parts
     * d'un total — la maquette les affiche bien comme des nombres absolus.
     */
    @Query("""
           select f, count(e.id) from Eleveur e join e.filieresPratiquees f
           group by f order by count(e.id) desc
           """)
    List<Object[]> compterEleveursParFiliere();

    // ── cheptel ─────────────────────────────────────────────────────────────

    /**
     * Le nombre de tetes vivantes sur toute la plateforme.
     *
     * <p>{@code coalesce} parce qu'une somme sur zero ligne rend {@code null},
     * pas zero : sans lui, une base vide ferait planter l'ecran d'accueil de
     * l'administration au lieu d'afficher 0.
     */
    @Query("""
           select coalesce(sum(l.effectifRestant), 0) from Lot l
           where l.statut = ml.moncheptel.api.modele.enumeration.StatutUnite.ACTIF
             and (:filiere is null or l.typeElevage.filiere = :filiere)
           """)
    long sommerTetesEnLot(@Param("filiere") ml.moncheptel.api.modele.enumeration.Filiere filiere);

    @Query("""
           select count(a) from Animal a
           where a.statut = ml.moncheptel.api.modele.enumeration.StatutUnite.ACTIF
             and (:filiere is null or a.typeElevage.filiere = :filiere)
           """)
    long compterAnimauxSuivis(@Param("filiere") ml.moncheptel.api.modele.enumeration.Filiere filiere);

    // ── sante ───────────────────────────────────────────────────────────────

    @Query("select count(s) from SignalementMaladie s where s.dateSignalement >= :depuis")
    long compterSignalementsDepuis(@Param("depuis") Instant depuis);

    @Query("""
           select count(s) from SignalementMaladie s
           where s.statut = ml.moncheptel.api.modele.enumeration.StatutSignalement.TRAITE
             and s.dateSignalement >= :depuis
           """)
    long compterSignalementsTraitesDepuis(@Param("depuis") Instant depuis);

    /**
     * Combien de signalements ont REELLEMENT NECESSITE un veterinaire.
     *
     * <h3>Le denominateur de « signalements traites »</h3>
     *
     * <p>Ajoutee le 5 octobre. La tuile de la maquette se calculait sur
     * {@link #compterSignalementsDepuis}, c'est-a-dire sur TOUS les
     * signalements. Or un signalement ne peut atteindre {@code TRAITE} qu'en
     * passant par {@code ORIENTE} :
     * {@code SignalementMaladie.prendreEnCharge()} refuse tout autre statut.
     *
     * <p>Et {@code SignalementService.orienter} n'oriente que si
     * {@code doitEtreOriente()} — gravite ELEVEE, ou maladie qui oriente
     * d'office. Les cas benins ne sont donc jamais orientes, par conception :
     * l'eleveur les traite seul avec le traitement affiche a l'ecran.
     *
     * <p>Consequence mesuree : ces cas restaient au denominateur sans pouvoir
     * jamais rejoindre le numerateur. Le pourcentage portait un <b>plafond
     * structurel</b> — plus la plateforme recevait de signalements benins,
     * plus elle paraissait defaillante, alors qu'elle fonctionnait exactement
     * comme prevu.
     *
     * <p>La tuile demande « des cas qui necessitaient un veterinaire, combien
     * ont ete pris en charge ? ». Le denominateur est donc l'ensemble des
     * signalements qui ont franchi l'orientation : {@code ORIENTE} (en
     * attente), {@code EN_COURS} (pris en charge) et {@code TRAITE} (clos),
     * PLUS les cas graves restes sans veterinaire faute de couverture : les
     * exclure ferait monter le taux justement quand la couverture manque.
     *
     * <p>Ce qui RESTE a faire, et qui est une fonctionnalite, pas ce
     * defaut-ci : un signalement benin n'est cloturable par aucune route, donc
     * la liste de l'eleveur grossit indefiniment. Un statut pose par
     * l'eleveur — « mes betes vont mieux » — y repondrait. Reporte en V2 :
     * c'est un manque de confort, pas un chiffre faux.
     */
    @Query("""
           select count(s) from SignalementMaladie s
           left join s.maladieProbable m
           where (s.statut in (
                   ml.moncheptel.api.modele.enumeration.StatutSignalement.ORIENTE,
                   ml.moncheptel.api.modele.enumeration.StatutSignalement.EN_COURS,
                   ml.moncheptel.api.modele.enumeration.StatutSignalement.TRAITE)
                  or s.gravite = ml.moncheptel.api.modele.enumeration.NiveauGravite.ELEVE
                  or m.orienteVersVeterinaire = true)
             and s.dateSignalement >= :depuis
           """)
    long compterNecessitantUnVeterinaireDepuis(@Param("depuis") Instant depuis);

    @Query("""
           select count(s) from SignalementMaladie s
           where s.statut = ml.moncheptel.api.modele.enumeration.StatutSignalement.ORIENTE
           """)
    long compterOrientesEnAttente();

    /**
     * Delai moyen, en heures, entre le signalement et sa prise en charge par
     * un veterinaire.
     *
     * <p>C'est le « délai moyen de prise en charge · éleveur → vétérinaire »
     * de la maquette. Calcule en SQL natif : la difference entre deux
     * {@code Instant} n'a pas d'equivalent portable en JPQL, et la faire en
     * Java obligerait a ramener tous les signalements.
     *
     * <p>Mesure jusqu'a la PRISE EN CHARGE par le veterinaire, pas jusqu'a
     * l'orientation : celle-ci est automatique et immediate, elle donnait
     * toujours « 0 min ».
     *
     * <p>Rend {@code null} si aucun signalement n'a encore ete pris en charge — un
     * cas reel sur une base neuve, que le service doit traiter.
     */
    @Query(value = """
           SELECT AVG(TIMESTAMPDIFF(MINUTE, date_signalement, date_prise_en_charge)) / 60.0
             FROM signalement_maladie
            WHERE date_prise_en_charge IS NOT NULL
              AND date_signalement >= :depuis
           """, nativeQuery = true)
    Double delaiMoyenPriseEnChargeHeures(@Param("depuis") Instant depuis);

    /**
     * Les maladies les plus signalees, nom et nombre.
     *
     * <p>Chaque cas compte pour la maladie que le veterinaire a retenue a la
     * cloture, et pour la proposition du moteur tant qu'aucun veterinaire n'a
     * tranche. Un cas que le moteur prenait pour de la coccidiose et que le
     * veterinaire a requalifie en Newcastle compte pour Newcastle.
     */
    @Query("""
           select coalesce(mc.nom, mp.nom), count(s.id) from SignalementMaladie s
           left join s.maladieConfirmee mc
           left join s.maladieProbable mp
           where s.dateSignalement >= :depuis
             and (mc.id is not null or mp.id is not null)
           group by coalesce(mc.nom, mp.nom)
           order by count(s.id) desc
           """)
    List<Object[]> compterSignalementsParMaladie(@Param("depuis") Instant depuis);

    // ── series des graphiques ───────────────────────────────────────────────

    /** Date et gravite de chaque signalement depuis {@code depuis}. */
    @Query("select s.dateSignalement, s.gravite from SignalementMaladie s where s.dateSignalement >= :depuis")
    List<Object[]> signalementsDepuis(@Param("depuis") Instant depuis);

    @Query("select e.dateInscription from Eleveur e where e.dateInscription >= :depuis")
    List<LocalDate> inscriptionsEleveursDepuis(@Param("depuis") LocalDate depuis);

    @Query("select v.dateInscription from Veterinaire v where v.dateInscription >= :depuis")
    List<LocalDate> inscriptionsVeterinairesDepuis(@Param("depuis") LocalDate depuis);

    /** Nombre de signalements par statut, depuis {@code depuis}. */
    @Query("""
           select s.statut, count(s) from SignalementMaladie s
           where s.dateSignalement >= :depuis group by s.statut
           """)
    List<Object[]> compterParStatutDepuis(@Param("depuis") Instant depuis);

    /** Signalement et prise en charge de chaque cas pris en main. */
    @Query("""
           select s.dateSignalement, s.datePriseEnCharge from SignalementMaladie s
           where s.datePriseEnCharge is not null and s.dateSignalement >= :depuis
           """)
    List<Object[]> prisesEnChargeDepuis(@Param("depuis") Instant depuis);

    /**
     * Pour chaque cas : la maladie proposee par le moteur et celle retenue par
     * le veterinaire. Colonnes : id propose, id confirme, nom retenu, gravite
     * de la maladie retenue.
     */
    @Query("""
           select mp.id, mc.id, coalesce(mc.nom, mp.nom), coalesce(mc.gravite, mp.gravite)
           from SignalementMaladie s
           left join s.maladieProbable mp
           left join s.maladieConfirmee mc
           where s.dateSignalement >= :depuis
             and (mc.id is not null or mp.id is not null)
           """)
    List<Object[]> diagnosticsDepuis(@Param("depuis") Instant depuis);

    // ── alertes et conseils ─────────────────────────────────────────────────

    @Query("""
           select count(a) from Alerte a
           where a.statut = ml.moncheptel.api.modele.enumeration.StatutAlerte.ACTIVE
           """)
    long compterAlertesActives();

    @Query("""
           select distinct a.zone.nom from Alerte a
           where a.statut = ml.moncheptel.api.modele.enumeration.StatutAlerte.ACTIVE
           order by a.zone.nom
           """)
    List<String> zonesSousAlerte();

    @Query("select count(a) from Alerte a where a.dateDeclenchement >= :depuis")
    long compterAlertesDepuis(@Param("depuis") Instant depuis);

    @Query("""
           select count(c) from Conseil c
           where c.statut = ml.moncheptel.api.modele.enumeration.StatutConseil.PUBLIE
           """)
    long compterConseilsPublies();
}
