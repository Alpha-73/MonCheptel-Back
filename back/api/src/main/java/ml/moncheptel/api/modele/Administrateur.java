package ml.moncheptel.api.modele;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import ml.moncheptel.api.modele.enumeration.Role;

/**
 * Administrateur de la plateforme.
 *
 * <p>Aucun attribut propre : la maquette ne lui en donne pas. La sous-classe
 * existe quand meme, pour que les trois roles se traitent de la meme facon —
 * une exception a l'heritage couterait plus cher a expliquer que la table
 * presque vide qu'elle evite.
 */
@Entity
@Table(name = "administrateur")
@DiscriminatorValue("ADMINISTRATEUR")
@PrimaryKeyJoinColumn(name = "utilisateur_id")
public class Administrateur extends Utilisateur {

    protected Administrateur() {
    }

    public Administrateur(String nom, String email, String motDePasse, Zone zone) {
        super(nom, email, motDePasse, zone);
    }

    @Override
    public Role getRole() {
        return Role.ADMINISTRATEUR;
    }
}
