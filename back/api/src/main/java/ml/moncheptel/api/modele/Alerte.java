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
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutAlerte;

/**
 * Alerte sanitaire de zone, declenchee par l'administration.
 *
 * <p>« Foyer de maladie de Newcastle confirme sur Koutiala » — notifiee
 * immediatement a tous les eleveurs de la zone qui pratiquent la filiere
 * concernee, par notification push.
 *
 * <p>La <b>portee est figee a l'envoi</b>. Recalculer « 318 eleveurs » six
 * mois plus tard donnerait un autre chiffre et rendrait l'historique faux :
 * l'ecran d'administration affiche ce qui a ete envoye, pas ce qui serait
 * envoye aujourd'hui.
 */
@Entity
@Table(name = "alerte")
public class Alerte extends EntiteBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maladie_id", nullable = false)
    private Maladie maladie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    /** Nulle = toutes filieres de la zone. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Filiere filiere;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String message;

    /** Nombre d'eleveurs notifies, fige a l'envoi. */
    @PositiveOrZero
    @Column(nullable = false)
    private int portee;

    @Column(name = "date_declenchement", nullable = false)
    private Instant dateDeclenchement = Instant.now();

    @Column(name = "date_cloture")
    private Instant dateCloture;

    /**
     * Dernier jour de l'alerte, facultatif. Le lendemain, la tache
     * planifiee la clot comme le ferait l'administration.
     */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatutAlerte statut = StatutAlerte.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "administrateur_id", nullable = false)
    private Administrateur administrateur;

    protected Alerte() {
    }

    public Alerte(Maladie maladie, Zone zone, Filiere filiere, String message,
                  int portee, Administrateur administrateur) {
        this.maladie = maladie;
        this.zone = zone;
        this.filiere = filiere;
        this.message = message;
        this.portee = portee;
        this.administrateur = administrateur;
    }

    public void cloturer() {
        if (statut == StatutAlerte.TERMINEE) {
            throw new IllegalStateException("Cette alerte est déjà clôturée.");
        }
        this.statut = StatutAlerte.TERMINEE;
        this.dateCloture = Instant.now();
    }

    /** Vrai si cet eleveur fait partie de la cible. */
    public boolean cible(Eleveur eleveur) {
        boolean memeZone = eleveur.getZone() != null && eleveur.getZone().equals(zone);
        boolean bonneFiliere = filiere == null || eleveur.pratique(filiere);
        return memeZone && bonneFiliere && eleveur.isActif();
    }

    /** Vrai si la date de fin est passee : l'alerte attend sa cloture. */
    public boolean estEchue(LocalDate aujourdhui) {
        return statut == StatutAlerte.ACTIVE && dateFin != null && dateFin.isBefore(aujourdhui);
    }

    public Maladie getMaladie() { return maladie; }
    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }
    public Zone getZone() { return zone; }
    public Filiere getFiliere() { return filiere; }
    public String getMessage() { return message; }
    public void setMessage(String m) { this.message = m; }
    public int getPortee() { return portee; }
    public Instant getDateDeclenchement() { return dateDeclenchement; }
    public Instant getDateCloture() { return dateCloture; }
    public StatutAlerte getStatut() { return statut; }
    public Administrateur getAdministrateur() { return administrateur; }
}
