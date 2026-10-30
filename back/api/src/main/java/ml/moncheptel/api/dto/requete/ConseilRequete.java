package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ml.moncheptel.api.modele.enumeration.CategorieConseil;
import ml.moncheptel.api.modele.enumeration.Filiere;

/**
 * Ecran « Veto - Rediger un conseil ».
 *
 * <p>Le statut n'est pas ici : un auteur ne se publie pas lui-meme. Il
 * enregistre un brouillon, puis le soumet ; c'est la moderation qui publie.
 */
public record ConseilRequete(
        @NotBlank @Size(max = 160) String titre,
        @NotBlank @Size(max = 10000) String contenu,
        @NotNull Filiere filiere,
        @NotNull CategorieConseil categorie) {
}
