package ml.moncheptel.api.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import ml.moncheptel.api.mapper.NotificationMapper;
import ml.moncheptel.api.modele.JetonAppareil;
import ml.moncheptel.api.modele.Notification;
import ml.moncheptel.api.modele.Utilisateur;
import ml.moncheptel.api.modele.enumeration.TypeNotification;
import ml.moncheptel.api.repository.JetonAppareilRepository;
import ml.moncheptel.api.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Une notification n'est poussee qu'apres la validation de la transaction (audit global G8). */
class NotificationApresValidationTest {

    private NotificationRepository notifications;
    private PasserelleNotification passerelle;
    private NotificationService service;
    private Utilisateur destinataire;

    @BeforeEach
    void preparer() {
        notifications = mock(NotificationRepository.class);
        JetonAppareilRepository jetons = mock(JetonAppareilRepository.class);
        passerelle = mock(PasserelleNotification.class);
        service = new NotificationService(notifications, jetons, passerelle,
                mock(NotificationMapper.class), mock(PlatformTransactionManager.class));
        destinataire = mock(Utilisateur.class);
        when(destinataire.getId()).thenReturn(5L);
        JetonAppareil jeton = mock(JetonAppareil.class);
        when(jeton.getJeton()).thenReturn("jeton-fcm");
        when(jetons.findByUtilisateurIdAndActifTrue(5L)).thenReturn(List.of(jeton));
        when(notifications.save(any())).thenAnswer(i -> i.getArgument(0));
        when(notifications.findById(any())).thenAnswer(i -> Optional.empty());
        when(passerelle.envoyer(any(), any())).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void nettoyer() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private Notification creer() {
        Notification n = service.creer(destinataire, TypeNotification.ORIENTATION,
                "Cas pris en charge", "corps", "SIGNALEMENT", 1L);
        when(notifications.findById(any())).thenReturn(Optional.of(n));
        return n;
    }

    @Test
    @DisplayName("rien n'est envoye avant le commit ; l'envoi part apres")
    void envoiApresCommit() {
        Notification n = creer();
        verify(passerelle, never()).envoyer(any(), any());

        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
        }
        verify(passerelle).envoyer(n, List.of("jeton-fcm"));
    }

    @Test
    @DisplayName("transaction annulee : le telephone ne sonne pas")
    void rienSiAnnulation() {
        creer();
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(passerelle, never()).envoyer(any(), any());
    }
}
