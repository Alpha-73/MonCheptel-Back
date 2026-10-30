package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.TrancheEffectif;

/**
 * Ecrans « Signalement » : choix du sujet, symptomes, ampleur.
 *
 * <p>Un seul envoi pour les trois ecrans. L'application accumule les reponses
 * et poste une fois : le diagnostic revient dans la meme reponse, et l'ecran
 * de resultat n'a pas besoin d'un second aller-retour.
 *
 * <p>{@code trancheEffectif} est une tranche, pas un nombre. La maquette
 * propose « 10 a 50 » : demander un chiffre exact a un eleveur qui constate
 * des symptomes dans un poulailler de 200 tetes produirait une precision
 * fausse, et cette precision fausse remonterait au veterinaire.
 */
public record SignalementRequete(
        @NotNull(message = "Indiquez le lot ou l'animal concerné")
        Long uniteId,

        @NotEmpty(message = "Sélectionnez au moins un symptôme")
        Set<Long> symptomeIds,

        @NotNull(message = "Indiquez le nombre de sujets touchés")
        TrancheEffectif trancheEffectif,

        @Size(max = 1000) String precision) {
}
