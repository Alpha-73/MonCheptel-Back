package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.TypeMouvement;

/**
 * Ecran « Sortie du cheptel » : vente, mortalite ou transfert.
 *
 * <p>{@code montant} n'est renseigne que pour une VENTE. Le service cree alors
 * l'operation financiere correspondante : l'eleveur saisit la vente une fois,
 * pas deux — c'est ce qui evite l'ecart entre le journal du cheptel et celui
 * des recettes.
 *
 * <p>{@code nombreTetes} est valide ici sur son signe seulement. Qu'il tienne
 * dans l'effectif restant est une regle metier, verifiee par
 * {@code UniteElevage.retirer} : l'annotation ne connait pas le lot vise.
 */
public record SortieRequete(
        @NotNull(message = "Le motif de sortie est obligatoire")
        TypeMouvement type,

        @NotNull @Positive(message = "Indiquez au moins une tête")
        Integer nombreTetes,

        @NotNull(message = "La date est obligatoire")
        LocalDate date,

        @Positive(message = "Le montant d'une vente doit être supérieur à zéro")
        Long montant,

        @Size(max = 255) String motif) {
}
