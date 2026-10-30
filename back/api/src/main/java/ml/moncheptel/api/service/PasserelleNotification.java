package ml.moncheptel.api.service;

import java.util.List;
import ml.moncheptel.api.modele.Notification;

/**
 * Envoi effectif d'une notification push.
 *
 * <p>Une interface, et non un appel direct a Firebase, pour trois raisons :
 *
 * <ul>
 *   <li>Les tests n'ont pas de projet Firebase. Sans cette coupure, tester
 *       une alerte epidemique demanderait un reseau et des identifiants.</li>
 *   <li>Le canal SMS, retire du perimetre V1, reviendra peut-etre. Il
 *       s'ajoutera comme une seconde implementation, sans toucher aux
 *       services.</li>
 *   <li>Une panne de Firebase ne doit pas empecher d'enregistrer la
 *       notification en base : l'eleveur la verra en ouvrant l'application.
 *       La separation rend ce repli evident.</li>
 * </ul>
 */
public interface PasserelleNotification {

    /**
     * @param jetons jetons d'appareil du destinataire ; un utilisateur peut
     *               avoir plusieurs telephones
     * @return les jetons devenus invalides, a desactiver en base
     */
    List<String> envoyer(Notification notification, List<String> jetons);
}
