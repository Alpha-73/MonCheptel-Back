package ml.moncheptel.api.modele;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Role;

/**
 * Veterinaire de zone.
 *
 * <p>Son compte n'est jamais cree par lui-meme : l'administration le cree et
 * lui envoie un mot de passe provisoire par courriel
 * (« Admin - Veterinaire creer »).
 *
 * <p>Ses <b>maladies traitees</b> ne sont pas decoratives — c'est la table qui
 * pilote l'orientation automatique : « Les eleveurs signalant ces maladies lui
 * seront orientes en priorite. »
 */
@Entity
@Table(name = "veterinaire")
@DiscriminatorValue("VETERINAIRE")
@PrimaryKeyJoinColumn(name = "utilisateur_id")
public class Veterinaire extends Utilisateur {

    /**
     * Zone d'intervention, distincte de la zone de residence heritee.
     * Un veterinaire base a Sikasso peut couvrir Koutiala.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_intervention_id")
    private Zone zoneIntervention;

    /** « Veterinaire de zone » sur la fiche. */
    @Size(max = 60)
    @Column(length = 60)
    private String titre;

    /** « Aviculture, pathologies volaille » dans l'annuaire. */
    @Size(max = 160)
    @Column(length = 160)
    private String specialite;

    @Column(nullable = false)
    private boolean disponible = true;

    /**
     * Les maladies que ce veterinaire traite, via la classe d'association
     * {@link Competence}. Une classe d'association et non un simple
     * {@code @ManyToMany} : le lien porte ses propres donnees (date de
     * declaration, niveau), et une table de jointure nue ne saurait pas les
     * accueillir le jour ou on en aura besoin.
     */
    @OneToMany(mappedBy = "veterinaire", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Competence> competences = new LinkedHashSet<>();

    protected Veterinaire() {
    }

    public Veterinaire(String nom, String email, String motDePasse,
                       Zone zone, Zone zoneIntervention) {
        super(nom, email, motDePasse, zone);
        this.zoneIntervention = zoneIntervention;
    }

    // ── comportement ────────────────────────────────────────────────────────

    /**
     * Declare que ce veterinaire traite cette maladie. Sans effet si elle est
     * deja declaree — l'ecran « Veto - Competences » est une liste a cocher,
     * et cocher deux fois ne doit pas creer deux lignes.
     */
    public void ajouterCompetence(Maladie maladie) {
        boolean dejaPresente = competences.stream()
                .anyMatch(c -> c.getMaladie().equals(maladie));
        if (!dejaPresente) {
            competences.add(new Competence(this, maladie));
        }
    }

    public void retirerCompetence(Maladie maladie) {
        competences.removeIf(c -> c.getMaladie().equals(maladie));
    }

    /** Vrai si ce veterinaire peut recevoir un signalement portant cette maladie. */
    public boolean traite(Maladie maladie) {
        return competences.stream().anyMatch(c -> c.getMaladie().equals(maladie));
    }

    /** Traite-t-il au moins une maladie de cette filiere ? */
    public boolean traiteFiliere(ml.moncheptel.api.modele.enumeration.Filiere filiere) {
        return competences.stream().anyMatch(c -> c.getMaladie().concerne(filiere));
    }

    @Override
    public Role getRole() {
        return Role.VETERINAIRE;
    }

    // ── accesseurs ──────────────────────────────────────────────────────────

    public Zone getZoneIntervention() { return zoneIntervention; }
    public void setZoneIntervention(Zone z) { this.zoneIntervention = z; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getSpecialite() { return specialite; }
    public void setSpecialite(String specialite) { this.specialite = specialite; }

    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }

    public Set<Competence> getCompetences() { return competences; }
}
