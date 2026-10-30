package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Nouveau symptome saisi par l'administration ou un veterinaire, quand une
 * maladie presente un signe qui ne figure pas encore dans la liste a cocher.
 */
public record SymptomeRequete(
        @NotBlank(message = "Indiquez le symptôme")
        @Size(max = 80, message = "80 caractères au plus")
        String libelle) {
}
