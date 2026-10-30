package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.Min;
import ml.moncheptel.api.modele.enumeration.Filiere;

/**
 * Ecran « Investir - Mon budget ».
 *
 * <p>{@code filiere} est facultative : laissee vide, le simulateur compare
 * toutes les filieres, ce qui est exactement la question que se pose un
 * eleveur qui hesite. Renseignee, il ne rend que les types de cette filiere.
 *
 * <p>{@code sujetsVises} est facultatif aussi : sans lui, le simulateur part
 * du budget et calcule combien de sujets il permet. Avec lui, il part de
 * l'objectif et dit ce qu'il en coute.
 */
public record SimulationRequete(
        @Min(value = 1000, message = "Indiquez un budget d'au moins 1 000 FCFA")
        long budget,

        Filiere filiere,

        Integer sujetsVises,

        /** Compter l'installation (batiment, materiel) ou partir d'un elevage deja equipe. */
        boolean inclureInstallation) {
}
