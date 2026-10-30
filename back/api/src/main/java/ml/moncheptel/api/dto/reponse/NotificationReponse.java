package ml.moncheptel.api.dto.reponse;

import java.time.Instant;
import ml.moncheptel.api.modele.enumeration.TypeNotification;

/**
 * Une notification du centre de notifications.
 *
 * <p>{@code referenceType} et {@code referenceId} disent vers quel ecran
 * ouvrir au clic — c'est ce qui evite une notification qui ne mene nulle part.
 */
public record NotificationReponse(
        Long id,
        TypeNotification type,
        String titre,
        String corps,
        boolean lue,
        String referenceType,
        Long referenceId,
        Instant dateCreation) {
}
