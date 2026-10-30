package ml.moncheptel.api.dto.reponse;

import java.util.List;

/**
 * Ecran « Investir - Options ».
 *
 * <p>Les options realisables d'abord, puis les autres par ecart croissant :
 * la premiere option hors budget est la plus proche, donc la plus utile a
 * connaitre.
 */
public record SimulationReponse(
        long budget,
        boolean installationIncluse,
        List<OptionInvestissementReponse> options) {
}
