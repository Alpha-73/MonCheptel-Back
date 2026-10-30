package ml.moncheptel.api.mapper;

import java.util.List;
import ml.moncheptel.api.dto.reponse.AlerteReponse;
import ml.moncheptel.api.dto.reponse.NotificationReponse;
import ml.moncheptel.api.modele.Alerte;
import ml.moncheptel.api.modele.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    /**
     * Le statut d'envoi Firebase et le compteur de tentatives ne sortent pas :
     * qu'une notification ait echoue trois fois regarde l'exploitation, pas
     * l'eleveur qui la lit dans l'application.
     */
    public NotificationReponse versReponse(Notification n) {
        if (n == null) {
            return null;
        }
        return new NotificationReponse(
                n.getId(),
                n.getType(),
                n.getTitre(),
                n.getCorps(),
                n.isLue(),
                n.getReferenceType(),
                n.getReferenceId(),
                n.getDateCreation());
    }

    public List<NotificationReponse> versReponses(List<Notification> notifications) {
        return notifications.stream().map(this::versReponse).toList();
    }

    public AlerteReponse versReponse(Alerte a) {
        if (a == null) {
            return null;
        }
        return new AlerteReponse(
                a.getId(),
                a.getMaladie() == null ? null : a.getMaladie().getNom(),
                a.getZone() == null ? null : a.getZone().getNom(),
                a.getFiliere(),
                a.getMessage(),
                a.getPortee(),
                a.getDateDeclenchement(),
                a.getStatut(),
                a.getDateFin(),
                a.getDateCloture());
    }

    public List<AlerteReponse> versAlertes(List<Alerte> alertes) {
        return alertes.stream().map(this::versReponse).toList();
    }
}
