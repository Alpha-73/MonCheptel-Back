package ml.moncheptel.api.service;

import java.time.Instant;
import java.util.List;
import ml.moncheptel.api.dto.reponse.NotificationReponse;
import ml.moncheptel.api.dto.reponse.PageReponse;
import ml.moncheptel.api.mapper.NotificationMapper;
import ml.moncheptel.api.modele.JetonAppareil;
import ml.moncheptel.api.modele.Notification;
import ml.moncheptel.api.modele.Utilisateur;
import ml.moncheptel.api.modele.enumeration.TypeNotification;
import ml.moncheptel.api.repository.JetonAppareilRepository;
import ml.moncheptel.api.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creation et envoi des notifications.
 *
 * <h2>Enregistrer d'abord, envoyer ensuite</h2>
 *
 * <p>La notification est ecrite en base avant toute tentative d'envoi. Si
 * Firebase est indisponible, l'alerte epidemique existe quand meme : l'eleveur
 * la verra en ouvrant l'application, et la tache de reprise retentera le push.
 * L'inverse — envoyer puis enregistrer — perdrait la notification a la
 * moindre coupure, c'est-a-dire souvent.
 *
 * <p>Et l'envoi n'a lieu qu'APRES la validation de la transaction qui a cree
 * la notification : voir {@link #pousserApresValidation}.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    /** Au-dela, on cesse de retenter : l'appareil a ete desinstalle. */
    private static final int TENTATIVES_MAX = 5;

    /** Taille d'un lot de reprise. Evite de charger la file entiere en memoire. */
    private static final int TAILLE_LOT = 200;

    private final NotificationRepository notificationRepository;
    private final JetonAppareilRepository jetonRepository;
    private final PasserelleNotification passerelle;
    private final NotificationMapper mapper;
    private final TransactionTemplate nouvelleTransaction;

    private static final Logger JOURNAL = LoggerFactory.getLogger(NotificationService.class);

    public NotificationService(NotificationRepository notificationRepository,
                               JetonAppareilRepository jetonRepository,
                               PasserelleNotification passerelle,
                               NotificationMapper mapper,
                               PlatformTransactionManager transactions) {
        this.notificationRepository = notificationRepository;
        this.jetonRepository = jetonRepository;
        this.passerelle = passerelle;
        this.mapper = mapper;
        this.nouvelleTransaction = new TransactionTemplate(transactions);
        this.nouvelleTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public Notification creer(Utilisateur destinataire, TypeNotification type,
                              String titre, String corps,
                              String referenceType, Long referenceId) {
        Notification notification = notificationRepository.save(new Notification(
                destinataire, type, titre, corps, referenceType, referenceId));
        pousserApresValidation(notification);
        return notification;
    }

    /**
     * Pousse la notification APRES la validation de la transaction metier
     * (audit global G8).
     *
     * <p>Elle etait poussee tout de suite, dans la transaction. Si celle-ci
     * echouait ensuite (conflit {@code @Version} detecte au commit, donc 409),
     * la ligne disparaissait de la base mais le telephone avait deja sonne :
     * « Cas pris en charge » pour un cas que personne n'avait pris.
     *
     * <p>Ici la notification reste {@code EN_ATTENTE} dans la transaction.
     * Une fois celle-ci validee, l'envoi se fait dans une transaction neuve
     * qui relit la ligne. Annulee, la transaction emporte la ligne et rien
     * n'est envoye. Si l'envoi lui-meme echoue, la tache
     * {@code rejouerNotifications} le reprendra.
     *
     * <p>Sans transaction active (appel direct, tests), l'envoi est immediat.
     */
    private void pousserApresValidation(Notification notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            pousser(notification);
            return;
        }
        Long id = notification.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    nouvelleTransaction.executeWithoutResult(etat ->
                            notificationRepository.findById(id).ifPresent(n -> pousser(n)));
                } catch (RuntimeException e) {
                    // La reponse HTTP est deja decidee : la notification reste
                    // EN_ATTENTE en base, la tache de reprise la renverra.
                    JOURNAL.warn("Envoi de la notification {} reporte : {}", id, e.getMessage());
                }
            }
        });
    }

    /** Diffusion : une notification par destinataire, jamais une pour tous. */
    @Transactional
    public int creerPourChacun(List<? extends Utilisateur> destinataires, TypeNotification type,
                               String titre, String corps, String referenceType, Long referenceId) {
        for (Utilisateur destinataire : destinataires) {
            creer(destinataire, type, titre, corps, referenceType, referenceId);
        }
        return destinataires.size();
    }

    /**
     * Tente l'envoi push et met a jour le statut.
     *
     * <p>Un echec n'est jamais propage : la notification est deja en base, et
     * faire echouer la sortie d'un lot parce que Firebase tousse serait
     * absurde.
     */
    private void pousser(Notification notification) {
        List<String> jetons = jetonRepository
                .findByUtilisateurIdAndActifTrue(notification.getDestinataire().getId())
                .stream().map(JetonAppareil::getJeton).toList();

        if (jetons.isEmpty()) {
            // Pas d'appareil enregistre : rien a pousser, et ce n'est pas un
            // echec. L'utilisateur lira la notification dans l'application.
            notification.marquerEnvoyee();
            return;
        }
        try {
            List<String> invalides = passerelle.envoyer(notification, jetons);
            desactiver(invalides);
            notification.marquerEnvoyee();
        } catch (RuntimeException e) {
            notification.marquerEchec(e.getMessage(), TENTATIVES_MAX);
        }
    }

    private void desactiver(List<String> jetons) {
        for (String jeton : jetons) {
            jetonRepository.findByJeton(jeton).ifPresent(JetonAppareil::desactiver);
        }
    }

    /** Tache de reprise : rejoue les envois en attente ou en echec. */
    @Transactional
    public int rejouerEnAttente() {
        List<Notification> aRejouer = notificationRepository.chercherAEnvoyer(
                TENTATIVES_MAX, org.springframework.data.domain.PageRequest.of(0, TAILLE_LOT));
        aRejouer.forEach(this::pousser);
        return aRejouer.size();
    }

    public PageReponse<NotificationReponse> lister(Long utilisateurId, Pageable pageable) {
        var page = notificationRepository
                .findByDestinataireIdOrderByDateCreationDesc(utilisateurId, pageable);
        return PageReponse.de(page).avec(mapper.versReponses(page.getContent()));
    }

    public long compterNonLues(Long utilisateurId) {
        return notificationRepository.countByDestinataireIdAndLueFalse(utilisateurId);
    }

    @Transactional
    public int marquerToutesLues(Long utilisateurId) {
        return notificationRepository.marquerToutesLues(utilisateurId);
    }

    /** Retire les notifications qui pointent vers un objet supprime. */
    @Transactional
    public int oublierReference(String referenceType, Long referenceId) {
        return notificationRepository.supprimerPourReference(referenceType, referenceId);
    }

    /** Purge des notifications lues de plus de {@code jours} jours. */
    @Transactional
    public int purger(int jours) {
        return notificationRepository.purger(Instant.now().minusSeconds(jours * 86_400L));
    }
}
