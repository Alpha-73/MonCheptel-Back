package ml.moncheptel.api.dto.reponse;

import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.NiveauDifficulte;

/**
 * Une option proposee par le simulateur — ecran « Investir - Options ».
 *
 * <p>{@code sujetsAAcheter} depasse {@code sujetsVises} : le referentiel
 * prevoit une mortalite, et viser 30 poulets vivants en fin de cycle demande
 * d'en acheter 33 a 8 %. L'ecart est montre, pas absorbe : un eleveur qui
 * decouvre a la fin qu'il lui en manque trois perd confiance dans l'outil.
 *
 * <p>{@code realisable} est faux quand le cout depasse le budget. L'option est
 * quand meme renvoyee, avec son manque : savoir qu'il manque 23 593 FCFA est
 * une information, une liste vide n'en est pas une.
 */
public record OptionInvestissementReponse(
        Long typeElevageId,
        String libelle,
        Filiere filiere,
        NiveauDifficulte difficulte,
        Integer dureeCycleJours,
        int sujetsVises,
        int sujetsAAcheter,
        long coutInstallation,
        long coutCycle,
        long coutTotal,
        /** Budget moins cout total : positif s'il reste, negatif s'il manque. */
        long ecartBudget,
        boolean realisable) {
}
