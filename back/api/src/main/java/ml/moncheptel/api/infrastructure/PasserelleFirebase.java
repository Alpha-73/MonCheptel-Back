package ml.moncheptel.api.infrastructure;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ml.moncheptel.api.modele.Notification;
import ml.moncheptel.api.modele.enumeration.TypeNotification;
import ml.moncheptel.api.service.PasserelleNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Envoi des notifications par Firebase Cloud Messaging.
 *
 * <h2>Les donnees accompagnent la notification</h2>
 *
 * <p>{@code referenceType} et {@code referenceId} sont joints en
 * {@code data} : c'est ce qui permet a l'application, au clic, d'ouvrir le
 * signalement ou le lot concerne plutot que l'ecran d'accueil. Une
 * notification qui ne mene nulle part n'est lue qu'une fois.
 *
 * <h2>UNREGISTERED n'est pas une panne</h2>
 *
 * <p>Firebase rend ce code quand l'application a ete desinstallee. Le jeton
 * est alors desactive plutot que retente : sans cela, chaque alerte rejouerait
 * indefiniment des envois vers des telephones qui n'ecoutent plus, et la file
 * de reprise se remplirait de cas insolubles.
 */
@Component
public class PasserelleFirebase implements PasserelleNotification {

    private static final Logger JOURNAL = LoggerFactory.getLogger(PasserelleFirebase.class);

    /** Nul quand Firebase n'est pas configure. Voir le constructeur. */
    private final FirebaseApp firebaseApp;

    /**
     * <b>{@code ObjectProvider} et non {@code FirebaseApp} directement.</b>
     *
     * <p>Spring n'enregistre PAS un bean dont la methode {@code @Bean} rend
     * {@code null} : il journalise « ignored as the bean value is null » et
     * passe. Une dependance declaree sur {@code FirebaseApp} fait alors
     * echouer le demarrage de toute l'application — ce qui est exactement ce
     * que {@code ConfigurationFirebase} cherche a eviter.
     *
     * <p>{@code ObjectProvider} exprime « ce bean peut ne pas exister » :
     * {@code getIfAvailable()} rend {@code null} sans rien casser. C'est le
     * seul endroit du projet ou une dependance est facultative, et c'est
     * assume : sans projet Firebase — le cas de tout poste de developpement —
     * les notifications sont enregistrees en base et lues dans
     * l'application, sans etre poussees.
     */
    public PasserelleFirebase(ObjectProvider<FirebaseApp> fournisseur) {
        this.firebaseApp = fournisseur.getIfAvailable();
    }

    @Override
    public List<String> envoyer(Notification notification, List<String> jetons) {
        if (firebaseApp == null) {
            // Firebase non configure : la notification reste en base et
            // s'affichera a l'ouverture de l'application. Ce n'est pas une
            // erreur, donc pas d'exception.
            JOURNAL.debug("Firebase absent : notification {} non poussee", notification.getId());
            return List.of();
        }
        if (jetons.isEmpty()) {
            return List.of();
        }

        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(jetons)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(notification.getTitre())
                        .setBody(notification.getCorps())
                        .build())
                .putAllData(donnees(notification))
                // Priorite HAUTE (audit du 11 octobre) : une priorite normale
                // peut etre differee par Android quand le telephone est en
                // veille — un vétérinaire apprendrait un cas grave des heures
                // plus tard. Firebase reserve la priorite haute aux messages
                // que l'utilisateur voit : c'est le cas de toutes les notres.
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(apparence(notification))
                        .build())
                .build();

        try {
            BatchResponse reponse = FirebaseMessaging.getInstance()
                    .sendEachForMulticast(message);
            return jetonsAOublier(reponse, jetons);
        } catch (FirebaseMessagingException e) {
            // Remonte au service, qui incrementera le compteur de tentatives.
            throw new IllegalStateException("Envoi Firebase impossible : " + e.getMessage(), e);
        }
    }

    /** Categorie « urgente » (bandeau et son), creee par l'application. */
    static final String CATEGORIE_URGENTE = "moncheptel_urgent";
    /** Categorie des rappels et informations (son, sans bandeau). */
    static final String CATEGORIE_INFOS = "moncheptel_infos";

    /**
     * Categorie Android de la notification (audit du 11 octobre). Un cas
     * confie, une reponse du veterinaire ou une alerte de zone doivent se
     * voir tout de suite ; un rappel de soins peut attendre que l'eleveur
     * regarde son telephone. L'utilisateur peut couper l'une sans l'autre.
     */
    static String categorie(TypeNotification type) {
        return switch (type) {
            case ALERTE, ORIENTATION -> CATEGORIE_URGENTE;
            case RAPPEL, MODERATION, SYSTEME -> CATEGORIE_INFOS;
        };
    }

    /**
     * Etiquette de regroupement : une nouvelle notification sur le MEME cas
     * (« pris en charge », puis « reponse du veterinaire ») remplace la
     * precedente dans le volet au lieu de s'empiler. Sans reference, aucune
     * etiquette : chaque notification reste visible.
     */
    static String etiquette(Notification notification) {
        if (notification.getReferenceType() == null || notification.getReferenceId() == null) {
            return null;
        }
        return notification.getReferenceType() + "-" + notification.getReferenceId();
    }

    private static AndroidNotification apparence(Notification notification) {
        AndroidNotification.Builder apparence = AndroidNotification.builder()
                .setChannelId(categorie(notification.getType()));
        String etiquette = etiquette(notification);
        if (etiquette != null) {
            apparence.setTag(etiquette);
        }
        return apparence.build();
    }

    private static Map<String, String> donnees(Notification notification) {
        return Map.of(
                "type", notification.getType().name(),
                "referenceType", notification.getReferenceType() == null
                        ? "" : notification.getReferenceType(),
                "referenceId", notification.getReferenceId() == null
                        ? "" : String.valueOf(notification.getReferenceId()));
    }

    /**
     * Repere les jetons a desactiver.
     *
     * <p>Les reponses sont dans le MEME ORDRE que les jetons envoyes : c'est
     * ce que garantit l'API de Firebase, et c'est ce qui permet d'associer un
     * echec au bon jeton. Un tri, un filtrage ou un {@code Set} entre les deux
     * casserait silencieusement cette correspondance, et on desactiverait le
     * telephone de quelqu'un d'autre.
     */
    private static List<String> jetonsAOublier(BatchResponse reponse, List<String> jetons) {
        List<SendResponse> reponses = reponse.getResponses();
        List<String> aOublier = new ArrayList<>();
        for (int i = 0; i < reponses.size() && i < jetons.size(); i++) {
            SendResponse r = reponses.get(i);
            if (r.isSuccessful()) {
                continue;
            }
            FirebaseMessagingException erreur = r.getException();
            if (erreur != null && erreur.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                aOublier.add(jetons.get(i));
            }
        }
        if (reponse.getFailureCount() > 0) {
            JOURNAL.debug("Firebase : {} envoi(s) en echec, {} jeton(s) desactive(s)",
                    reponse.getFailureCount(), aOublier.size());
        }
        return aOublier;
    }
}
