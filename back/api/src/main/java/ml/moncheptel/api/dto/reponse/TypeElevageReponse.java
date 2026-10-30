package ml.moncheptel.api.dto.reponse;

import java.math.BigDecimal;
import java.util.List;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.modele.enumeration.NiveauDifficulte;

/** Ecran « Admin - Referentiel : fiche », et liste deroulante a la creation d'un lot. */
public record TypeElevageReponse(
        Long id,
        Filiere filiere,
        String libelle,
        ModeEvaluation modeEvaluation,
        Integer dureeCycleJours,
        int capaciteReference,
        BigDecimal espaceParSujetM2,
        Integer temperatureMin,
        Integer temperatureMax,
        BigDecimal alimentParSujetKg,
        BigDecimal eauParSujetJourL,
        String vaccinsObligatoires,
        BigDecimal tauxMortalite,
        NiveauDifficulte difficulte,
        boolean actif,
        List<PosteCoutReponse> postesCout) {
}
