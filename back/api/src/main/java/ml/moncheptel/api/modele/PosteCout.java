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
import java.math.BigDecimal;
import java.math.RoundingMode;
import ml.moncheptel.api.modele.enumeration.ModeQuantite;
import ml.moncheptel.api.modele.enumeration.PhaseCout;

/**
 * Poste de cout unitaire d'un type d'elevage.
 *
 * <p><b>Un prix, jamais un total.</b> L'administrateur saisit « le kilo
 * d'aliment coute 345 F », pas « l'aliment coute 18 975 F » : c'est ce qui
 * permet a l'application de chiffrer n'importe quel effectif, et de tout
 * recalculer quand un seul prix bouge. L'ecran le dit lui-meme de son apercu :
 * « Calcule, non saisi. »
 */
@Entity
@Table(name = "poste_cout")
public class PosteCout extends EntiteBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_elevage_id", nullable = false)
    private TypeElevage typeElevage;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String libelle;

    /** En FCFA. Le franc CFA n'a pas de subdivision : un entier suffit et ne perd rien. */
    @PositiveOrZero
    @Column(nullable = false)
    private long montant;

    /** m2, kg, sujet, forfait — pour l'affichage seulement. */
    @Size(max = 20)
    @Column(length = 20)
    private String unite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private PhaseCout phase;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_quantite", nullable = false, length = 14)
    private ModeQuantite modeQuantite;

    /** Quantite par unite de base : 0,1 m2 par sujet, 1,667 kg par sujet. */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal coefficient = BigDecimal.ONE;

    @Column(nullable = false)
    private int ordre = 0;

    protected PosteCout() {
    }

    public PosteCout(TypeElevage typeElevage, String libelle, long montant, String unite,
                     PhaseCout phase, ModeQuantite modeQuantite, BigDecimal coefficient, int ordre) {
        this.typeElevage = typeElevage;
        this.libelle = libelle;
        this.montant = montant;
        this.unite = unite;
        this.phase = phase;
        this.modeQuantite = modeQuantite;
        this.coefficient = coefficient;
        this.ordre = ordre;
    }

    /**
     * Cout de ce poste pour un effectif donne.
     *
     * @param sujetsAchetes effectif reellement achete, mortalite comprise
     * @param capacite      capacite de reference de l'installation
     */
    /**
     * Nombre d'installations de {@code capacite} places pour loger
     * {@code sujetsAchetes} sujets : au moins une (audit eleveur L10). Un poste
     * PAR_CAPACITE ne dependait jamais de l'effectif : 2 000 poulets
     * tenaient dans un poulailler de 10 m2.
     */
    public static int batiments(int sujetsAchetes, int capacite) {
        if (capacite <= 0 || sujetsAchetes <= capacite) {
            return 1;
        }
        return (sujetsAchetes + capacite - 1) / capacite;
    }

    public long calculer(int sujetsAchetes, int capacite) {
        BigDecimal base = switch (modeQuantite) {
            case PAR_SUJET -> BigDecimal.valueOf(sujetsAchetes);
            case PAR_CAPACITE -> BigDecimal.valueOf((long) capacite * batiments(sujetsAchetes, capacite));
            case FORFAIT -> BigDecimal.ONE;
        };
        return base.multiply(coefficient)
                .multiply(BigDecimal.valueOf(montant))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public TypeElevage getTypeElevage() { return typeElevage; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String l) { this.libelle = l; }

    public long getMontant() { return montant; }
    public void setMontant(long m) { this.montant = m; }

    public String getUnite() { return unite; }
    public void setUnite(String u) { this.unite = u; }

    public PhaseCout getPhase() { return phase; }
    public void setPhase(PhaseCout p) { this.phase = p; }

    public ModeQuantite getModeQuantite() { return modeQuantite; }
    public void setModeQuantite(ModeQuantite m) { this.modeQuantite = m; }

    public BigDecimal getCoefficient() { return coefficient; }
    public void setCoefficient(BigDecimal c) { this.coefficient = c; }

    public int getOrdre() { return ordre; }
    public void setOrdre(int o) { this.ordre = o; }
}
