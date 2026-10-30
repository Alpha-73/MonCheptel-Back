package ml.moncheptel.api.modele;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;
import ml.moncheptel.api.modele.enumeration.TrancheEffectif;

/**
 * Ce qu'un eleveur declare quand ses betes vont mal.
 *
 * <p>Le principe tient dans une phrase de l'ecran : « Touchez tout ce que vous
 * observez. <b>Vous n'avez pas a nommer la maladie.</b> » L'eleveur decrit, le
 * systeme deduit. C'est l'inverse d'un formulaire ou il faudrait deja savoir.
 *
 * <p>L'ampleur est une <b>tranche</b>, pas un nombre : trois boutons, « moins
 * de 10 », « 10 a 50 », « plus de 50 ». Un eleveur qui voit ses poulets mourir
 * estime, il ne compte pas. Lui demander un chiffre exact produirait un faux
 * avec une apparence de precision.
 *
 * <p>La probabilite calculee n'est affichee nulle part : l'ecran dit « maladie
 * probable » et « autre hypothese ». Elle est conservee pour classer les
 * candidates et pour les statistiques — pas pour demander a l'eleveur
 * d'arbitrer entre 65 % et 28 %.
 */
@Entity
@Table(name = "signalement_maladie")
public class SignalementMaladie extends EntiteBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eleveur_id", nullable = false)
    private Eleveur eleveur;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unite_id", nullable = false)
    private UniteElevage unite;

    /** Identifiants des symptomes coches. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "signalement_symptome",
            joinColumns = @JoinColumn(name = "signalement_id"))
    @Column(name = "symptome_id", nullable = false)
    private Set<Long> symptomes = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "tranche_effectif", nullable = false, length = 16)
    private TrancheEffectif trancheEffectif;

    /**
     * « Autre chose a signaler ? Facultatif — ce que la liste ne dit pas. »
     *
     * <h3>Les accents graves ne sont pas decoratifs</h3>
     *
     * <p>{@code precision} est un MOT RESERVE de MySQL — il fait partie de
     * {@code DOUBLE PRECISION}. La migration V1 le savait et a cite la colonne
     * a la creation. Le mapping, lui, ne portait pas cette citation : sans
     * {@code name}, Hibernate derive le nom {@code precision} du champ et
     * l'ecrit NU dans ses requetes.
     *
     * <p>Resultat mesure le 4 octobre : l'INSERT du signalement echouait en
     * {@code SQLSyntaxErrorException}, et {@code POST /api/signalements} —
     * la fonctionnalite centrale de la plateforme — rendait 500. Les trois
     * scenarios du diagnostic tombaient avec, et les quatre scenarios de
     * prise en charge veterinaire qui en dependent restaient intestables.
     *
     * <p>Pourquoi aucun controle ne l'a vu :
     * <ul>
     *   <li>{@code ddl-auto: validate} compare des NOMS, et le nom derive est
     *       bien {@code precision} : la validation passe ;</li>
     *   <li>le volet 6 de l'audit confronte les entites au schema reel, et
     *       trouve la colonne : elle existe ;</li>
     *   <li>les migrations rejouees passaient — mais sur MariaDB, qui ne
     *       reserve pas ce mot comme MySQL 8 ;</li>
     *   <li>et la route n'etait exercee par aucun scenario reussi : tous les
     *       autres sortent avant l'INSERT, sur une validation ou un refus.</li>
     * </ul>
     *
     * <p>Seul l'appel reel de la route pouvait le reveler. Les accents graves
     * du {@code name} sont transmis par Hibernate au dialecte, qui les traduit
     * en citation MySQL.
     */
    @Size(max = 1000)
    @Column(name = "`precision`", length = 1000)
    private String precision;

    @Column(name = "date_signalement", nullable = false)
    private Instant dateSignalement = Instant.now();

    // ── resultat du moteur de diagnostic ────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maladie_probable_id")
    private Maladie maladieProbable;

    @Column(precision = 4, scale = 3)
    private BigDecimal probabilite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maladie_alternative_id")
    private Maladie maladieAlternative;

    @Column(name = "probabilite_alternative", precision = 4, scale = 3)
    private BigDecimal probabiliteAlternative;

    /**
     * La maladie que le veterinaire retient en cloturant le cas. Nulle tant
     * qu'aucun veterinaire n'a tranche : les statistiques comptent alors la
     * proposition du moteur.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maladie_confirmee_id")
    private Maladie maladieConfirmee;

    /** Croisement de la gravite de la maladie et de l'ampleur declaree. */
    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private NiveauGravite gravite;

    // ── suivi ───────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatutSignalement statut = StatutSignalement.NOUVEAU;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinaire_id")
    private Veterinaire veterinaire;

    @Column(name = "date_orientation")
    private Instant dateOrientation;

    /** Quand le veterinaire a pris le cas en main : mesure le delai de prise en charge. */
    @Column(name = "date_prise_en_charge")
    private Instant datePriseEnCharge;

    // ── relances (V14) ──────────────────────────────────────────────────────

    /** Rappel envoye au veterinaire qui n'a pas encore repondu. */
    @Column(name = "date_relance_veterinaire")
    private Instant dateRelanceVeterinaire;

    /**
     * L'eleveur a ete invite a changer de veterinaire (pas de reponse), ou a
     * en choisir un (cas urgent reste sans veterinaire).
     */
    @Column(name = "date_relance_eleveur")
    private Instant dateRelanceEleveur;

    /** Retour « sans veterinaire » (refus, depart, zone) : le delai repart de la. */
    @Column(name = "date_remise_en_attente")
    private Instant dateRemiseEnAttente;

    @Column(name = "date_traitement")
    private Instant dateTraitement;

    @Size(max = 2000)
    @Column(name = "compte_rendu", length = 2000)
    private String compteRendu;

    /**
     * Verrou optimiste (V11). Une cloture par le veterinaire et une
     * reattribution par l'administration au meme instant ne s'ecrasent plus :
     * la seconde ecriture echoue, et l'API rend 409 (audit veterinaire M4).
     */
    @jakarta.persistence.Version
    @Column(nullable = false)
    private long version;

    protected SignalementMaladie() {
    }

    public SignalementMaladie(Eleveur eleveur, UniteElevage unite,
                              Set<Long> symptomes, TrancheEffectif tranche, String precision) {
        if (symptomes == null || symptomes.isEmpty()) {
            throw new IllegalArgumentException("Un signalement porte au moins un symptome");
        }
        this.eleveur = eleveur;
        this.unite = unite;
        this.symptomes = new LinkedHashSet<>(symptomes);
        this.trancheEffectif = tranche;
        this.precision = precision;
    }

    // ── comportement ────────────────────────────────────────────────────────

    /**
     * Enregistre le verdict du moteur et en deduit la gravite.
     *
     * <p>La gravite ne vient pas de la maladie seule : un sujet malade sur
     * cent-quatre-vingts n'est pas une epidemie. C'est le croisement de la
     * gravite intrinseque et de l'ampleur declaree qui decide de l'urgence.
     */
    public void enregistrerDiagnostic(Maladie probable, BigDecimal probabilite,
                                      Maladie alternative, BigDecimal probabiliteAlternative) {
        this.maladieProbable = probable;
        this.probabilite = probabilite;
        this.maladieAlternative = alternative;
        this.probabiliteAlternative = probabiliteAlternative;
        this.gravite = probable == null
                ? trancheEffectif.graviteInduite()
                : probable.getGravite().max(trancheEffectif.graviteInduite());
    }

    /**
     * Vrai si ce signalement doit partir vers un veterinaire.
     * Deux motifs : la gravite constatee, ou une maladie qui oriente d'office
     * — le Newcastle se propage trop vite pour etre traite seul.
     */
    public boolean doitEtreOriente() {
        return gravite == NiveauGravite.ELEVE
                || (maladieProbable != null && maladieProbable.isOrienteVersVeterinaire());
    }

    /**
     * Le cas est confie a ce veterinaire, qui doit l'ACCEPTER
     * ({@link #prendreEnCharge}) ou le refuser. Les relances repartent a zero.
     */
    public void orienterVers(Veterinaire veterinaire) {
        this.veterinaire = veterinaire;
        this.statut = StatutSignalement.ORIENTE;
        this.dateOrientation = Instant.now();
        this.dateRelanceVeterinaire = null;
        this.dateRelanceEleveur = null;
    }

    /**
     * Le veterinaire n'est plus la (desactive, supprime, hors zone) : le cas
     * revient en attente d'orientation, sans perdre son diagnostic.
     */
    public void detacherDuVeterinaire() {
        this.veterinaire = null;
        this.statut = StatutSignalement.NOUVEAU;
        this.dateOrientation = null;
        this.datePriseEnCharge = null;
        this.dateRemiseEnAttente = Instant.now();
        this.dateRelanceVeterinaire = null;
        this.dateRelanceEleveur = null;
    }

    /**
     * Depuis quand le cas attend : la reponse du veterinaire s'il est confie,
     * sinon le choix d'un veterinaire (depuis le signalement ou le dernier
     * retour en attente).
     */
    public Instant debutAttente() {
        if (statut == StatutSignalement.ORIENTE && dateOrientation != null) {
            return dateOrientation;
        }
        return dateRemiseEnAttente != null ? dateRemiseEnAttente : dateSignalement;
    }

    public void marquerRelanceVeterinaire(Instant quand) {
        this.dateRelanceVeterinaire = quand;
    }

    public void marquerRelanceEleveur(Instant quand) {
        this.dateRelanceEleveur = quand;
        if (this.dateRelanceVeterinaire == null) {
            this.dateRelanceVeterinaire = quand;
        }
    }

    public void prendreEnCharge() {
        if (statut != StatutSignalement.ORIENTE) {
            throw new IllegalStateException(
                    statut == StatutSignalement.TRAITE
                    ? "Ce cas est déjà clôturé."
                    : statut == StatutSignalement.EN_COURS
                    ? "Vous avez déjà pris ce cas en charge."
                    : "Ce cas n'est pas orienté vers un vétérinaire : il ne peut pas être pris en charge.");
        }
        this.statut = StatutSignalement.EN_COURS;
        this.datePriseEnCharge = Instant.now();
    }

    public void marquerTraite(String compteRendu) {
        marquerTraite(compteRendu, null);
    }

    /** {@code confirmee} : le diagnostic du veterinaire, s'il en pose un. */
    public void marquerTraite(String compteRendu, Maladie confirmee) {
        this.statut = StatutSignalement.TRAITE;
        this.maladieConfirmee = confirmee;
        this.dateTraitement = Instant.now();
        // Traite sans passer par « en cours » : la prise en charge, c'est maintenant.
        if (this.datePriseEnCharge == null) {
            this.datePriseEnCharge = this.dateTraitement;
        }
        this.compteRendu = compteRendu;
    }

    // ── accesseurs ──────────────────────────────────────────────────────────

    public Eleveur getEleveur() { return eleveur; }
    public UniteElevage getUnite() { return unite; }
    public Set<Long> getSymptomes() { return symptomes; }
    public TrancheEffectif getTrancheEffectif() { return trancheEffectif; }
    public String getPrecision() { return precision; }
    public Instant getDateSignalement() { return dateSignalement; }
    public Maladie getMaladieProbable() { return maladieProbable; }
    public BigDecimal getProbabilite() { return probabilite; }
    public Maladie getMaladieAlternative() { return maladieAlternative; }
    public BigDecimal getProbabiliteAlternative() { return probabiliteAlternative; }
    public Maladie getMaladieConfirmee() { return maladieConfirmee; }

    /** La maladie retenue : celle du veterinaire, a defaut celle du moteur. */
    public Maladie getMaladieRetenue() {
        return maladieConfirmee != null ? maladieConfirmee : maladieProbable;
    }
    public NiveauGravite getGravite() { return gravite; }
    public StatutSignalement getStatut() { return statut; }
    public Veterinaire getVeterinaire() { return veterinaire; }
    public Instant getDateOrientation() { return dateOrientation; }
    public Instant getDatePriseEnCharge() { return datePriseEnCharge; }
    public Instant getDateRelanceVeterinaire() { return dateRelanceVeterinaire; }
    public Instant getDateRelanceEleveur() { return dateRelanceEleveur; }
    public Instant getDateRemiseEnAttente() { return dateRemiseEnAttente; }
    public Instant getDateTraitement() { return dateTraitement; }
    public String getCompteRendu() { return compteRendu; }
    public long getVersion() { return version; }
}