package ml.moncheptel.api.dto.reponse;

import java.util.List;

/** Ecran « Investir - Detail d'une option » : l'option, puis chaque poste. */
public record DetailSimulationReponse(
        OptionInvestissementReponse option,
        List<LignePosteReponse> postesInstallation,
        List<LignePosteReponse> postesCycle) {
}
