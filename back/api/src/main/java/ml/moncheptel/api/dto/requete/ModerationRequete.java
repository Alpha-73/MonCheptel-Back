package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ecran « Admin - Moderation » : publier ou refuser un conseil.
 *
 * <p>Le motif est obligatoire quand la decision est negative — la verification
 * croisee est dans le service, une annotation ne peut pas lire l'autre champ.
 * Refuser sans motif laisserait le veterinaire sans rien a corriger.
 */
public record ModerationRequete(
        @NotNull(message = "Indiquez votre décision")
        Boolean publier,

        @Size(max = 500) String motif) {
}
