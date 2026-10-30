package ml.moncheptel.api.modele;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import ml.moncheptel.api.modele.enumeration.TypeMouvement;

/**
 * Ce qu'un eleveur possede et suit : un lot de volaille, ou une bete.
 *
 * <p><b>Pourquoi cette abstraction.</b> Un evenement sanitaire, une operation
 * financiere, un signalement visent tantot un lot, tantot un animal. Sans
 * classe commune, chacune de ces tables porterait deux cles etrangeres
 * nullables et une contrainte « une seule des deux » — que MySQL refuse par
 * ailleurs de combiner avec une action referentielle. Avec elle : une cle,
 * une contrainte, un vrai lien.
 *
 * <p><b>Le statut ne se saisit jamais.</b> Il decoule des mouvements. L'ecran
 * « Historique - LOT-014 » le dit : « Le statut ne se modifie pas a la main.
 * Il suit les sorties enregistrees. » La methode {@link #retirer} est le seul
 * chemin qui le change, et elle vit ici plutot que dans un service pour qu'on
 * ne puisse pas decrementer un effectif ailleurs dans le code.
 */
@Entity
@Table(name = "unite_elevage",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_unite_proprietaire_code",
               columnNames = {"proprietaire_id", "code"}))
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "mode_suivi", discriminatorType = DiscriminatorType.STRING, length = 12)
public abstract class UniteElevage extends EntiteBase {

    /**
     * « LOT-014 », « BOV-047 ». Unique CHEZ SON PROPRIETAIRE, pas sur la
     * plateforme.
     *
     * <p>Une unicite globale paraissait plus simple. Elle aurait fait refuser
     * « LOT-001 » au deuxieme eleveur qui ouvre un compte, pour un conflit
     * avec un lot qu'il ne voit pas et sur lequel il ne peut rien. Le code est
     * une reference de terrain — ce que l'eleveur ecrit a la craie sur son
     * poulailler — pas un identifiant technique : c'est {@code id} qui joue ce
     * role.
     *
     * <p>La contrainte composee est portee par la table (voir
     * {@code uk_unite_proprietaire_code}), pas par la colonne.
     */
    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proprietaire_id", nullable = false)
    private Eleveur proprietaire;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_elevage_id", nullable = false)
    private TypeElevage typeElevage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StatutUnite statut = StatutUnite.ACTIF;

    /** Mise en place d'un lot, ou entree au cheptel d'un animal. */
    @Column(name = "date_entree", nullable = false)
    private LocalDate dateEntree;

    /** Renseignee au dernier mouvement de sortie. Nulle tant que l'unite est active. */
    @Column(name = "date_sortie")
    private LocalDate dateSortie;

    @Size(max = 500)
    @Column(length = 500)
    private String note;

