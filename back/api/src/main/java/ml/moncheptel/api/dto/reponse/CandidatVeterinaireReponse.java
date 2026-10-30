package ml.moncheptel.api.dto.reponse;

/**
 * Un veterinaire propose dans la fenetre « Attribuer » de l'administration,
 * avec ce qui aide a choisir : competent pour la maladie, meme zone que
 * l'eleveur, nombre de cas qu'il suit deja.
 */
public record CandidatVeterinaireReponse(
        Long id,
        String nom,
        String zone,
        boolean competent,
        boolean memeZone,
        boolean disponible,
        long casOuverts,
        boolean actuel,
        /** Il a deja refuse ce cas (V14) : l'administration peut passer outre. */
        boolean aRefuse) {
}
