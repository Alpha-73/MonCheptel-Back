package ml.moncheptel.api.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Lance un envoi de courriel APRES la validation de la transaction, hors du
 * fil de la requete (audit global G14).
 *
 * <p>Les courriels de mot de passe provisoire (creation d'un veterinaire,
 * reinitialisation par l'administration) partaient DANS la transaction :
 * <ul>
 *   <li>une panne SMTP faisait echouer l'operation en 500 et l'annulait ;</li>
 *   <li>un commit qui echouait apres l'envoi laissait un mot de passe pour un
 *       compte qui n'existait pas ;</li>
 *   <li>le fil gardait sa connexion a la base pendant toute l'attente SMTP.</li>
 * </ul>
 *
 * <p>Ici l'envoi n'a lieu que si la transaction est validee, sur un fil a
 * part. Un echec est journalise en ERROR ; l'administration peut relancer
 * une reinitialisation. Que le courriel soit CONFIGURE est verifie avant,
 * par l'appelant ({@link ServiceCourriel#estConfigure()}), avec un 422 clair.
 *
 * <p>Sans transaction active, l'envoi part tout de suite, sur le meme fil
 * a part.
 */
public final class ApresValidation {

    private static final Logger JOURNAL = LoggerFactory.getLogger(ApresValidation.class);

    private static final ExecutorService ENVOIS = Executors.newFixedThreadPool(2, tache -> {
        Thread fil = new Thread(tache, "courriel-apres-validation");
        fil.setDaemon(true);
        return fil;
    });

    private ApresValidation() {
    }

    /** @param quoi ce qui est envoye, pour le journal (jamais de secret) */
    public static void envoyer(String quoi, Runnable envoi) {
        Runnable protege = () -> {
            try {
                envoi.run();
            } catch (RuntimeException e) {
                JOURNAL.error("Envoi impossible ({}) : {}", quoi, e.getMessage());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            ENVOIS.submit(protege);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ENVOIS.submit(protege);
            }
        });
    }
}
