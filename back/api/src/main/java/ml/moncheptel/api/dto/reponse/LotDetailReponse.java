package ml.moncheptel.api.dto.reponse;

import java.util.List;

/**
 * Ecran « Fiche du lot » : le resume, puis les trois journaux.
 *
 * <p>Tout en un seul appel. La fiche affiche les trois listes des l'ouverture ;
 * trois requetes separees feraient trois allers-retours sur un reseau qui
 * n'est pas toujours la, pour un ecran qui tient en un.
 */
public record LotDetailReponse(
        UniteResumeReponse unite,
        List<MouvementReponse> mouvements,
        List<EvenementReponse> evenements,
        List<RappelReponse> rappels,
        SoldeReponse rentabilite,
        /** Sorties (vente, mort, transfert), la plus recente d'abord. {@code id} = id du mouvement. */
        List<SortieReponse> sorties) {
}
