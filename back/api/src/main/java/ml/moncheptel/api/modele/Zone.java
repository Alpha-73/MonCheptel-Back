package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Zone geographique : Koutiala, Sikasso, Segou, Mopti, Koulikoro.
 *
 * <p>Une table et non une enumeration : l'administration doit pouvoir en
 * ajouter sans redeployer. C'est la maille de ciblage des alertes
 * — « 318 eleveurs de volaille a Koutiala ».
 */
@Entity
@Table(name = "zone")
public class Zone extends EntiteBase {

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, unique = true, length = 80)
    private String nom;

    @Size(max = 80)
    @Column(length = 80)
    private String region;

    protected Zone() {
        // requis par JPA
    }

    public Zone(String nom, String region) {
        this.nom = nom;
        this.region = region;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
