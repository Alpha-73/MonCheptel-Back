package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ecran « Admin - Ajouter un veterinaire ».
 *
 * <p>Pas de mot de passe : l'administration n'en choisit pas un. Le service en
 * genere un provisoire, l'envoie par courriel et pose
 * {@code doitChangerMotDePasse}. Un mot de passe saisi par un tiers est un mot
 * de passe connu d'un tiers.
 *
 * <p>Le courriel est l'identifiant de connexion, et le mot de passe
 * provisoire y est envoye. Le telephone reste obligatoire ICI seulement :
 * c'est le contact que l'annuaire affiche aux eleveurs (« appeler le
 * veterinaire »). Pour un eleveur, il est facultatif.
 */
public record VeterinaireRequete(
        @NotBlank @Size(max = 120) String nom,
        @NotBlank @Size(max = 20) String telephone,
        @NotBlank @Email @Size(max = 160) String email,
        @NotNull Long zoneId,
        @NotNull Long zoneInterventionId,
        @Size(max = 60) String titre,
        @Size(max = 120) String specialite) {
}
