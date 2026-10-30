package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Signe observable par un eleveur.
 *
 * <p>Les dix puces de l'ecran « Signalement - Symptomes » : toux, diarrhee
 * verdatre, diarrhee avec sang, torticolis, abattement, chute de ponte, morts
 * subites, fievre, boiterie, perte d'appetit.
 *
 * <p>Le libelle est ecrit pour un eleveur, pas pour un veterinaire. L'ecran le
 * dit : « Touchez tout ce que vous observez. Vous n'avez pas a nommer la
 * maladie. » C'est tout le principe du signalement par symptomes.
 */
@Entity
@Table(name = "symptome")
public class Symptome extends EntiteBase {

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, unique = true, length = 80)
    private String libelle;

    /** Ordre d'affichage dans la liste a cocher. */
    @Column(nullable = false)
    private int ordre = 0;

    protected Symptome() {
    }

    public Symptome(String libelle, int ordre) {
        this.libelle = libelle;
        this.ordre = ordre;
    }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }
}
