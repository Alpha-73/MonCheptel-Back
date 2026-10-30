package ml.moncheptel.api.config;

import ml.moncheptel.api.service.AlerteService;
import ml.moncheptel.api.service.AuthentificationService;
import ml.moncheptel.api.service.NotificationService;
import ml.moncheptel.api.service.OrientationService;
import ml.moncheptel.api.service.SanitaireService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Traitements periodiques.
 *
 * <h2>Pourquoi ces taches existent</h2>
 *
 * <p>Deux etats du systeme ne changent pas tout seuls : un rappel dont
 * l'echeance est passee reste marque « a venir » tant que personne ne le
 * relit, et une notification dont l'envoi a echoue reste en echec. Sans
 * passage periodique, l'eleveur verrait un rappel depasse presente comme a
 * venir, et ne recevrait jamais la notification perdue.
 *
 * <h2>Une seule instance</h2>
 *
 * <p>{@code @Scheduled} s'execute sur CHAQUE instance de l'application. Avec
 * une seule instance — ce qui est le cas ici — tout va bien. Le jour ou le
 * back tournera en plusieurs exemplaires, ces taches s'executeront en
 * parallele : la reevaluation des rappels est idempotente et le supporte, mais
 * la reprise des notifications enverrait des doublons. Il faudra alors un
 * verrou partage (ShedLock ou equivalent). Ce commentaire est la pour qu'on
 * s'en souvienne avant de dupliquer l'instance, pas apres.
 */
@Component
public class TachesPlanifiees {

    private static final Logger JOURNAL = LoggerFactory.getLogger(TachesPlanifiees.class);

    /** Notifications lues conservees, en jours. */
    private static final int RETENTION_JOURS = 90;

    /** Delai avant suppression d'un code de reinitialisation expire, en jours. */
    private static final int RETENTION_CODES_JOURS = 1;

    private final SanitaireService sanitaireService;
    private final NotificationService notificationService;
    private final AuthentificationService authentificationService;
    private final AlerteService alerteService;
    private final OrientationService orientationService;

    public TachesPlanifiees(SanitaireService sanitaireService,
                            NotificationService notificationService,
                            AuthentificationService authentificationService,
                            AlerteService alerteService,
                            OrientationService orientationService) {
        this.sanitaireService = sanitaireService;
        this.notificationService = notificationService;
        this.authentificationService = authentificationService;
        this.alerteService = alerteService;
        this.orientationService = orientationService;
    }

    /**
     * Relance les cas sans reponse, toutes les 15 minutes (V14) : rappel au
     * veterinaire apres le delai (2 h urgent, 12 h sinon), puis l'eleveur est
     * invite a changer de veterinaire et l'administration prevenue d'un cas
     * grave. Chaque relance part une seule fois par periode d'attente.
     */
    @Scheduled(fixedDelay = 900_000L, initialDelay = 120_000L)
    public void relancerCasSansReponse() {
        int envoyees = orientationService.relancer(java.time.Instant.now());
        if (envoyees > 0) {
            JOURNAL.info("Relances de signalements envoyees : {}", envoyees);
        }
    }

    /**
     * Clot les alertes dont la date de fin est passee, toutes les heures.
     *
     * <p>Une fois par heure plutot qu'a minuit pile : si le serveur est
     * arrete a minuit, l'alerte se clot au demarrage suivant au lieu
     * d'attendre le lendemain. Les eleveurs recoivent l'avis de fin.
     */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 60_000L)
    public void cloturerAlertesEchues() {
        int closes = alerteService.cloturerEchues();
        if (closes > 0) {
            JOURNAL.info("Alertes closes a leur date de fin : {}", closes);
        }
    }

    /**
     * Passe les rappels echus a l'etat ECHU, chaque matin a 5 h.
     *
     * <p>Avant l'heure ou un eleveur ouvre l'application : le tableau de bord
     * du matin est deja a jour quand il le regarde.
     */
    @Scheduled(cron = "0 0 5 * * *", zone = "Africa/Bamako")
    public void reevaluerRappels() {
        int touches = sanitaireService.reevaluerEcheances();
        if (touches > 0) {
            JOURNAL.info("Rappels passes a echu : {}", touches);
        }
    }

    /**
     * Notifie les soins du jour et du lendemain, chaque matin a 6 h (audit
     * eleveur F5) : apres la reevaluation de 5 h, avant que l'eleveur ne
     * parte au poulailler.
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "Africa/Bamako")
    public void notifierRappels() {
        int notifiees = sanitaireService.notifierRappels(
                java.time.LocalDate.now(java.time.ZoneId.of("Africa/Bamako")), notificationService);
        if (notifiees > 0) {
            JOURNAL.info("Rappels de soins notifies : {}", notifiees);
        }
    }

    /**
     * Rejoue les notifications en attente ou en echec, toutes les 15 minutes.
     *
     * <p>Ce n'est pas du temps reel : une notification poussee normalement
     * part des la validation de sa transaction. Cette tache ne ramasse que ce
     * qui a echoue — une coupure reseau, Firebase indisponible.
     */
    @Scheduled(fixedDelay = 900_000L)
    public void rejouerNotifications() {
        int rejouees = notificationService.rejouerEnAttente();
        if (rejouees > 0) {
            JOURNAL.info("Notifications rejouees : {}", rejouees);
        }
    }

    /** Purge des notifications lues, le dimanche a 3 h. */
    @Scheduled(cron = "0 0 3 * * SUN", zone = "Africa/Bamako")
    public void purgerNotifications() {
        int purgees = notificationService.purger(RETENTION_JOURS);
        JOURNAL.info("Notifications purgees : {}", purgees);
    }

    /**
     * Supprime les codes de reinitialisation expires, chaque nuit a 4 h.
     *
     * <h3>Pourquoi les supprimer, puisqu'ils sont deja inoperants</h3>
     *
     * <p>Un code expire ne sert plus a rien : {@code reinitialiser} verifie la
     * date avant de comparer. La purge n'est donc pas une mesure de securite,
     * c'est de l'hygiene — la table grossirait indefiniment, a raison de
     * jusqu'a trois lignes par compte et par heure.
     *
     * <p><b>La marge d'un jour est volontaire.</b> On ne supprime pas ce qui a
     * expire il y a dix minutes, mais ce qui a expire il y a plus de
     * vingt-quatre heures. Deux raisons : un utilisateur qui se plaint de ne
     * pas pouvoir se reinitialiser appelle le lendemain, et la ligne doit
     * encore etre la pour qu'on comprenne ce qui s'est passe ;
     * {@code compterDemandesDepuis} s'appuie sur l'historique de la derniere
     * heure, qu'une purge trop zelee effacerait au mauvais moment.
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Africa/Bamako")
    public void purgerCodesReinitialisation() {
        int purges = authentificationService.purgerCodesExpires(RETENTION_CODES_JOURS);
        JOURNAL.info("Codes de reinitialisation purges : {}", purges);
    }

    /**
     * Supprime les sessions expirees depuis plus de sept jours, chaque nuit a
     * 4 h 30 (audit global G15). Sept jours de marge : de quoi relire la
     * liste des sessions d'un compte apres un incident.
     */
    @Scheduled(cron = "0 30 4 * * *", zone = "Africa/Bamako")
    public void purgerSessions() {
        int purgees = authentificationService.purgerSessionsExpirees(7);
        JOURNAL.info("Sessions expirees purgees : {}", purgees);
    }
}
