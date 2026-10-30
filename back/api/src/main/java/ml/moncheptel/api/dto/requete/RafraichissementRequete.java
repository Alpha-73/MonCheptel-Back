package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotBlank;

/** Renouvellement de session : le jeton de rafraichissement recu a la connexion. */
public record RafraichissementRequete(
        @NotBlank(message = "Le jeton de rafraîchissement est obligatoire")
        String jetonRafraichissement) {
}
