package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.Sexe;

/** Ecran « Ajouter un animal » — filieres a suivi individuel. */
public record AnimalRequete(
        @Size(max = 20) String code,

        @NotNull(message = "Le type d'élevage est obligatoire")
        Long typeElevageId,

        @NotNull(message = "La date d'entrée est obligatoire")
        LocalDate dateEntree,

        @Size(max = 60) String race,
        Sexe sexe,
        LocalDate dateNaissance) {
}
