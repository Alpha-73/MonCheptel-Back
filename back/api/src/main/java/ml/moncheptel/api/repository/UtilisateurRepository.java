package ml.moncheptel.api.repository;

import java.util.Optional;
import ml.moncheptel.api.modele.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Acces aux comptes, tous roles confondus.
 *
 * <p>Requeter la classe de base suffit a l'authentification : l'ecran de
 * connexion demande l'e-mail sans savoir a quel role il a affaire.
 * Hibernate resout la sous-classe tout seul grace au discriminant.
 */
@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    /**
     * Point d'entree de l'authentification et de « mot de passe oublie ».
     *
     * <p>L'appelant passe l'adresse DEJA normalisee
     * ({@link Utilisateur#normaliserEmail(String)}) : la requete ne fait pas
     * de {@code lower()}, qui empecherait MySQL d'utiliser l'index unique.
     */
    @Query("""
           select u from Utilisateur u
           left join fetch u.zone
           where u.email = :email
           """)
    Optional<Utilisateur> chercherParEmail(@Param("email") String email);

    /**
     * Charge l'utilisateur AVEC sa zone, pour le filtre d'authentification.
     *
     * <p>{@code open-in-view} est desactive : une fois la transaction fermee,
     * l'entite est detachee et tout acces a une association paresseuse leve
     * une {@code LazyInitializationException}. Le filtre lit cette entite a
     * chaque requete et les ecrans affichent « Balla Kone — Koutiala » : la
     * zone doit donc etre chargee ici, une bonne fois.
     */
    @Query("select u from Utilisateur u left join fetch u.zone where u.id = :id")
    Optional<Utilisateur> chercherAvecZone(@Param("id") Long id);

    boolean existsByEmail(String email);

    /** Compteur du tableau de bord de l'administration. */
    @Query("select count(u) from Utilisateur u where u.actif = true")
    long compterComptesActifs();
}
