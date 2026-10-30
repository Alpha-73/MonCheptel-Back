package ml.moncheptel.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import ml.moncheptel.api.dto.requete.PriseEnChargeRequete;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.mapper.MaladieMapper;
import ml.moncheptel.api.mapper.SignalementMapper;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.Lot;
import ml.moncheptel.api.modele.Maladie;
import ml.moncheptel.api.modele.SignalementMaladie;
import ml.moncheptel.api.modele.TypeElevage;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;
import ml.moncheptel.api.modele.enumeration.TrancheEffectif;
import ml.moncheptel.api.modele.enumeration.TypeNotification;
import ml.moncheptel.api.repository.MaladieRepository;
import ml.moncheptel.api.repository.SignalementMaladieRepository;
import ml.moncheptel.api.repository.SymptomeRepository;
import ml.moncheptel.api.repository.UniteElevageRepository;
import ml.moncheptel.api.repository.VeterinaireRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Suivi d'un signalement (audits du 7 octobre) : lecture par l'eleveur ou le
 * veterinaire en charge, prise en charge notifiee, maladie confirmee
 * controlee, notification de cloture lisible.
 */
class SuiviSignalementEleveurTest {

    private static final long ID = 42L;

    private SignalementMaladieRepository signalements;
    private MaladieRepository maladies;
    private NotificationService notifications;
    private SignalementMapper mapper;
    private SignalementService service;

    private Eleveur eleveur;
    private Veterinaire veterinaire;
    private SignalementMaladie signalement;

    @BeforeEach
    void preparer() {
        signalements = mock(SignalementMaladieRepository.class);
        maladies = mock(MaladieRepository.class);
        notifications = mock(NotificationService.class);
        mapper = mock(SignalementMapper.class);
        service = new SignalementService(signalements, mock(UniteElevageRepository.class),
                mock(SymptomeRepository.class), mock(VeterinaireRepository.class),
                mock(DiagnosticService.class), notifications, mapper, mock(MaladieMapper.class),
                mock(OrientationService.class), maladies,
                mock(ml.moncheptel.api.mapper.UtilisateurMapper.class));

        eleveur = mock(Eleveur.class);
        when(eleveur.getId()).thenReturn(5L);
        veterinaire = mock(Veterinaire.class);
        when(veterinaire.getId()).thenReturn(9L);
        when(veterinaire.getNom()).thenReturn("Aminata Sow");

        TypeElevage poulet = new TypeElevage(Filiere.VOLAILLE, "Poulet de chair", ModeEvaluation.PAR_PERIODE);
        poulet.setDureeCycleJours(45);
        Lot lot = new Lot("LOT-014", eleveur, poulet, 180, LocalDate.of(2026, 9, 1));
        signalement = new SignalementMaladie(eleveur, lot, Set.of(1L), TrancheEffectif.MOINS_DE_10, null);
        signalement.orienterVers(veterinaire);
        org.springframework.test.util.ReflectionTestUtils.setField(signalement, "id", ID);
        when(signalements.findById(ID)).thenReturn(Optional.of(signalement));
    }

    private static Maladie maladie(String nom, Filiere filiere, boolean active) {
        Maladie m = new Maladie(nom, NiveauGravite.ELEVE, Set.of(filiere));
        m.setActive(active);
        return m;
    }

    @Test
    @DisplayName("l'eleveur proprietaire lit son signalement, un autre eleveur recoit 404")
    void detailEleveur() {
        service.detail(eleveur, ID);
        verify(mapper).versReponseEleveur(eq(signalement), any());

        Eleveur voisin = mock(Eleveur.class);
        when(voisin.getId()).thenReturn(6L);
        assertThatThrownBy(() -> service.detail(voisin, ID))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    @DisplayName("le veterinaire en charge lit le cas en vue praticien, un confrere recoit 404")
    void detailVeterinaire() {
        service.detail(veterinaire, ID);
        verify(mapper).versReponseVeterinaire(eq(signalement), any());

        Veterinaire confrere = mock(Veterinaire.class);
        when(confrere.getId()).thenReturn(10L);
        assertThatThrownBy(() -> service.detail(confrere, ID))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    @DisplayName("la prise en charge previent l'eleveur, avec une reference vers le cas")
    void priseEnChargeNotifiee() {
        service.prendreEnCharge(veterinaire, ID);

        assertThat(signalement.getStatut()).isEqualTo(StatutSignalement.EN_COURS);
        verify(notifications).creer(eq(eleveur), eq(TypeNotification.ORIENTATION),
                contains("a accepté votre cas"), contains("Dr Aminata Sow"), eq("SIGNALEMENT"), eq(ID));
    }

    @Test
    @DisplayName("une seconde prise en charge est refusee avec un message sans code technique")
    void secondePriseEnCharge() {
        service.prendreEnCharge(veterinaire, ID);
        assertThatThrownBy(() -> service.prendreEnCharge(veterinaire, ID))
                .isInstanceOf(RegleMetierException.class)
                .hasMessage("Vous avez déjà pris ce cas en charge.");
    }

    @Test
    @DisplayName("une maladie retiree du catalogue ne peut pas etre confirmee")
    void refuseUneMaladieRetiree() {
        when(maladies.findById(3L)).thenReturn(Optional.of(maladie("Gumboro", Filiere.VOLAILLE, false)));

        assertThatThrownBy(() -> service.cloturer(veterinaire, ID, new PriseEnChargeRequete("Vu", 3L)))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("retirée du catalogue");
        assertThat(signalement.getStatut()).isEqualTo(StatutSignalement.ORIENTE);
        verify(notifications, never()).creer(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("une maladie d'une autre filiere que l'unite ne peut pas etre confirmee")
    void refuseUneAutreFiliere() {
        when(maladies.findById(4L)).thenReturn(Optional.of(maladie("Fièvre aphteuse", Filiere.BOVINS, true)));

        assertThatThrownBy(() -> service.cloturer(veterinaire, ID, new PriseEnChargeRequete("Vu", 4L)))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Volaille");
    }

    @Test
    @DisplayName("la cloture notifie l'eleveur en clair, avec la maladie retenue")
    void clotureLisible() {
        when(maladies.findById(1L)).thenReturn(Optional.of(maladie("Maladie de Newcastle", Filiere.VOLAILLE, true)));

        service.cloturer(veterinaire, ID, new PriseEnChargeRequete("Lot vacciné", 1L));

        assertThat(signalement.getStatut()).isEqualTo(StatutSignalement.TRAITE);
        verify(notifications).creer(eq(eleveur), eq(TypeNotification.ORIENTATION),
                contains("LOT-014"), contains("a répondu à votre signalement : Maladie de Newcastle"),
                eq("SIGNALEMENT"), eq(ID));
    }
}
