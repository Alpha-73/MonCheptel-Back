package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ml.moncheptel.api.modele.enumeration.TypeEvenement;

/**
 * Une etape du protocole sanitaire d'un type d'elevage (« Newcastle, J7 »).
 * {@code id} absent pour une etape ajoutee a l'ecran.
 */
public record EtapeProtocoleRequete(
        Long id,
        @NotBlank @Size(max = 120) String libelle,
        @NotNull TypeEvenement typeEvenement,
        @PositiveOrZero @Max(value = 3650, message = "Jour du cycle au-delà de dix ans") int jourDuCycle,
        boolean obligatoire) {
}
