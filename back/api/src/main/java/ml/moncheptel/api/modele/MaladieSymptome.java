package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/**
 * Lien pondere entre une maladie et un symptome.
 *
 * <p>Le poids dit a quel point le signe est <b>caracteristique</b> de la
 * maladie, pas a quel point il est grave. Le torticolis vaut 1,00 pour le
 * Newcastle — quand on le voit, on ne se trompe guere ; la toux vaut 0,40,
 * parce qu'on la voit partout.
 *
 * <p>C'est ce poids, et lui seul, qui permet de departager deux hypotheses
 * quand les symptomes sont banals. Sans lui, « toux » seule donne bronchite et
 * Newcastle a 50/50, et l'application ne sert a rien.
 *
 * <p><b>Ces poids doivent etre valides par un veterinaire avant la
 * soutenance.</b> Ils ne sont pas issus d'une source publiee.
 */
@Entity
@Table(name = "maladie_symptome",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_maladie_symptome",
               columnNames = {"maladie_id", "symptome_id"}))
public class MaladieSymptome extends EntiteBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maladie_id", nullable = false)
    private Maladie maladie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "symptome_id", nullable = false)
    private Symptome symptome;

    @DecimalMin("0.01")
    @DecimalMax("1.00")
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal poids;

    protected MaladieSymptome() {
    }

    public MaladieSymptome(Maladie maladie, Symptome symptome, BigDecimal poids) {
        this.maladie = maladie;
        this.symptome = symptome;
        this.poids = poids;
    }

    public Maladie getMaladie() { return maladie; }

    public Symptome getSymptome() { return symptome; }

    public BigDecimal getPoids() { return poids; }
    public void setPoids(BigDecimal poids) { this.poids = poids; }
}
