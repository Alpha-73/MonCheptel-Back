package ml.moncheptel.api.dto.reponse;

import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.ModeSuivi;
import ml.moncheptel.api.modele.enumeration.Sexe;
import ml.moncheptel.api.modele.enumeration.StatutUnite;

/**
 * Une ligne de la liste « Mes animaux ».
 *
 * <p>Une seule forme pour un lot et pour un animal. La liste les melange par
 * filiere : deux reponses distinctes obligeraient le client a deux appels et a
 * deux rendus pour une seule liste.
 *
 * <p>{@code effectifInitial} et {@code effectifRestant} valent 1 et 1 ou 0
 * pour un animal. C'est exact, pas un bouche-trou : un animal est une unite
 * d'une tete.
 */
public record UniteResumeReponse(
        Long id,
        String code,
        ModeSuivi modeSuivi,
        Filiere filiere,
        String typeElevage,
        StatutUnite statut,
        LocalDate dateEntree,
        int effectifInitial,
        int effectifRestant,
        /** Renseignee pour un lot a suivi par periode, nulle sinon. */
        LocalDate dateCloturePrevue,
        /** Jours restants avant cloture ; negatif si depassee, nul si non applicable. */
        Integer joursRestants,
        /**
         * Identifiant du type d'elevage : le formulaire de modification doit
         * le renvoyer tel quel ({@code UniteRequete.typeElevageId} est
         * obligatoire), et le libelle seul ne permet pas de le retrouver.
         */
        Long typeElevageId,
        /** Animal seulement ; nuls pour un lot (omis du JSON). */
        String race,
        Sexe sexe,
        LocalDate dateNaissance,
        String note) {
}
