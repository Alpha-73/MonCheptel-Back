package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.CategorieOperation;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.TypeOperation;

/**
 * Ecran « Ajouter une operation ».
 *
 * <p>Le montant est un ENTIER de FCFA, comme partout dans le modele : le franc
 * CFA n'a pas de subdivision en circulation. Un flottant introduirait des
 * arrondis a additionner, et un solde qui ne tombe pas juste.
 *
 * <p>Il est toujours POSITIF. Le sens vient de {@code type}, et de lui seul :
 * accepter un montant negatif ouvrirait deux facons d'ecrire la meme depense,
 * et le solde du mois deviendrait indefendable.
 *
 * <p>{@code uniteId} est facultatif : un sac d'aliment achete en gros ne se
 * rattache a aucun lot. Ces operations comptent dans le solde du mois, jamais
 * dans la rentabilite d'un lot.
 */
public record OperationRequete(
        @NotNull TypeOperation type,
        @NotNull CategorieOperation categorie,

        @Positive(message = "Le montant doit être supérieur à zéro")
        long montant,

        @NotNull(message = "La date est obligatoire")
        LocalDate dateOperation,

        @NotNull(message = "La filière est obligatoire")
        Filiere filiere,

        Long uniteId,

        @Size(max = 255) String libelle) {
}
