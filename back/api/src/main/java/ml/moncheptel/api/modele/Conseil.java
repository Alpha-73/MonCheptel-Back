package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.Length;
import ml.moncheptel.api.modele.enumeration.CategorieConseil;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutConseil;

/**
 * Conseil redige par un veterinaire, a destination des eleveurs.
 *
 * <p>La moderation est conservee. L'ecran de redaction l'annonce au
 * veterinaire : « Votre conseil sera visible par les eleveurs de la filiere
 * choisie. L'administrateur peut le retirer s'il ne respecte pas la charte. »
 *
 * <p><b>Un retrait exige toujours un motif.</b> Sans lui, le veterinaire ne
 * sait pas quoi corriger et le retrait devient arbitraire — c'est la regle que
 * porte {@link #retirer}, et la base la fait respecter aussi.
 */
@Entity
@Table(name = "conseil")
public class Conseil extends EntiteBase {

    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String titre;

    /**
     * Le corps du conseil, en texte long.
     *
     * <p><b>La longueur n'est pas decorative.</b> Avec {@code @Lob} seul,
     * Hibernate applique la longueur par defaut — 255 — et le dialecte MySQL
     * traduit « CLOB de 255 » par {@code tinytext}. Le demarrage echoue alors
     * sur :
     *
     * <pre>
     *   Schema-validation: wrong column type in column [contenu]
     *   found [longtext], but expecting [tinytext]
     * </pre>
     *
     * <p>{@link Length#LONG32} vaut 2 147 483 647 : le dialecte choisit alors
     * {@code longtext}, ce que declare la migration V1. Les deux cotes
     * s'accordent, et {@code ddl-auto: validate} passe.
     */
    @NotBlank
    @Lob
    @Column(nullable = false, length = Length.LONG32)
    private String contenu;

    /** Nulle = toutes filieres. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Filiere filiere;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CategorieConseil categorie;

    /** Nul pour un conseil publie directement par l'administration. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id")
    private Veterinaire auteur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private StatutConseil statut = StatutConseil.BROUILLON;

    @Column(name = "date_soumission")
    private Instant dateSoumission;

    @Column(name = "date_publication")
    private Instant datePublication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderateur_id")
    private Administrateur moderateur;

    @Size(max = 500)
    @Column(name = "motif_retrait", length = 500)
    private String motifRetrait;

    @Column(name = "nombre_lectures", nullable = false)
    private int nombreLectures = 0;

    protected Conseil() {
    }

    public Conseil(String titre, String contenu, Filiere filiere,
                   CategorieConseil categorie, Veterinaire auteur) {
        this.titre = titre;
        this.contenu = contenu;
        this.filiere = filiere;
        this.categorie = categorie;
        this.auteur = auteur;
    }

    /**
     * Conseil redige et publie par l'administration (ecran « Ajouter un
     * conseil ») : il ne passe pas par la moderation, puisque c'est elle qui
     * l'ecrit.
     */
    public static Conseil publieParAdministration(String titre, String contenu, Filiere filiere,
                                                  CategorieConseil categorie,
                                                  Administrateur moderateur) {
        Conseil conseil = new Conseil(titre, contenu, filiere, categorie, null);
        conseil.statut = StatutConseil.PUBLIE;
        conseil.datePublication = Instant.now();
        conseil.moderateur = moderateur;
        return conseil;
    }

    // ── cycle de moderation ─────────────────────────────────────────────────

    /**
     * Le veterinaire corrige un conseil deja publie : il quitte le fil des
     * eleveurs et repasse en moderation (ecran « Modifier le conseil »).
     */
    public void reviser() {
        if (statut == StatutConseil.PUBLIE) {
            this.statut = StatutConseil.EN_MODERATION;
            this.dateSoumission = Instant.now();
        }
    }

    /** Le veterinaire soumet : le conseil entre dans la file de moderation. */
    public void soumettre() {
        if (statut != StatutConseil.BROUILLON && statut != StatutConseil.RETIRE) {
            throw new IllegalStateException(
                    "Seul un brouillon ou un conseil retiré peut être soumis à la relecture.");
        }
        this.statut = StatutConseil.EN_MODERATION;
        this.dateSoumission = Instant.now();
        this.motifRetrait = null;
    }

    public void publier(Administrateur moderateur) {
        // Un conseil de l'administration (sans auteur veterinaire) retire par
        // erreur se republie : personne d'autre ne peut le resoumettre.
        boolean republication = statut == StatutConseil.RETIRE && auteur == null;
        if (statut != StatutConseil.EN_MODERATION && !republication) {
            throw new IllegalStateException(
                    "Seul un conseil en modération peut être publié.");
        }
        this.statut = StatutConseil.PUBLIE;
        this.datePublication = Instant.now();
        this.moderateur = moderateur;
    }

    /** Retrait par l'administration. Le motif est obligatoire. */
    public void retirer(Administrateur moderateur, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException(
                    "Indiquez un motif : il dit au vétérinaire quoi corriger.");
        }
        if (statut != StatutConseil.EN_MODERATION && statut != StatutConseil.PUBLIE) {
            throw new IllegalStateException(
                    "Seul un conseil en modération ou publié peut être retiré.");
        }
        this.statut = StatutConseil.RETIRE;
        this.moderateur = moderateur;
        this.motifRetrait = motif;
    }

    public void enregistrerLecture() {
        this.nombreLectures++;
    }

    public boolean visiblePar(Eleveur eleveur) {
        return statut.visiblePourEleveur()
                && (filiere == null || eleveur.pratique(filiere));
    }

    // ── accesseurs ──────────────────────────────────────────────────────────

    public String getTitre() { return titre; }
    public void setTitre(String t) { this.titre = t; }

    public String getContenu() { return contenu; }
    public void setContenu(String c) { this.contenu = c; }

    public Filiere getFiliere() { return filiere; }
    public void setFiliere(Filiere f) { this.filiere = f; }

    public CategorieConseil getCategorie() { return categorie; }
    public void setCategorie(CategorieConseil c) { this.categorie = c; }

    public Veterinaire getAuteur() { return auteur; }
    public StatutConseil getStatut() { return statut; }
    public Instant getDateSoumission() { return dateSoumission; }
    public Instant getDatePublication() { return datePublication; }
    public Administrateur getModerateur() { return moderateur; }
    public String getMotifRetrait() { return motifRetrait; }
    public int getNombreLectures() { return nombreLectures; }
}
