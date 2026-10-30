package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotNull;

/** {@code PUT /api/veterinaires/disponibilite} : {@code {"disponible": true}}. */
public record DisponibiliteRequete(
        @NotNull(message = "Indiquez si vous êtes disponible")
        Boolean disponible) {
}
