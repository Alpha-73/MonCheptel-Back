package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotNull;

/** Attribution manuelle d'un signalement a un veterinaire par l'administration. */
public record AttributionRequete(
        @NotNull(message = "Choisissez un vétérinaire") Long veterinaireId) {
}
