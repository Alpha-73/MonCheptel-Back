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
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.TypeEvenement;

/**
 * Protocole sanitaire d'un type d'elevage : quel acte, a quel jour du cycle.
 *
 * <p>« Vaccins obligatoires : Newcastle J7 et J21 » devient deux lignes de
 * calendrier. A la creation d'un lot, chacune engendre un
 * {@link RappelSanitaire} date — c'est ce qui produit l'encart
 * « Rappel vaccination — LOT-014, echeance dans 3 jours ».
 *
 * <p>Un modele plutot qu'un texte libre : sans lui, le rappel serait a saisir
 * a la main pour chaque lot, et personne ne le ferait.
 */
@Entity
@Table(name = "calendrier_sanitaire")
public class CalendrierSanitaire extends EntiteBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_elevage_id", nullable = false)
    private TypeElevage typeElevage;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_evenement", nullable = false, length = 16)
    private TypeEvenement typeEvenement = TypeEvenement.VACCINATION;

    /** Jour du cycle : 7 pour « Newcastle J7 ». 0 = le jour de la mise en place. */
    @PositiveOrZero
    @Column(name = "jour_du_cycle", nullable = false)
    private int jourDuCycle;

    /** Faux pour un acte recommande mais non obligatoire. */
    @Column(nullable = false)
    private boolean obligatoire = true;

    @Column(nullable = false)
    private int ordre = 0;

    protected CalendrierSanitaire() {
    }

    public CalendrierSanitaire(TypeElevage typeElevage, String libelle,
                               TypeEvenement typeEvenement, int jourDuCycle, int ordre) {
        this.typeElevage = typeElevage;
        this.libelle = libelle;
        this.typeEvenement = typeEvenement;
        this.jourDuCycle = jourDuCycle;
        this.ordre = ordre;
    }

    /** Echeance de cet acte pour une unite entree a cette date. */
    public LocalDate echeancePour(LocalDate dateEntree) {
        return dateEntree.plusDays(jourDuCycle);
    }

    public TypeElevage getTypeElevage() { return typeElevage; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String l) { this.libelle = l; }
    public TypeEvenement getTypeEvenement() { return typeEvenement; }
    public void setTypeEvenement(TypeEvenement t) { this.typeEvenement = t; }
    public int getJourDuCycle() { return jourDuCycle; }
    public void setJourDuCycle(int j) { this.jourDuCycle = j; }
    public boolean isObligatoire() { return obligatoire; }
    public void setObligatoire(boolean o) { this.obligatoire = o; }
    public int getOrdre() { return ordre; }
    public void setOrdre(int o) { this.ordre = o; }
}
