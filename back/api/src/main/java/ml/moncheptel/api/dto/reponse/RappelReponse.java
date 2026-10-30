package ml.moncheptel.api.dto.reponse;

import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.StatutRappel;

/**
 * « Rappel vaccination — LOT-014, echeance dans 3 jours ».
 *
 * <p>{@code joursRestants} est calcule par le serveur, pas par le client :
 * le telephone d'un eleveur peut avoir une date fausse, et un rappel de
 * vaccination affiche trois jours trop tard coute un troupeau.
 */
public record RappelReponse(
        Long id,
        Long uniteId,
        String uniteCode,
        String libelle,
        LocalDate dateEcheance,
        int joursRestants,
        StatutRappel statut) {
}
