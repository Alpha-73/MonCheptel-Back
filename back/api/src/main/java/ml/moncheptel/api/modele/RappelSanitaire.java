package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import ml.moncheptel.api.modele.enumeration.StatutRappel;
import ml.moncheptel.api.modele.enumeration.TypeEvenement;

/**
 * Echeance sanitaire concrete pour une unite donnee.
 *
 * <p>Engendre a la creation d'une unite a partir du {@link CalendrierSanitaire}
 * de son type d'elevage. C'est l'objet derriere l'encart du tableau de bord :
 * « Rappel vaccination — LOT-014, echeance dans 3 jours ».
 *
 * <p>Se solde tout seul : enregistrer l'evenement sanitaire correspondant le
 * passe a {@link StatutRappel#FAIT}. L'eleveur n'a pas a cocher deux fois.
 */
@Entity
@Table(name = "rappel_sanitaire")
public class RappelSanitaire extends EntiteBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unite_id", nullable = false)
    private UniteElevage unite;

    /** Ligne de protocole d'origine. Nulle pour un rappel ajoute a la main. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendrier_id")
    private CalendrierSanitaire calendrier;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String libelle;

    /**
     * L'acte que ce rappel attend. Celui du protocole pour un rappel engendre,
     * celui du soin d'origine pour un rappel « dans N jours ». C'est lui qui
     * permet de solder aussi un rappel libre (V11).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_evenement", length = 16)
    private TypeEvenement typeEvenement;

    @Column(name = "date_echeance", nullable = false)
    private LocalDate dateEcheance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatutRappel statut = StatutRappel.A_VENIR;

    /**
     * Ecarte parce que l'unite est sortie du cheptel, et non par l'eleveur
     * (V14). Une sortie annulee rouvre ces rappels-la, et eux seuls.
     */
    @Column(name = "ecarte_a_la_sortie", nullable = false)
    private boolean ecarteALaSortie = false;

    /** Evenement qui a solde ce rappel. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evenement_id")
    private EvenementSanitaire evenement;

    protected RappelSanitaire() {
    }

    public RappelSanitaire(UniteElevage unite, CalendrierSanitaire calendrier,
                           String libelle, LocalDate dateEcheance) {
        this.unite = unite;
        this.calendrier = calendrier;
        this.libelle = libelle;
        this.dateEcheance = dateEcheance;
        this.typeEvenement = calendrier == null ? null : calendrier.getTypeEvenement();
    }

    /** Rappel libre (« Rappel dans N jours ») : il attend un acte de ce type. */
    public static RappelSanitaire libre(UniteElevage unite, TypeEvenement type,
                                        String libelle, LocalDate dateEcheance) {
        RappelSanitaire r = new RappelSanitaire(unite, null, libelle, dateEcheance);
        r.typeEvenement = type;
        return r;
    }

    /** Vrai si un acte de ce type peut solder ce rappel encore ouvert. */
    public boolean attend(TypeEvenement type) {
        if (statut != StatutRappel.A_VENIR && statut != StatutRappel.ECHU) {
            return false;
        }
        TypeEvenement attendu = calendrier != null ? calendrier.getTypeEvenement() : typeEvenement;
        return attendu != null && attendu == type;
    }

    /** Engendre le rappel correspondant a une ligne de protocole. */
    public static RappelSanitaire depuisCalendrier(UniteElevage unite, CalendrierSanitaire ligne) {
        return new RappelSanitaire(unite, ligne, ligne.getLibelle(),
                ligne.echeancePour(unite.getDateEntree()));
    }

    /**
     * Un rappel que le PROTOCOLE a engendre et que personne n'a encore traite.
     *
     * <p>Ceux-la, et seulement ceux-la, dependent de la date d'entree et du
     * type d'elevage de l'unite : ils doivent suivre quand l'un des deux
     * change. Un rappel ajoute a la main (pas de ligne de protocole), deja
     * fait ou ignore, est une decision de l'eleveur : on n'y touche pas.
     */
    public boolean estProgrammeEtOuvert() {
        return calendrier != null
                && (statut == StatutRappel.A_VENIR || statut == StatutRappel.ECHU);
    }

    /**
     * Recalcule l'echeance d'apres une nouvelle date d'entree, et le statut
     * avec elle : un rappel repousse dans l'avenir cesse d'etre echu.
     */
    public void reprogrammer(LocalDate nouvelleDateEntree) {
        this.dateEcheance = calendrier.echeancePour(nouvelleDateEntree);
        reevaluer(LocalDate.now());
    }

    /** Jours restants. Negatif si l'echeance est passee. */
    public long joursRestants(LocalDate aujourdHui) {
        return ChronoUnit.DAYS.between(aujourdHui, dateEcheance);
    }

    /** Reevalue le statut. Appelee chaque jour par la tache planifiee. */
    public void reevaluer(LocalDate aujourdHui) {
        if (statut == StatutRappel.FAIT || statut == StatutRappel.IGNORE) {
            return;
        }
        this.statut = dateEcheance.isBefore(aujourdHui) ? StatutRappel.ECHU : StatutRappel.A_VENIR;
    }

    public void solder(EvenementSanitaire evenement) {
        this.statut = StatutRappel.FAIT;
        this.evenement = evenement;
    }

    /**
     * Annule un soldage : l'acte qui l'avait ferme vient d'etre supprime.
     *
     * <h3>Pourquoi cette methode doit exister</h3>
     *
     * <p>Sans elle, une vaccination saisie par erreur puis supprimee
     * laisserait son rappel marque « fait », pointant vers un evenement
     * disparu. L'eleveur croirait son lot vaccine et personne ne le
     * relancerait — un silence qui coute un lot.
     *
     * <h3>Le statut est RECALCULE, pas remis a « a venir »</h3>
     *
     * <p>Un rappel dont l'echeance est passee doit redevenir ECHU, pas
     * A_VENIR : il est en retard, et l'ecran doit le dire. C'est exactement ce
     * que fait {@link #reevaluer}, qu'on reutilise au lieu d'ecrire la regle
     * une seconde fois.
     *
     * <p>{@code evenement} est remis a null AVANT l'appel, sans quoi
     * {@code reevaluer} sortirait aussitot — il ne touche pas a un rappel
     * FAIT.
     */
    public void rouvrir() {
        this.evenement = null;
        this.statut = StatutRappel.A_VENIR;
        reevaluer(LocalDate.now());
    }

    public void ignorer() {
        this.statut = StatutRappel.IGNORE;
    }

    /** L'unite sort du cheptel : le rappel n'a plus d'objet (audit G5). */
    public void ecarterALaSortie() {
        this.statut = StatutRappel.IGNORE;
        this.ecarteALaSortie = true;
    }

    /**
     * La sortie qui avait termine l'unite est annulee (audit du 29 octobre,
     * point 7) : le rappel revient, a venir ou echu selon sa date.
     */
    public void rouvrirApresSortieAnnulee() {
        if (!ecarteALaSortie) {
            return;
        }
        this.ecarteALaSortie = false;
        this.statut = StatutRappel.A_VENIR;
        reevaluer(LocalDate.now());
    }

    public boolean isEcarteALaSortie() { return ecarteALaSortie; }

    public UniteElevage getUnite() { return unite; }
    public CalendrierSanitaire getCalendrier() { return calendrier; }
    public String getLibelle() { return libelle; }
    public LocalDate getDateEcheance() { return dateEcheance; }
    public TypeEvenement getTypeEvenement() { return typeEvenement; }
    public StatutRappel getStatut() { return statut; }
    public EvenementSanitaire getEvenement() { return evenement; }
}
