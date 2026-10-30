package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.Sexe;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import ml.moncheptel.api.modele.enumeration.TypeMouvement;

/**
 * Bete suivie individuellement — « BOV-047, Zebu, femelle, nee le 10 janv. 2022 ».
 *
 * <p>Son effectif vaut toujours 1 ou 0. Contrairement au lot, son statut final
 * retient la RAISON de la sortie : vendu, mort ou transfere. C'est ce que
 * l'ecran « MesAnimaux » affiche en pastille.
 */
@Entity
@Table(name = "animal")
@DiscriminatorValue("INDIVIDUEL")
@PrimaryKeyJoinColumn(name = "unite_id")
public class Animal extends UniteElevage {

    @Size(max = 60)
    @Column(length = 60)
    private String race;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Sexe sexe;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    /** Present dans le cheptel : 1, sorti : 0. */
    @Column(nullable = false)
    private int present = 1;

    protected Animal() {
    }

    public Animal(String code, Eleveur proprietaire, TypeElevage typeElevage,
                  LocalDate dateEntree, String race, Sexe sexe, LocalDate dateNaissance) {
        super(code, proprietaire, typeElevage, dateEntree);
        this.race = race;
        this.sexe = sexe;
        this.dateNaissance = dateNaissance;
    }

    @Override
    public int getEffectifRestant() {
        return present;
    }

    @Override
    public int getEffectifInitial() {
        return 1;
    }

    @Override
    protected void appliquerRetrait(int nombreTetes) {
        // retirer() a deja verifie que nombreTetes vaut au plus l'effectif, soit 1
        this.present = 0;
    }

    @Override
    protected void appliquerRetour(int nombreTetes) {
        this.present = 1;
    }

    @Override
    protected StatutUnite statutApresSortieTotale(TypeMouvement type) {
        return type.statutResultant();
    }

    /** Age en mois, pour l'affichage de la fiche. Nul si la naissance est inconnue. */
    public Integer ageEnMois(LocalDate aujourdHui) {
        return dateNaissance == null
                ? null
                : (int) java.time.temporal.ChronoUnit.MONTHS.between(dateNaissance, aujourdHui);
    }

    public String getRace() { return race; }
    public void setRace(String race) { this.race = race; }

    public Sexe getSexe() { return sexe; }
    public void setSexe(Sexe sexe) { this.sexe = sexe; }

    public LocalDate getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(LocalDate d) { this.dateNaissance = d; }
}
