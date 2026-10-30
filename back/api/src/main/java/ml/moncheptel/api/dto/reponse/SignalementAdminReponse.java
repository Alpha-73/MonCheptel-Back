package ml.moncheptel.api.dto.reponse;

import java.time.Instant;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;

/**
 * Une ligne de l'ecran « Signalements » de l'administration.
 *
 * <p>{@code maladieProposee} est celle du moteur, {@code maladieConfirmee}
 * celle du veterinaire a la cloture. {@code aAttribuer} : cas qui devrait
 * etre suivi par un veterinaire et ne l'est pas.
 */
public record SignalementAdminReponse(
        Long id,
        Long eleveurId,
        String eleveurNom,
        String zone,
        String uniteCode,
        String maladieProposee,
        String maladieConfirmee,
        NiveauGravite gravite,
        StatutSignalement statut,
        boolean aAttribuer,
        Long veterinaireId,
        String veterinaireNom,
        Instant dateSignalement,
        Instant dateOrientation,
        Instant datePriseEnCharge,
        Instant dateTraitement,
        /** Les refus de ce cas par des veterinaires (V14), du plus recent au plus ancien. */
        java.util.List<RefusReponse> refus) {
}
