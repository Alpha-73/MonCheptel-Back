package ml.moncheptel.api.dto.reponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;

/**
 * Une maladie du catalogue vue par l'administration : ecrans « Catalogue des
 * maladies », « Ajouter » et « Modifier une maladie ».
 *
 * <p>Porte les ponderations symptome par symptome, que le formulaire de
 * modification doit pouvoir relire, et le nombre de veterinaires competents
 * (colonne « Compétence » du catalogue).
 *
 * <p>{@code ajouteePar} : le veterinaire qui l'a ajoutee, nul pour le
 * catalogue de reference. Ajoute en fin de record : les clients existants
 * ne sont pas affectes.
 */
public record MaladieAdminReponse(
        Long id,
        String nom,
        NiveauGravite gravite,
        Set<Filiere> filieres,
        String symptomesTexte,
        String traitement,
        boolean orienteVersVeterinaire,
        boolean active,
        List<PoidsSymptome> symptomes,
        long veterinairesCompetents,
        String ajouteePar) {

    public record PoidsSymptome(Long symptomeId, String libelle, BigDecimal poids) {
    }
}