    /**
     * Le journal, ordonne. C'est LUI la verite de l'effectif ; le compteur des
     * sous-classes n'en est qu'un cache, verifie par un test.
     */
    @OneToMany(mappedBy = "unite", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dateMouvement ASC, id ASC")
    private List<MouvementCheptel> mouvements = new ArrayList<>();

    protected UniteElevage() {
    }

    protected UniteElevage(String code, Eleveur proprietaire, TypeElevage typeElevage,
                           LocalDate dateEntree) {
        this.code = code;
        this.proprietaire = proprietaire;
        this.typeElevage = typeElevage;
        this.dateEntree = dateEntree;
    }

    // ── contrat des sous-classes ────────────────────────────────────────────

    /** Nombre de tetes encore presentes. Toujours 1 ou 0 pour un animal. */
    public abstract int getEffectifRestant();

    /** Effectif a l'entree. Toujours 1 pour un animal. */
    public abstract int getEffectifInitial();

    /** Applique le retrait a l'effectif propre a la sous-classe. */
    protected abstract void appliquerRetrait(int nombreTetes);

    /**
     * Statut a prendre quand l'unite est entierement sortie.
     * Un lot devient CLOTURE quelle que soit la raison ; un animal prend
     * l'etat de sa sortie — vendu, mort ou transfere.
     */
    protected abstract StatutUnite statutApresSortieTotale(TypeMouvement type);

    // ── la regle metier centrale ────────────────────────────────────────────

    /**
     * Retire des tetes du cheptel et met le statut a jour.
     *
     * <p>Appelee dans la meme transaction que l'enregistrement du mouvement.
     * C'est le seul endroit du code ou un effectif diminue.
     *
     * @throws IllegalStateException    si l'unite est deja sortie
     * @throws IllegalArgumentException si le nombre demande depasse l'effectif
     */
    public void retirer(int nombreTetes, TypeMouvement type, LocalDate date) {
        if (!type.estSortie()) {
            throw new IllegalArgumentException(
                    "Seul un mouvement de sortie retire des têtes : " + type.getLibelle());
        }
        if (statut.estSortie()) {
            throw new IllegalStateException(
                    code + " est déjà sorti du cheptel (" + statut.getLibelle().toLowerCase() + ").");
        }
        if (nombreTetes <= 0) {
            throw new IllegalArgumentException("Le nombre de têtes doit être supérieur à zéro.");
        }
        if (nombreTetes > getEffectifRestant()) {
            throw new IllegalArgumentException(
                    code + " ne compte que " + tetes(getEffectifRestant())
                            + " : impossible d'en retirer " + nombreTetes + ".");
        }

        appliquerRetrait(nombreTetes);

        if (getEffectifRestant() == 0) {
            this.statut = statutApresSortieTotale(type);
            this.dateSortie = date;
        }
    }

    /**
     * Annule un retrait : la sortie a ete saisie par erreur (audit F1). Les
     * tetes reviennent, et une unite que cette sortie avait fait sortir du
     * cheptel redevient active.
     */
    public void restituer(int nombreTetes) {
        if (nombreTetes <= 0) {
            throw new IllegalArgumentException("Le nombre de têtes doit être supérieur à zéro.");
        }
        if (getEffectifRestant() + nombreTetes > getEffectifInitial()) {
            throw new IllegalArgumentException(
                    "Annuler cette sortie porterait " + code + " au-delà de son effectif d'entrée.");
        }
        // L'unite ne redevient active que si c'est CETTE sortie qui l'avait
        // fait sortir, c'est-a-dire si elle etait a zero tete. Une unite
        // cloturee a la main avec des tetes restantes reste cloturee : annuler
        // une vente anterieure ne revient pas sur la decision de l'eleveur.
        boolean termineeParLaSortie = statut.estSortie() && getEffectifRestant() == 0;
        appliquerRetour(nombreTetes);
        if (termineeParLaSortie) {
            this.statut = StatutUnite.ACTIF;
            this.dateSortie = null;
        }
    }

    /** Remet des tetes a l'effectif propre a la sous-classe. */
    protected abstract void appliquerRetour(int nombreTetes);

    /**
     * Termine l'elevage a la main : fin de cycle, animal reforme, lot
     * abandonne (audit F2). Les tetes eventuellement restantes ne sont pas
     * retirees du journal — rien ne dit ce qu'elles sont devenues — mais
     * l'unite quitte les listes actives et son dossier se ferme.
     */
    public void terminer(LocalDate date) {
        if (statut.estSortie()) {
            throw new IllegalStateException(statut == StatutUnite.CLOTURE
                    ? code + " est déjà clôturé."
                    : code + " est déjà sorti du cheptel (" + statut.getLibelle().toLowerCase() + ").");
        }
        this.statut = StatutUnite.CLOTURE;
        this.dateSortie = date;
    }

    /** « 1 tête », « 30 têtes ». */
    public static String tetes(int n) {
        return n + (n > 1 ? " têtes" : " tête");
    }

    /** Ajoute un mouvement au journal en maintenant les deux cotes du lien. */
    public void ajouterMouvement(MouvementCheptel mouvement) {
        mouvements.add(mouvement);
    }

    /**
     * Effectif recalcule depuis le journal. Sert au test de coherence : cette
     * valeur et {@link #getEffectifRestant()} doivent toujours coincider.
     */
    public int effectifSelonJournal() {
        return mouvements.stream()
                .mapToInt(m -> m.getType().estSortie() ? -m.getNombreTetes() : m.getNombreTetes())
                .sum();
    }

    public Filiere getFiliere() {
        return typeElevage.getFiliere();
    }

    // ── accesseurs ──────────────────────────────────────────────────────────

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Eleveur getProprietaire() { return proprietaire; }

    public TypeElevage getTypeElevage() { return typeElevage; }
    public void setTypeElevage(TypeElevage t) { this.typeElevage = t; }

    public StatutUnite getStatut() { return statut; }
    protected void setStatut(StatutUnite s) { this.statut = s; }

    public LocalDate getDateEntree() { return dateEntree; }
    public void setDateEntree(LocalDate d) { this.dateEntree = d; }

    public LocalDate getDateSortie() { return dateSortie; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public List<MouvementCheptel> getMouvements() { return mouvements; }
}
