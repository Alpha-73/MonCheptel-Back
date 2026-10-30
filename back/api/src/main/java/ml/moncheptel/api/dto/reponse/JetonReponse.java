package ml.moncheptel.api.dto.reponse;

import ml.moncheptel.api.modele.enumeration.Role;

/**
 * Reponse de la connexion.
 *
 * <p>{@code doitChangerMotDePasse} pilote la redirection de l'application :
 * un veterinaire qui vient de recevoir son mot de passe provisoire est envoye
 * sur l'ecran de changement, pas sur son tableau de bord.
 */
public record JetonReponse(
        String jetonAcces,
        String jetonRafraichissement,
        long expireDansSecondes,
        Long utilisateurId,
        String nom,
        Role role,
        boolean doitChangerMotDePasse) {
}
