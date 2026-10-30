package ml.moncheptel.api.dto.reponse;

import java.time.Instant;
import java.util.List;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;
import ml.moncheptel.api.modele.enumeration.TrancheEffectif;

/**
 * Un signalement, cote eleveur comme cote veterinaire.
 *
 * <p>{@code eleveurTelephone} n'est renseigne que pour le veterinaire en
 * charge. Un eleveur qui relit son propre signalement n'a pas besoin de son
 * numero, et l'exposer dans la reponse generale le rendrait lisible par
 * quiconque devine un identifiant.
 */
public record SignalementReponse(
        Long id,
        Long uniteId,
        String uniteCode,
        String eleveurNom,
        String eleveurTelephone,
        String eleveurZone,
        List<SymptomeReponse> symptomes,
        TrancheEffectif trancheEffectif,
        String precision,
        Instant dateSignalement,
        DiagnosticReponse diagnostic,
        StatutSignalement statut,
        Instant dateTraitement,
        String compteRendu,
        String uniteType,
        Integer uniteEffectif,
        /** Le diagnostic retenu par le veterinaire a la cloture, s'il en a pose un. */
        String maladieConfirmee,
        /** Filiere de l'unite : filtre le catalogue au moment de la cloture. */
        ml.moncheptel.api.modele.enumeration.Filiere uniteFiliere,
        /**
         * Le veterinaire oriente ou en charge (nom, telephone…), renseigne
         * meme quand {@code diagnostic} est nul. Absent tant que le cas n'est
         * confie a personne.
         */
        VeterinaireResumeReponse veterinaire,
        Instant dateOrientation,
        Instant datePriseEnCharge,
        /**
         * Les veterinaires qui ont refuse ce cas (V14), du plus recent au
         * plus ancien. Vide dans les listes : seul le detail le charge.
         */
        List<RefusReponse> refus) {

    /** La meme reponse, avec l'historique des refus. */
    public SignalementReponse avecRefus(List<RefusReponse> historique) {
        return new SignalementReponse(id, uniteId, uniteCode, eleveurNom, eleveurTelephone,
                eleveurZone, symptomes, trancheEffectif, precision, dateSignalement, diagnostic,
                statut, dateTraitement, compteRendu, uniteType, uniteEffectif, maladieConfirmee,
                uniteFiliere, veterinaire, dateOrientation, datePriseEnCharge,
                historique == null ? List.of() : historique);
    }
}
