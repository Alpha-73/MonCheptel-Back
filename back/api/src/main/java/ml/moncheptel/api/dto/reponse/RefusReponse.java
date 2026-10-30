package ml.moncheptel.api.dto.reponse;

import java.time.Instant;

/** Un refus de cas par un veterinaire (V14) : qui, quand, pourquoi. */
public record RefusReponse(
        String veterinaireNom,
        String motif,
        Instant dateRefus) {
}
