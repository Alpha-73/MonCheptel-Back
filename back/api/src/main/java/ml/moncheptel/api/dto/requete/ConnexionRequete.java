package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ecran « Connexion ».
 *
 * <p>Le mot de passe n'a pas de contrainte de longueur MINIMALE ICI : a la connexion,
 * refuser une saisie trop courte avant de la verifier revient a dire a un
 * attaquant que le mot de passe recherche fait au moins 8 caracteres. La
 * contrainte est sur {@link InscriptionRequete}, ou elle a un sens.
 */
public record ConnexionRequete(
        @NotBlank(message = "L'adresse e-mail est obligatoire")
        String email,

        // Seule borne : BCrypt ne lit que 72 octets et leve au-dela, ce qui
        // sortait en 500 (audit global G16). Elle ne dit rien d'un vrai mot
        // de passe, qui en fait toujours moins.
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(max = 72, message = "Mot de passe trop long")
        String motDePasse) {
}
