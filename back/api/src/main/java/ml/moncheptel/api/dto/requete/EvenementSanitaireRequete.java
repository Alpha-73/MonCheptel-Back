package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.TypeEvenement;

/** Ecran « Ajouter un evenement » — carnet sanitaire. */
public record EvenementSanitaireRequete(
        @NotNull(message = "Le type d'événement est obligatoire")
        TypeEvenement type,

        @NotNull(message = "La date est obligatoire")
        LocalDate dateEvenement,

        @Size(max = 255) String produitObservation,

        /** Rappel a programmer, en jours. Nul ou zero : pas de rappel. */
        Integer rappelDansJours) {
}
