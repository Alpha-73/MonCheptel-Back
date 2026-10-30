package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ecran « Veto - Repondre a un signalement ». */
public record PriseEnChargeRequete(
        @NotBlank(message = "Le compte rendu est obligatoire")
        @Size(max = 2000) String compteRendu,

        /**
         * Facultatif : la maladie que le veterinaire retient. Elle remplace
         * la proposition du moteur dans les statistiques.
         */
        Long maladieConfirmeeId) {
}
