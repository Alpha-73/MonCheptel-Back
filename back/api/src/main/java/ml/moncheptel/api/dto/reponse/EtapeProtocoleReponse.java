package ml.moncheptel.api.dto.reponse;

import ml.moncheptel.api.modele.enumeration.TypeEvenement;

/** Une etape du protocole sanitaire d'un type d'elevage. */
public record EtapeProtocoleReponse(
        Long id,
        String libelle,
        TypeEvenement typeEvenement,
        int jourDuCycle,
        boolean obligatoire) {
}
