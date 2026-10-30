package ml.moncheptel.api.modele;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;

/**
 * Maladie du catalogue, administree depuis « Admin - Maladies catalogue ».
 *
 * <p>Une maladie peut toucher plusieurs filieres : la peste des petits
 * ruminants concerne les ovins ET les caprins, d'ou l'ensemble et non un champ
 * unique.
 *
 * <p>La regle la plus importante de cette classe tient dans
 * {@link #orienteVersVeterinaire} : une maladie grave n'affiche <b>aucun</b>
 * traitement a l'eleveur. L'ecran « Maladie - Fiche » le dit : « Gravite
 * elevee : aucun traitement n'est propose ici. L'application vous met
 * directement en relation avec un veterinaire competent de votre zone. »
 * Proposer un traitement pour le Newcastle serait une faute — la maladie se
 * propage trop vite pour etre traitee seul.
 */
@Entity
@Table(name = "maladie")
public class Maladie extends EntiteBase {

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, unique = true, length = 120)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private NiveauGravite gravite;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "maladie_filiere",
                     joinColumns = @JoinColumn(name = "maladie_id"))
    @Column(name = "filiere", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Set<Filiere> filieres = EnumSet.noneOf(Filiere.class);

    /** Resume lisible, affiche tel quel sous « SYMPTOMES PRINCIPAUX ». */
    @Size(max = 1000)
    @Column(name = "symptomes_texte", length = 1000)
    private String symptomesTexte;

    /**
     * Conduite a tenir, affichee a l'eleveur UNIQUEMENT si
     * {@link #orienteVersVeterinaire} est faux.
     */
    @Size(max = 2000)
    @Column(length = 2000)
    private String traitement;

    /**
     * Vrai : on n'affiche jamais de traitement, on oriente.
     * Mis a vrai pour toute maladie de gravite elevee.
     */
    @Column(name = "oriente_vers_veterinaire", nullable = false)
    private boolean orienteVersVeterinaire = false;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Nom du veterinaire qui a ajoute la maladie (V13, audit du 10 octobre) ;
     * nul pour le catalogue de reference et les ajouts de l'administration.
     * Texte et non cle etrangere : la trace survit a la suppression du
     * compte (V8).
     */
    @Size(max = 120)
    @Column(name = "ajoutee_par", length = 120)
    private String ajouteePar;

    @OneToMany(mappedBy = "maladie", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MaladieSymptome> symptomes = new LinkedHashSet<>();

    protected Maladie() {
    }

    public Maladie(String nom, NiveauGravite gravite, Set<Filiere> filieres) {
        this.nom = nom;
        this.gravite = gravite;
        this.filieres = filieres == null || filieres.isEmpty()
                ? EnumSet.noneOf(Filiere.class)
                : EnumSet.copyOf(filieres);
        // Une maladie grave oriente par defaut : c'est le comportement sur.
        this.orienteVersVeterinaire = gravite == NiveauGravite.ELEVE;
    }

    // ── comportement ────────────────────────────────────────────────────────

    public void associerSymptome(Symptome symptome, BigDecimal poids) {
        symptomes.removeIf(ms -> ms.getSymptome().equals(symptome));
        symptomes.add(new MaladieSymptome(this, symptome, poids));
    }

    public void dissocierSymptome(Symptome symptome) {
        symptomes.removeIf(ms -> ms.getSymptome().equals(symptome));
    }

    /**
     * Somme des poids de tous les symptomes de cette maladie. Denominateur du
     * calcul de couverture dans le moteur de diagnostic.
     */
    public BigDecimal poidsTotal() {
        return symptomes.stream()
                .map(MaladieSymptome::getPoids)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Ce qu'on affiche a l'eleveur. Vide si la maladie oriente : l'ecran
     * montre alors la conduite a tenir et le veterinaire recommande.
     */
    public String traitementAffichable() {
        return orienteVersVeterinaire ? null : traitement;
    }

    public boolean concerne(Filiere filiere) {
        return filieres.contains(filiere);
    }

    // ── accesseurs ──────────────────────────────────────────────────────────

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public NiveauGravite getGravite() { return gravite; }
    public void setGravite(NiveauGravite gravite) { this.gravite = gravite; }

    public Set<Filiere> getFilieres() { return filieres; }
    public void setFilieres(Set<Filiere> f) {
        this.filieres = f == null || f.isEmpty() ? EnumSet.noneOf(Filiere.class) : EnumSet.copyOf(f);
    }

    public String getSymptomesTexte() { return symptomesTexte; }
    public String getAjouteePar() { return ajouteePar; }
    public void setAjouteePar(String nom) { this.ajouteePar = nom; }
    public void setSymptomesTexte(String s) { this.symptomesTexte = s; }

    public String getTraitement() { return traitement; }
    public void setTraitement(String t) { this.traitement = t; }

    public boolean isOrienteVersVeterinaire() { return orienteVersVeterinaire; }
    public void setOrienteVersVeterinaire(boolean v) { this.orienteVersVeterinaire = v; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Set<MaladieSymptome> getSymptomes() { return symptomes; }
}
