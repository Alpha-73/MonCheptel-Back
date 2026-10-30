package ml.moncheptel.api.dto.reponse;

/**
 * Ecran « Veto - Accueil » : les compteurs du haut de page.
 *
 * <p>« En attente » regroupe les cas orientes et ceux deja pris en charge mais
 * pas encore traites : du point de vue de l'eleveur, les deux attendent.
 */
public record AccueilVeterinaireReponse(
        long enAttente,
        long enAttenteGraves,
        long traitesCeMois,
        long conseilsPublies,
        long conseilsEnModeration,
        /** Le veterinaire recoit-il de nouveaux cas (interrupteur de l'accueil). */
        boolean disponible) {
}
