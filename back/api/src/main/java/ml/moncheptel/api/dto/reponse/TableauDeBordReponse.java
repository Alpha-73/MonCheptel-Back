package ml.moncheptel.api.dto.reponse;

import java.util.List;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;

/**
 * Ecran d'accueil de l'eleveur, en un appel.
 *
 * <p>Les compteurs sont ceux de la filiere demandee ; le solde est celui de
 * TOUTES les filieres — c'est ce que dit la maquette, « Solde du mois — toutes
 * filieres », et c'est coherent : un eleveur gere une seule tresorerie.
 */
public record TableauDeBordReponse(
        String nomEleveur,
        String zone,
        Set<Filiere> filieres,
        Filiere filiereAffichee,
        int unitesActives,
        int tetesRestantes,
        int unitesCloturees,
        int tetesSorties,
        SoldeReponse soldeDuMois,
        String moisCourant,
        List<AlerteReponse> alertes,
        List<RappelReponse> rappels,
        long notificationsNonLues) {
}
