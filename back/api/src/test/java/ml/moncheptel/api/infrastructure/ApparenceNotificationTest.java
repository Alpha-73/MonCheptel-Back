package ml.moncheptel.api.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ml.moncheptel.api.modele.Notification;
import ml.moncheptel.api.modele.enumeration.TypeNotification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Apparence des notifications poussees (audit du 11 octobre). */
class ApparenceNotificationTest {

    @Test
    @DisplayName("cas et alertes : categorie urgente ; rappels et moderation : informations")
    void categories() {
        assertThat(PasserelleFirebase.categorie(TypeNotification.ORIENTATION))
                .isEqualTo(PasserelleFirebase.CATEGORIE_URGENTE);
        assertThat(PasserelleFirebase.categorie(TypeNotification.ALERTE))
                .isEqualTo(PasserelleFirebase.CATEGORIE_URGENTE);
        assertThat(PasserelleFirebase.categorie(TypeNotification.RAPPEL))
                .isEqualTo(PasserelleFirebase.CATEGORIE_INFOS);
        assertThat(PasserelleFirebase.categorie(TypeNotification.MODERATION))
                .isEqualTo(PasserelleFirebase.CATEGORIE_INFOS);
        assertThat(PasserelleFirebase.categorie(TypeNotification.SYSTEME))
                .isEqualTo(PasserelleFirebase.CATEGORIE_INFOS);
    }

    @Test
    @DisplayName("deux notifications sur le meme cas portent la meme etiquette ; sans reference, aucune")
    void etiquettes() {
        Notification priseEnCharge = mock(Notification.class);
        when(priseEnCharge.getReferenceType()).thenReturn("SIGNALEMENT");
        when(priseEnCharge.getReferenceId()).thenReturn(42L);
        Notification reponse = mock(Notification.class);
        when(reponse.getReferenceType()).thenReturn("SIGNALEMENT");
        when(reponse.getReferenceId()).thenReturn(42L);
        Notification rappel = mock(Notification.class);

        assertThat(PasserelleFirebase.etiquette(priseEnCharge)).isEqualTo("SIGNALEMENT-42");
        assertThat(PasserelleFirebase.etiquette(reponse)).isEqualTo(PasserelleFirebase.etiquette(priseEnCharge));
        assertThat(PasserelleFirebase.etiquette(rappel)).isNull();
    }
}
