package ml.moncheptel.api.dto.reponse;

import java.time.Instant;
import ml.moncheptel.api.modele.enumeration.CategorieConseil;
import ml.moncheptel.api.modele.enumeration.Filiere;

/**
 * Une carte de la liste « Conseils ».
 *
 * <p>{@code extrait} plutot que le contenu entier : un conseil peut faire dix
 * mille caracteres, et une liste de vingt cartes en ferait deux cents mille
 * sur une connexion de campagne pour afficher trois lignes chacune.
 */
public record ConseilResumeReponse(
        Long id,
        String titre,
        String extrait,
        Filiere filiere,
        CategorieConseil categorie,
        String auteur,
        Instant datePublication,
        int nombreLectures) {
}
