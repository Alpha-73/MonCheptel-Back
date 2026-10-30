package ml.moncheptel.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.modele.RappelSanitaire;
import ml.moncheptel.api.modele.RefusSignalement;
import ml.moncheptel.api.modele.SignalementMaladie;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.modele.Zone;
import ml.moncheptel.api.modele.enumeration.StatutRappel;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import ml.moncheptel.api.modele.enumeration.TypeEvenement;
import ml.moncheptel.api.modele.enumeration.TypeNotification;
import ml.moncheptel.api.repository.RefusSignalementRepository;
import ml.moncheptel.api.repository.SignalementMaladieRepository;
import ml.moncheptel.api.repository.VeterinaireRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Audit des regles metier du 29 octobre : le veterinaire accepte ou refuse
 * un cas confie, et les cas sans reponse sont relances (V14).
 */
class RefusEtRelancesTest {

    private static final long ZONE = 1L;
    private static final long MALADIE = 7L;
    private static final Instant MAINTENANT = Instant.parse("2026-10-29T12:00:00Z");

    private SignalementMaladieRepository signalements;
    private VeterinaireRepository veterinaires;
    private NotificationService notifications;
    private RefusSignalementRepository refus;
    private AvisAdministration avis;
    private OrientationService service;

    @BeforeEach
    void preparer() {
        signalements = mock(SignalementMaladieRepository.class);
        veterinaires = mock(VeterinaireRepository.class);
        notifications = mock(NotificationService.class);
        refus = mock(RefusSignalementRepository.class);
        avis = mock(AvisAdministration.class);
        service = new OrientationService(signalements, veterinaires, notifications);
        service.setRefusRepository(refus);
        service.setAvisAdministration(avis);
        service.setDelais(Duration.ofHours(2), Duration.ofHours(12));
    }

    private static SignalementMaladie cas(StatutSignalement statut, boolean urgent) {
        SignalementMaladie s = mock(SignalementMaladie.class, RETURNS_DEEP_STUBS);
        when(s.getId()).thenReturn(40L);
        when(s.getStatut()).thenReturn(statut);
        when(s.doitEtreOriente()).thenReturn(urgent);
        when(s.getEleveur().getZone().getId()).thenReturn(ZONE);
        when(s.getEleveur().getNom()).thenReturn("Balla Kone");
        when(s.getMaladieProbable().getId()).thenReturn(MALADIE);
        when(s.getUnite().getCode()).thenReturn("LOT-014");
        when(s.getUnite().getStatut()).thenReturn(StatutUnite.ACTIF);
        when(s.getDateRelanceVeterinaire()).thenReturn(null);
        return s;
    }

    private static Veterinaire veto(long id) {
        Veterinaire v = mock(Veterinaire.class);
        when(v.getId()).thenReturn(id);
        when(v.getNom()).thenReturn("Aissata Traore");
        when(v.isActif()).thenReturn(true);
        when(v.isDisponible()).thenReturn(true);
        Zone z = mock(Zone.class);
        when(z.getId()).thenReturn(ZONE);
        when(v.getZoneIntervention()).thenReturn(z);
        return v;
    }

    // ── refus ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refuser un cas confie : refus garde, cas remis en attente, eleveur prevenu avec le motif")
    void refusPrevientEleveur() {
        SignalementMaladie s = cas(StatutSignalement.ORIENTE, false);
        Veterinaire v = veto(10);
        var eleveur = s.getEleveur();

        service.refuser(s, v, "En déplacement à Ségou");

        verify(refus).save(any(RefusSignalement.class));
        verify(s).detacherDuVeterinaire();
        verify(notifications).creer(eq(eleveur), eq(TypeNotification.ORIENTATION),
                eq("Dr Aissata Traore ne peut pas prendre votre cas"),
                contains("En déplacement à Ségou"), eq("SIGNALEMENT"), eq(40L));
    }

