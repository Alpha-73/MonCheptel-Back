package ml.moncheptel.api.dto.reponse;

/** Un mois de la courbe d'evolution. {@code mois} au format AAAA-MM. */
public record PointMensuelReponse(
        String mois,
        long revenus,
        long depenses,
        long solde) {
}
