package ml.moncheptel.api.dto.reponse;

import java.util.List;

/**
 * L'ecran « Statistiques » : adoption et activite de la plateforme.
 *
 * <h2>Trois chiffres demandent une definition, pas seulement un calcul</h2>
 *
 * <p><b>{@code utilisateurs}</b> : TOUS les comptes de la plateforme, les
 * trois roles confondus. C'est la somme de {@code parRole}, et c'est le meme
 * ensemble que le denominateur de {@code tauxRetention30j} — les trois tuiles
 * de la maquette sont cote a cote, elles doivent compter la meme population.
 * Jusqu'au 5 octobre, cette tuile rendait les ELEVEURS seuls tandis que les
 * deux autres portaient sur tous les roles : trois populations sous un titre.
 *
 * <p><b>{@code tauxRetention30j}</b> : la part des comptes ayant ouvert
 * l'application au moins une fois dans les trente derniers jours. La maquette
 * affiche « 64 % » sans dire de quoi ; c'est la definition retenue, et elle
 * doit etre enoncee avec le chiffre. Un taux de retention sans sa definition
 * ne veut rien dire.
 *
 * <p><b>{@code delaiMoyenPriseEnChargeHeures}</b> : moyenne, en heures, entre
 * le signalement et son orientation vers un veterinaire. {@code null} tant
 * qu'aucun signalement n'a ete oriente — sur une base neuve, l'ecran doit
 * afficher un tiret, pas « 0 h », qui laisserait croire a une prise en charge
 * instantanee.
 *
 * <p>Les variations de la maquette — « + 5 pts » — ne sont pas rendues : voir
 * {@code StatistiqueRepository}.
 *
 * @param signalementsTraitesSur le denominateur de {@code partSignalementsTraites},
 *                               affiche tel quel par la maquette : « 81 % sur
 *                               94 signalements ». Un pourcentage sur neuf cas
 *                               ne se lit pas comme un pourcentage sur neuf
 *                               cents.
 *                               <p><b>Change le 5 octobre</b> : ce n'est plus
 *                               le nombre de TOUS les signalements, mais celui
 *                               des signalements qui ont NECESSITE un
 *                               veterinaire — ceux qui ont franchi
 *                               l'orientation. Un cas benin n'est jamais
 *                               oriente, donc jamais {@code TRAITE} : le
 *                               compter au denominateur posait au pourcentage
 *                               un plafond que rien ne pouvait lever. Le
 *                               libelle a afficher devient donc « 81 % des
 *                               cas orientes » plutot que « des
 *                               signalements ». Voir
 *                               {@code StatistiqueRepository.compterNecessitantUnVeterinaireDepuis}.
 */
public record StatistiquesReponse(
        long utilisateurs,
        long inscritsCeMois,
        int tauxRetention30j,
        int partSignalementsTraites,
        long signalementsTraitesSur,
        Double delaiMoyenPriseEnChargeHeures,
        List<LigneStatistiqueReponse> parZone,
        List<LigneStatistiqueReponse> parRole,
        List<LigneStatistiqueReponse> maladiesLesPlusSignalees) {
}
