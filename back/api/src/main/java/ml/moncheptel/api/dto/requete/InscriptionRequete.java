package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;

/**
 * Ecran « Creer un compte ». Ne cree qu'un ELEVEUR.
 *
 * <p>Les comptes veterinaire et administrateur ne s'ouvrent pas ici : c'est
 * l'administration qui les cree, et le veterinaire recoit un mot de passe
 * provisoire par courriel. Une inscription libre a ces roles laisserait
 * n'importe qui repondre a des signalements de maladie.
 */
public record InscriptionRequete(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 120)
        String nom,

        @NotBlank(message = "L'adresse e-mail est obligatoire")
        @Email(message = "Adresse e-mail invalide")
        @Size(max = 160)
        String email,

        /** Facultatif : contact direct, jamais identifiant. */
        @Size(max = 20)
        String telephone,

        @NotNull(message = "La zone est obligatoire")
        Long zoneId,

        @NotNull @Size(min = 1, message = "Indiquez au moins une filière")
        Set<Filiere> filieres,

        @NotBlank
        @Size(min = 8, max = 72,
              message = "Le mot de passe doit faire entre 8 et 72 caractères")
        String motDePasse) {
}