    @Test
    @DisplayName("un cas deja accepte ne se refuse plus : il se cloture")
    void casAccepteNeSeRefusePas() {
        SignalementMaladie s = cas(StatutSignalement.EN_COURS, false);
        assertThatThrownBy(() -> service.refuser(s, veto(10), null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("clôturez-le");
        verify(s, never()).detacherDuVeterinaire();
    }

    @Test
    @DisplayName("refus d'un cas urgent sans autre veterinaire possible : l'administration est prevenue")
    void refusUrgentSansAutre() {
        SignalementMaladie s = cas(StatutSignalement.ORIENTE, true);
        when(veterinaires.chercherCompetents(MALADIE, ZONE)).thenReturn(List.of());

        service.refuser(s, veto(10), null);

        verify(avis).prevenir(eq(TypeNotification.ORIENTATION), eq("Cas grave sans vétérinaire"),
                anyString(), eq("SIGNALEMENT"), eq(40L));
    }

    @Test
    @DisplayName("un veterinaire qui a refuse ce cas ne le revoit plus dans la liste de l'eleveur")
    void refuseNonPropose() {
        SignalementMaladie s = cas(StatutSignalement.NOUVEAU, false);
        Veterinaire a = veto(10);
        Veterinaire b = veto(11);
        when(veterinaires.chercherCompetents(MALADIE, ZONE)).thenReturn(List.of(a, b));
        when(refus.veterinairesAyantRefuse(40L)).thenReturn(List.of(10L));

        assertThat(service.candidats(s)).containsExactly(b);
    }

    // ── relances ─────────────────────────────────────────────────────────

    private SignalementMaladie confieDepuis(Duration attente, boolean urgent, Veterinaire v) {
        SignalementMaladie s = cas(StatutSignalement.ORIENTE, urgent);
        when(s.getVeterinaire()).thenReturn(v);
        when(s.debutAttente()).thenReturn(MAINTENANT.minus(attente));
        when(signalements.chercherARelancer()).thenReturn(List.of(s));
        return s;
    }

    @Test
    @DisplayName("cas urgent sans reponse depuis 3 h : rappel au veterinaire, une fois")
    void rappelAuVeterinaire() {
        Veterinaire v = veto(10);
        SignalementMaladie s = confieDepuis(Duration.ofHours(3), true, v);

        assertThat(service.relancer(MAINTENANT)).isEqualTo(1);

        verify(notifications).creer(eq(v), eq(TypeNotification.ORIENTATION),
                eq("Un éleveur attend votre réponse"), anyString(), eq("SIGNALEMENT"), eq(40L));
        verify(s).marquerRelanceVeterinaire(MAINTENANT);
        verify(s, never()).marquerRelanceEleveur(any());
    }

    @Test
    @DisplayName("cas urgent sans reponse depuis 5 h : l'eleveur peut changer, l'administration est prevenue")
    void invitationEleveurEtAdministration() {
        Veterinaire v = veto(10);
        SignalementMaladie s = confieDepuis(Duration.ofHours(5), true, v);
        var eleveur = s.getEleveur();

        service.relancer(MAINTENANT);

        verify(notifications).creer(eq(eleveur), eq(TypeNotification.ORIENTATION),
                eq("Dr Aissata Traore n'a pas encore répondu"), anyString(), eq("SIGNALEMENT"), eq(40L));
        verify(avis).prevenir(eq(TypeNotification.ORIENTATION), eq("Cas grave sans réponse"),
                anyString(), eq("SIGNALEMENT"), eq(40L));
        verify(s).marquerRelanceEleveur(MAINTENANT);
    }

    @Test
    @DisplayName("cas ordinaire confie depuis 3 h : le delai de 12 h n'est pas atteint, rien ne part")
    void delaiNormalNonAtteint() {
        SignalementMaladie s = confieDepuis(Duration.ofHours(3), false, veto(10));

        assertThat(service.relancer(MAINTENANT)).isZero();

        verify(notifications, never()).creer(any(), any(), anyString(), anyString(), anyString(), anyLong());
        verify(s, never()).marquerRelanceVeterinaire(any());
    }

    @Test
    @DisplayName("cas urgent reste sans veterinaire : l'eleveur est invite a choisir, l'administration prevenue")
    void urgentSansVeterinaire() {
        SignalementMaladie s = cas(StatutSignalement.NOUVEAU, true);
        when(s.debutAttente()).thenReturn(MAINTENANT.minus(Duration.ofHours(3)));
        when(signalements.chercherARelancer()).thenReturn(List.of(s));
        when(veterinaires.chercherCompetents(MALADIE, ZONE)).thenReturn(List.of(veto(10)));
        var eleveur = s.getEleveur();

        service.relancer(MAINTENANT);

        verify(notifications).creer(eq(eleveur), eq(TypeNotification.ORIENTATION),
                eq("Choisissez un vétérinaire"), anyString(), eq("SIGNALEMENT"), eq(40L));
        verify(avis).prevenir(eq(TypeNotification.ORIENTATION), eq("Cas grave sans vétérinaire"),
                anyString(), eq("SIGNALEMENT"), eq(40L));
        verify(s).marquerRelanceEleveur(MAINTENANT);
    }

    @Test
    @DisplayName("aucune relance pour un elevage sorti du cheptel")
    void elevageSorti() {
        SignalementMaladie s = confieDepuis(Duration.ofHours(30), true, veto(10));
        when(s.getUnite().getStatut()).thenReturn(StatutUnite.CLOTURE);

        assertThat(service.relancer(MAINTENANT)).isZero();
    }

    @Test
    @DisplayName("format des durees : 3 h, 1 j, 1 j 4 h")
    void formatHeures() {
        assertThat(OrientationService.heures(Duration.ofHours(3))).isEqualTo("3 h");
        assertThat(OrientationService.heures(Duration.ofHours(24))).isEqualTo("1 j");
        assertThat(OrientationService.heures(Duration.ofHours(28))).isEqualTo("1 j 4 h");
    }

    // ── rappels rouverts a l'annulation d'une sortie ─────────────────────

    @Test
    @DisplayName("annuler la sortie rouvre les rappels ecartes par elle, pas ceux ignores par l'eleveur")
    void rappelsRouverts() {
        RappelSanitaire parLaSortie = RappelSanitaire.libre(null, TypeEvenement.VACCINATION,
                "Rappel Newcastle", LocalDate.now().plusDays(3));
        RappelSanitaire parEleveur = RappelSanitaire.libre(null, TypeEvenement.VACCINATION,
                "Vermifuge", LocalDate.now().plusDays(3));
        parLaSortie.ecarterALaSortie();
        parEleveur.ignorer();

        parLaSortie.rouvrirApresSortieAnnulee();
        parEleveur.rouvrirApresSortieAnnulee();

        assertThat(parLaSortie.getStatut()).isEqualTo(StatutRappel.A_VENIR);
        assertThat(parLaSortie.isEcarteALaSortie()).isFalse();
        assertThat(parEleveur.getStatut()).isEqualTo(StatutRappel.IGNORE);
    }
}
