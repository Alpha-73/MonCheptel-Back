package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import ml.moncheptel.api.modele.enumeration.TypeMouvement;

/**
 * Bande de volaille — « LOT-014, 180 tetes sur 185 ».
 *
 * <p>Un lot ne devient jamais « vendu » : il peut etre ecoule en trois ventes,
 * avec des morts entre-temps. Il est actif, puis cloture. C'est pourquoi
 * {@link #statutApresSortieTotale} ignore le type du dernier mouvement.
 */
@Entity
@Table(name = "lot")
@DiscriminatorValue("LOT")
@PrimaryKeyJoinColumn(name = "unite_id")
public class Lot extends UniteElevage {

    @Positive
    @Column(name = "effectif_initial", nullable = false)
    private int effectifInitial;

    /**
     * Cache de lecture. La verite est le journal des mouvements ; ce compteur
     * evite de sommer le journal a chaque affichage de liste.
     */
    @PositiveOrZero
    @Column(name = "effectif_restant", nullable = false)
    private int effectifRestant;

    /**
     * Echeance du cycle, calculee a la mise en place.
     *
     * <p>Valeur derivee mais stockee, pour deux raisons. Elle est affichee
     * telle quelle — « Cloture du cycle le 18 oct. 2026 » — et surtout elle
     * doit etre INTERROGEABLE : sans colonne, chercher les cycles qui se
     * terminent bientot demanderait une fonction de date propre a MySQL dans
     * du JPQL, qui ne passerait pas en test sur H2.
     *
     * <p>Nulle pour un elevage sans cycle.
     */
    @Column(name = "date_cloture_prevue")
    private LocalDate dateCloturePrevue;

    protected Lot() {
    }

    public Lot(String code, Eleveur proprietaire, TypeElevage typeElevage,
               int effectifInitial, LocalDate dateMiseEnPlace) {
        super(code, proprietaire, typeElevage, dateMiseEnPlace);
        if (effectifInitial <= 0) {
            throw new IllegalArgumentException("Un lot démarre avec au moins une tête.");
        }
        this.effectifInitial = effectifInitial;
        this.effectifRestant = effectifInitial;
        this.dateCloturePrevue = typeElevage.clotureDuCycle(dateMiseEnPlace);
    }

    @Override
    public int getEffectifRestant() {
        return effectifRestant;
    }

    @Override
    public int getEffectifInitial() {
        return effectifInitial;
    }

    @Override
    protected void appliquerRetrait(int nombreTetes) {
        this.effectifRestant -= nombreTetes;
    }

    @Override
    protected void appliquerRetour(int nombreTetes) {
        this.effectifRestant += nombreTetes;
    }

    @Override
    protected StatutUnite statutApresSortieTotale(TypeMouvement type) {
        return StatutUnite.CLOTURE;
    }

    public LocalDate getDateCloturePrevue() {
        return dateCloturePrevue;
    }

    /**
     * Recalcule l'echeance. A appeler si le type d'elevage ou la date de mise
     * en place change — c'est ce qu'annonce l'ecran de modification : « Le
     * modifier recalcule la periode en cours. »
     */
    public void recalculerCloture() {
        this.dateCloturePrevue = getTypeElevage().clotureDuCycle(getDateEntree());
    }

    /** Jour courant du cycle — « jour 16 sur 45 ». */
    public long jourDuCycle(LocalDate aujourdHui) {
        return java.time.temporal.ChronoUnit.DAYS.between(getDateEntree(), aujourdHui);
    }

    public int getTetesSorties() {
        return effectifInitial - effectifRestant;
    }

    public void setEffectifInitial(int e) { this.effectifInitial = e; }
}
