package ml.moncheptel.api.dto.reponse;

import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;

/**
 * Fiche d'une maladie, telle qu'un ELEVEUR la voit.
 *
 * <p>{@code traitement} est nul quand la maladie exige un veterinaire. Ce
 * n'est pas une omission d'affichage : le texte ne quitte pas le serveur. Le
 * masquer cote client laisserait le protocole dans la reponse JSON, lisible
 * par quiconque ouvre les outils de developpement.
 */
public record MaladieReponse(
        Long id,
        String nom,
        NiveauGravite gravite,
        Set<Filiere> filieres,
        String symptomesTexte,
        String traitement,
        boolean orienteVersVeterinaire) {
}
