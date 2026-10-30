package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Ecran « Ajouter un lot ».
 *
 * <p>{@code code} est facultatif : la maquette le pre-remplit (« LOT-015 »)
 * et l'eleveur peut le garder. Vide, le service en genere un.
 *
 * <p>Il n'y a PAS de {@code effectifRestant} : il vaut l'effectif initial a la
 * creation, et ne bouge ensuite que par un mouvement enregistre. L'exposer en
 * ecriture permettrait de faire disparaitre cinquante tetes sans trace.
 */
public record LotRequete(
        @Size(max = 20) String code,

        @NotNull(message = "Le type d'élevage est obligatoire")
        Long typeElevageId,

        @NotNull @Positive(message = "L'effectif doit être supérieur à zéro")
        Integer effectifInitial,

        @NotNull(message = "La date de mise en place est obligatoire")
        LocalDate dateMiseEnPlace) {
}
