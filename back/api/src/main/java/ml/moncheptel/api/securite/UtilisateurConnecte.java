package ml.moncheptel.api.securite;

import java.util.Collection;
import java.util.List;
import ml.moncheptel.api.exception.AccesRefuseException;
import ml.moncheptel.api.modele.Administrateur;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.Utilisateur;
import ml.moncheptel.api.modele.Veterinaire;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * L'utilisateur porte par la requete en cours.
 *
 * <h2>Pourquoi l'entite est embarquee, et ce que cela implique</h2>
 *
 * <p>Le filtre la charge une fois par requete, avec sa zone
 * ({@code UtilisateurRepository.chercherAvecZone}). Les controleurs y lisent
 * le nom, la zone, les filieres : de la lecture, sur des champs deja charges.
 *
 * <p><b>Cette entite est DETACHEE.</b> Elle a ete lue dans une transaction
 * refermee depuis. La modifier n'ecrirait rien : Hibernate ne surveille que
 * les entites attachees a une session ouverte, et
 * {@code spring.jpa.open-in-view} est a {@code false}. Tout service qui doit
 * la modifier la recharge par son identifiant, dans sa propre transaction —
 * c'est ce que fait {@code AuthentificationService.changerMotDePasse}.
 *
 * <p>Les accesseurs types ci-dessous remplacent les {@code instanceof}
 * disperses dans les controleurs. Ils levent 403 plutot que de rendre
 * {@code null} : un controleur qui demande l'eleveur alors que l'appelant est
 * veterinaire a un probleme de droits, pas une valeur absente a tester.
 */
public record UtilisateurConnecte(Utilisateur utilisateur) implements UserDetails {

    public Long id() {
        return utilisateur.getId();
    }

    public Eleveur eleveur() {
        if (utilisateur instanceof Eleveur e) {
            return e;
        }
        throw new AccesRefuseException("Cette action est réservée aux éleveurs");
    }

    public Veterinaire veterinaire() {
        if (utilisateur instanceof Veterinaire v) {
            return v;
        }
        throw new AccesRefuseException("Cette action est réservée aux vétérinaires");
    }

    public Administrateur administrateur() {
        if (utilisateur instanceof Administrateur a) {
            return a;
        }
        throw new AccesRefuseException("Cette action est réservée à l'administration");
    }

    /**
     * Le prefixe {@code ROLE_} est une convention de Spring Security :
     * {@code hasRole('ELEVEUR')} cherche l'autorite {@code ROLE_ELEVEUR}.
     * L'omettre fait echouer silencieusement toutes les regles d'acces —
     * elles refusent tout, ce qui se remarque, mais tard.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
    }

    @Override
    public String getPassword() {
        return utilisateur.getMotDePasse();
    }

    @Override
    public String getUsername() {
        return utilisateur.getEmail();
    }

    @Override
    public boolean isAccountNonLocked() {
        return !utilisateur.estVerrouille();
    }

    @Override
    public boolean isEnabled() {
        return utilisateur.isActif();
    }
}
