package ml.moncheptel.api.dto.reponse;

import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.CategorieOperation;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.TypeOperation;

/**
 * Une ligne de « Depenses et revenus ».
 *
 * <p>{@code montant} reste positif ; c'est {@code type} qui porte le sens.
 * {@code montantSigne} est fourni en plus pour que le client n'ait pas a
 * rejouer la regle du signe a l'affichage.
 */
public record OperationReponse(
        Long id,
        TypeOperation type,
        CategorieOperation categorie,
        long montant,
        long montantSigne,
        LocalDate dateOperation,
        Filiere filiere,
        Long uniteId,
        String uniteCode,
        String libelle,
        /** Vraie quand l'operation est nee d'une vente : elle n'est pas modifiable. */
        boolean issueDUnMouvement) {
}
