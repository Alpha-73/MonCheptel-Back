package ml.moncheptel.api.dto.reponse;

import java.time.Instant;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;

/**
 * Une ligne du tableau « Derniers signalements » de l'administration.
 *
 * <p>Exactement les quatre colonnes de la maquette — eleveur, maladie, zone,
 * gravite — plus la date et le statut, dont l'ecran a besoin pour trier et
 * colorer.
 *
 * <p>Pourquoi pas {@code SignalementReponse} : elle porte les symptomes, le
 * diagnostic complet avec ses deux hypotheses et leurs probabilites, et le
 * compte rendu. Charger tout cela pour afficher quatre mots ferait quatre
 * jointures de plus par ligne, sur un ecran qui en montre quatre.
 *
 * @param maladie {@code null} tant qu'aucune hypothese n'a ete retenue
 */
public record SignalementResumeReponse(
        Long id,
        String eleveurNom,
        String zone,
        String maladie,
        NiveauGravite gravite,
        StatutSignalement statut,
        Instant dateSignalement) {
}
