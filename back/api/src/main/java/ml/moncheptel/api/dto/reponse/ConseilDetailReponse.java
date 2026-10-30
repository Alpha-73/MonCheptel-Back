package ml.moncheptel.api.dto.reponse;

import java.time.Instant;
import ml.moncheptel.api.modele.enumeration.CategorieConseil;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutConseil;

/**
 * Lecture d'un conseil.
 *
 * <p>{@code statut} et {@code motifRetrait} ne sont renseignes que pour
 * l'auteur et l'administration : un eleveur ne lit que des conseils publies,
 * ces deux champs seraient toujours les memes pour lui.
 */
public record ConseilDetailReponse(
        Long id,
        String titre,
        String contenu,
        Filiere filiere,
        CategorieConseil categorie,
        VeterinaireResumeReponse auteur,
        Instant datePublication,
        int nombreLectures,
        StatutConseil statut,
        String motifRetrait,
        Instant dateCreation,
        Instant dateSoumission) {
}
