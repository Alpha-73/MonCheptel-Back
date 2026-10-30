package ml.moncheptel.api.dto.reponse;

/**
 * Un libelle et un nombre : une ligne de barre sur les ecrans de pilotage.
 *
 * <p>Sert aux repartitions par zone, par role, par filiere et par maladie.
 * {@code LigneRepartitionReponse} ne pouvait pas servir : elle porte une
 * {@code CategorieOperation} et un montant, elle appartient aux finances.
 *
 * <p>Pas de pourcentage ici. Selon la repartition, le total n'a pas le meme
 * sens — un eleveur compte dans chacune de ses filieres, donc la somme des
 * lignes depasse le nombre d'eleveurs. Un pourcentage calcule au serveur
 * serait juste pour les zones et faux pour les filieres. C'est l'ecran, qui
 * sait ce qu'il dessine, qui choisit la reference.
 */
public record LigneStatistiqueReponse(String libelle, long valeur) {
}
