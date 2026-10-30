package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/**
 * Competence d'un veterinaire sur une maladie.
 *
 * <p>Classe d'association entre {@link Veterinaire} et {@link Maladie}. Elle
 * porte l'orientation automatique : quand un signalement grave est etabli,
 * c'est ici qu'on cherche a qui l'adresser, en croisant la maladie probable et
 * la zone.
 *
 * <p>La contrainte d'unicite (veterinaire, maladie) empeche qu'un meme
 * praticien apparaisse deux fois pour la meme maladie et soit compte double
 * dans « Veterinaires competents : 3 ».
 */
@Entity
@Table(name = "competence",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_competence_veto_maladie",
               columnNames = {"veterinaire_id", "maladie_id"}))
public class Competence extends EntiteBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veterinaire_id", nullable = false)
    private Veterinaire veterinaire;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maladie_id", nullable = false)
    private Maladie maladie;

    @Column(name = "date_declaration", nullable = false)
    private LocalDate dateDeclaration = LocalDate.now();

    protected Competence() {
    }

    public Competence(Veterinaire veterinaire, Maladie maladie) {
        this.veterinaire = veterinaire;
        this.maladie = maladie;
    }

    public Veterinaire getVeterinaire() { return veterinaire; }

    public Maladie getMaladie() { return maladie; }

    public LocalDate getDateDeclaration() { return dateDeclaration; }
}
