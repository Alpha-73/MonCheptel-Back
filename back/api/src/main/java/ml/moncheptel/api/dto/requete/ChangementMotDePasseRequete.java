package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ecran « Mon profil - Changer le mot de passe ».
 *
 * <p>L'ancien mot de passe est exige meme si l'utilisateur est deja
 * authentifie : un telephone laisse deverrouille suffirait sinon a changer le
 * mot de passe et a verrouiller le proprietaire hors de son propre compte.
 *
 * <p>Borne haute a 72 : BCrypt ignore silencieusement les octets au-dela. Sans
 * cette limite, deux mots de passe differents sur les 100 premiers caracteres
 * ouvriraient le meme compte.
 */
public record ChangementMotDePasseRequete(
        @NotBlank String ancienMotDePasse,
        @NotBlank @Size(min = 8, max = 72) String nouveauMotDePasse) {
}
