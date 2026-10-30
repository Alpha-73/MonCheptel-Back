package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.Filiere;

/**
 * Ecran « Admin - Declencher une alerte ».
 *
 * <p>{@code filiere} nulle vise toute la zone. C'est volontaire : une
 * contamination de l'eau ne connait pas les filieres, une epidemie aviaire si.
 *
 * <p>Declencher une alerte envoie une notification push a tous les eleveurs
 * concernes. {@code dateFin}, facultative, est le dernier jour de l'alerte :
 * elle se clot d'elle-meme le lendemain.
 *
 * <p>C'est irreversible — l'ecran demande confirmation, et le service
 * refuse un doublon actif sur le meme couple maladie/zone.
 */
public record AlerteRequete(
        @NotNull Long maladieId,
        @NotNull Long zoneId,
        Filiere filiere,
        @NotBlank @Size(max = 500) String message,
        @FutureOrPresent(message = "La date de fin ne peut pas être passée") LocalDate dateFin) {
}
