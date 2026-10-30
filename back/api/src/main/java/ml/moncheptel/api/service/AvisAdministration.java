package ml.moncheptel.api.service;

import ml.moncheptel.api.modele.Administrateur;
import ml.moncheptel.api.modele.enumeration.TypeNotification;
import ml.moncheptel.api.repository.AdministrateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Previent les administrateurs actifs d'un evenement qui demande leur regard
 * (audit du 10 octobre) :
 * <ul>
 *   <li>une maladie ajoutee au catalogue par un veterinaire — elle est
 *       utilisable tout de suite, l'administration garde la tracabilite et
 *       le droit de retrait ;</li>
 *   <li>un cas grave pour lequel aucun veterinaire n'est proposable (aucun
 *       competent dans la zone, ou eleveur sans zone) — il serait reste
 *       {@code NOUVEAU} sans que personne ne le sache.</li>
 * </ul>
 *
 * <p>Une notification par administrateur, dans la transaction appelante :
 * annulee avec elle, envoyee apres sa validation (NotificationService).
 */
@Service
public class AvisAdministration {

    private final AdministrateurRepository administrateurs;
    private final NotificationService notifications;

    public AvisAdministration(AdministrateurRepository administrateurs,
                              NotificationService notifications) {
        this.administrateurs = administrateurs;
        this.notifications = notifications;
    }

    @Transactional
    public int prevenir(TypeNotification type, String titre, String corps,
                        String referenceType, Long referenceId) {
        int n = 0;
        for (Administrateur a : administrateurs.findAll()) {
            if (a.isActif()) {
                notifications.creer(a, type, titre, corps, referenceType, referenceId);
                n++;
            }
        }
        return n;
    }
}
