package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.TypeEvenement;

/**
 * Acte sanitaire porte au carnet d'une unite.
 *
 * <p>Les quatre types de la liste deroulante, au-dessus du separateur
 * « SORTIE DU CHEPTEL » : vaccination, traitement, maladie, consultation.
 *
 * <p>Ne touche jamais a l'effectif. Un evenement sanitaire peut en revanche
 * solder un {@link RappelSanitaire} : enregistrer la vaccination Newcastle J7
 * fait disparaitre le rappel correspondant du tableau de bord.
 */
@Entity
@Table(name = "evenement_sanitaire")
public class EvenementSanitaire extends EntiteBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unite_id", nullable = false)
    private UniteElevage unite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TypeEvenement type;

    @Column(name = "date_evenement", nullable = false)
    private LocalDate dateEvenement;

    /**
     * « Produit et observation » a l'ecran — un seul champ.
     * « Ex : vaccin Newcastle HB1, dose de rappel ».
     */
    @Size(max = 500)
    @Column(name = "produit_observation", length = 500)
    private String produitObservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id")
    private Utilisateur auteur;

    /** Rempli si l'acte a ete pose par un veterinaire plutot que par l'eleveur. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinaire_id")
    private Veterinaire veterinaire;

    protected EvenementSanitaire() {
    }

    public EvenementSanitaire(UniteElevage unite, TypeEvenement type, LocalDate dateEvenement,
                              String produitObservation, Utilisateur auteur) {
        this.unite = unite;
        this.type = type;
        this.dateEvenement = dateEvenement;
        this.produitObservation = produitObservation;
        this.auteur = auteur;
    }

    public UniteElevage getUnite() { return unite; }

    public TypeEvenement getType() { return type; }
    public void setType(TypeEvenement t) { this.type = t; }

    public LocalDate getDateEvenement() { return dateEvenement; }
    public void setDateEvenement(LocalDate d) { this.dateEvenement = d; }

    public String getProduitObservation() { return produitObservation; }
    public void setProduitObservation(String p) { this.produitObservation = p; }

    public Utilisateur getAuteur() { return auteur; }

    public Veterinaire getVeterinaire() { return veterinaire; }
    public void setVeterinaire(Veterinaire v) { this.veterinaire = v; }
}
