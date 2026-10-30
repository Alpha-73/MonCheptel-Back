package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.Size;

/**
 * Le veterinaire refuse un cas (V14). Le motif est facultatif : il est
 * transmis a l'eleveur et garde pour l'administration.
 */
public record RefusRequete(
        @Size(max = 500, message = "Le motif ne dépasse pas 500 caractères") String motif) {
}
