package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Enregistrement du jeton Firebase, envoye par l'application au demarrage puis
 * a chaque renouvellement.
 *
 * <p>{@code plateforme} est une chaine libre, pas une enumeration : Firebase
 * en ajoute (web, ios, android, et demain autre chose), et une enumeration
 * obligerait a redeployer le back pour accueillir un client de plus. Elle ne
 * sert qu'au diagnostic.
 */
public record JetonAppareilRequete(
        @NotBlank(message = "Le jeton est obligatoire")
        @Size(max = 255) String jeton,
        @Size(max = 12) String plateforme) {
}
