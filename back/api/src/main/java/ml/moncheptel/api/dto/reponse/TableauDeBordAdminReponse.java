package ml.moncheptel.api.dto.reponse;

import java.util.List;

/**
 * L'ecran d'accueil de l'administration : huit tuiles, deux tableaux.
 *
 * <h2>Ce que la maquette affiche et que l'API ne rend pas</h2>
 *
 * <p>« + 86 ce mois » est rendu ({@code inscritsCeMois}). En revanche, aucun
 * ECART par rapport au mois precedent n'est calculable : il faudrait un releve
 * conserve, et rien ne le conserve. Mieux vaut une tuile sans variation qu'une
 * variation inventee.
 *
 * @param tetesVivantes    somme des effectifs de lot plus les animaux suivis un
 *                         par un, sur toute la plateforme
 * @param tetesVolaille    la part volaille des precedentes
 * @param zonesSousAlerte  les noms des zones ou une alerte est active, pour la
 *                         ligne « Koutiala, Koulikoro » sous la tuile
 * @param casSansVeterinaire signalements graves qu'aucun veterinaire ne
 *                         couvre (zone et maladie) : ils restent NOUVEAU
 * @param parFiliere       un eleveur pratiquant quatre filieres compte quatre
 *                         fois : la somme depasse le nombre d'eleveurs, et ce
 *                         n'est pas une erreur
 */
public record TableauDeBordAdminReponse(
        long utilisateurs,
        long inscritsCeMois,
        long tetesVivantes,
        long tetesVolaille,
        long alertesActives,
        List<String> zonesSousAlerte,
        long veterinaires,
        long zonesCouvertes,
        long signalements30j,
        long alertes30j,
        long orientesEnAttente,
        long casSansVeterinaire,
        long conseilsPublies,
        long conseilsEnModeration,
        List<SignalementResumeReponse> derniersSignalements,
        List<LigneStatistiqueReponse> parFiliere) {
}
