package ml.moncheptel.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import ml.moncheptel.api.modele.Zone;
import ml.moncheptel.api.modele.SignalementMaladie;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;
import ml.moncheptel.api.modele.enumeration.TypeNotification;
import ml.moncheptel.api.repository.SignalementMaladieRepository;
import ml.moncheptel.api.repository.VeterinaireRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Choix du veterinaire par l'eleveur (zone, competence, hasard) et reprise des cas. */
class OrientationServiceTest {

    private static final long ZONE = 1L;
    private static final long MALADIE = 7L;

    private SignalementMaladieRepository signalements;
    private VeterinaireRepository veterinaires;
    private OrientationService service;
    private NotificationService notifications;

    @BeforeEach
    void preparer() {
        signalements = mock(SignalementMaladieRepository.class);
        veterinaires = mock(VeterinaireRepository.class);
        notifications = mock(NotificationService.class);
        service = new OrientationService(signalements, veterinaires, notifications);
    }

    private static SignalementMaladie cas() {
        SignalementMaladie s = mock(SignalementMaladie.class, RETURNS_DEEP_STUBS);
        when(s.getEleveur().getZone().getId()).thenReturn(ZONE);
        when(s.getMaladieProbable().getId()).thenReturn(MALADIE);
        when(s.getMaladieProbable().getNom()).thenReturn("Newcastle");
        return s;
    }

    private static Veterinaire veto(long id) {
        return veto(id, ZONE);
    }

    private static Veterinaire veto(long id, long zone) {
        Veterinaire v = mock(Veterinaire.class);
        when(v.getId()).thenReturn(id);
        when(v.isDisponible()).thenReturn(true);
        when(v.isActif()).thenReturn(true);
        Zone z = mock(Zone.class);
        when(z.getId()).thenReturn(zone);
        when(v.getZoneIntervention()).thenReturn(z);
        return v;
    }

    @Test
    @DisplayName("tous les veterinaires competents de la zone sont proposes, et seulement eux")
    void tousLesCompetentsDeLaZone() {
        Veterinaire a = veto(10);
        Veterinaire b = veto(11);
        Veterinaire autreZone = veto(12, 99L);
        when(veterinaires.chercherCompetents(MALADIE, ZONE)).thenReturn(List.of(a, b, autreZone));

        assertThat(service.candidats(cas())).containsExactlyInAnyOrder(a, b);
    }

    @Test
    @DisplayName("l'ordre d'affichage est aleatoire : aucun veterinaire n'a de place reservee")
    void ordreAleatoire() {
        List<Veterinaire> liste = new java.util.ArrayList<>();
        for (long i = 1; i <= 6; i++) liste.add(veto(i));
        when(veterinaires.chercherCompetents(MALADIE, ZONE)).thenReturn(liste);
        SignalementMaladie s = cas();

        java.util.Set<Long> premiers = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            premiers.add(service.candidats(s).get(0).getId());
        }
        assertThat(premiers).hasSizeGreaterThan(3);
    }

    @Test
    @DisplayName("eleveur sans zone : personne n'est propose")
    void sansZone() {
        SignalementMaladie s = cas();
        when(s.getEleveur().getZone()).thenReturn(null);
        assertThat(service.candidats(s)).isEmpty();
    }

    @Test
    @DisplayName("l'eleveur confie le cas au veterinaire choisi")
    void choixDeLEleveur() {
        Veterinaire choisi = veto(10);
        Veterinaire autre = veto(11);
        when(veterinaires.chercherCompetents(MALADIE, ZONE)).thenReturn(List.of(autre, choisi));
        SignalementMaladie s = cas();
        when(s.getStatut()).thenReturn(StatutSignalement.NOUVEAU);
        when(s.getVeterinaire()).thenReturn(null);

        service.choisirParEleveur(s, 10L);

        verify(s).orienterVers(choisi);
    }

    @Test
    @DisplayName("un veterinaire d'une autre zone est refuse")
    void autreZoneRefusee() {
        Veterinaire ailleurs = veto(12, 99L);
        when(veterinaires.chercherCompetents(MALADIE, ZONE)).thenReturn(List.of(ailleurs));
        SignalementMaladie s = cas();
        when(s.getStatut()).thenReturn(StatutSignalement.NOUVEAU);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.choisirParEleveur(s, 12L))
                .isInstanceOf(ml.moncheptel.api.exception.RegleMetierException.class);
        verify(s, never()).orienterVers(any());
    }

    @Test
    @DisplayName("un veterinaire indisponible ne peut pas etre choisi")
    void indisponibleRefuse() {
        Veterinaire absent = veto(13);
        when(absent.isDisponible()).thenReturn(false);
        when(veterinaires.chercherCompetents(MALADIE, ZONE)).thenReturn(List.of(absent));
        SignalementMaladie s = cas();
        when(s.getStatut()).thenReturn(StatutSignalement.NOUVEAU);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.choisirParEleveur(s, 13L))
                .isInstanceOf(ml.moncheptel.api.exception.RegleMetierException.class);
        verify(s, never()).orienterVers(any());
    }

    @Test
    @DisplayName("un cas deja pris en charge ne change plus de veterinaire")
    void casPrisEnChargeFige() {
        SignalementMaladie s = cas();
        when(s.getStatut()).thenReturn(StatutSignalement.EN_COURS);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.choisirParEleveur(s, 10L))
                .isInstanceOf(ml.moncheptel.api.exception.RegleMetierException.class);
    }

    @Test
    @DisplayName("un veterinaire qui part rend ses cas : ils redeviennent nouveaux, sans reorientation")
    void reprise() {
        Veterinaire partant = veto(20);
        SignalementMaladie s = cas();
        when(s.getVeterinaire()).thenReturn(partant);
        when(signalements.findByVeterinaireIdAndStatutIn(eq(20L), any()))
                .thenReturn(List.of(s));

        service.reprendreCasDe(20L, true);

        verify(s).detacherDuVeterinaire();
        verify(s, never()).orienterVers(any());
        verify(signalements).findByVeterinaireIdAndStatutIn(20L,
                List.of(StatutSignalement.ORIENTE, StatutSignalement.EN_COURS));
    }

    @Test
    @DisplayName("changement de zone : seuls les cas auxquels il ne convient plus sont repris")
    void repriseCibleeApresChangement() {
        Veterinaire v = veto(50);
        when(v.isActif()).thenReturn(true);
        when(v.isDisponible()).thenReturn(true);
        Zone ailleurs = mock(Zone.class);
        when(ailleurs.getId()).thenReturn(99L);
        when(v.getZoneIntervention()).thenReturn(ailleurs);
        SignalementMaladie garde = cas();
        when(garde.getEleveur().getZone().getId()).thenReturn(99L);
        when(garde.getVeterinaire()).thenReturn(v);
        when(v.traite(garde.getMaladieProbable())).thenReturn(true);
        SignalementMaladie rendu = cas();
        when(rendu.getVeterinaire()).thenReturn(v);
        when(signalements.findByVeterinaireIdAndStatutIn(eq(50L), any()))
                .thenReturn(List.of(garde, rendu));

        service.reprendreCasDe(50L, false);

        verify(garde, never()).detacherDuVeterinaire();
        verify(rendu).detacherDuVeterinaire();
        verify(signalements).findByVeterinaireIdAndStatutIn(50L, List.of(StatutSignalement.ORIENTE));
    }

    @Test
    @DisplayName("il faut a la fois la zone de l'eleveur et la competence pour garder le cas")
    void convientParLaZoneEtLaCompetence() {
        Veterinaire v = veto(60);
        when(v.isActif()).thenReturn(true);
        when(v.isDisponible()).thenReturn(true);
        Zone meme = mock(Zone.class);
        when(meme.getId()).thenReturn(ZONE);
        when(v.getZoneIntervention()).thenReturn(meme);
        SignalementMaladie s = cas();

        assertThat(OrientationService.convientEncore(s, v)).isFalse();
        when(v.traite(s.getMaladieProbable())).thenReturn(true);
        assertThat(OrientationService.convientEncore(s, v)).isTrue();
        when(meme.getId()).thenReturn(99L);
        assertThat(OrientationService.convientEncore(s, v)).isFalse();
        when(meme.getId()).thenReturn(ZONE);
        when(v.isActif()).thenReturn(false);
        assertThat(OrientationService.convientEncore(s, v)).isFalse();
    }

    @Test
    @DisplayName("un veterinaire indisponible ne garde pas un cas qu'il n'a pas ouvert")
    void indisponibleNeGardePas() {
        Veterinaire v = veto(61);
        when(v.isActif()).thenReturn(true);
        when(v.isDisponible()).thenReturn(false);
        assertThat(OrientationService.convientEncore(cas(), v)).isFalse();
    }

    @Test
    @DisplayName("attribution par l'administration : le veterinaire ET l'eleveur sont prevenus")
    void attributionPrevientLesDeux() {
        SignalementMaladie s = cas();
        when(s.getVeterinaire()).thenReturn(null);
        when(s.getUnite().getCode()).thenReturn("LOT-014");
        Veterinaire v = veto(10);
        when(v.getNom()).thenReturn("Aïssata Traoré");

        var eleveur = s.getEleveur();

        service.attribuer(s, v);

        verify(s).orienterVers(v);
        verify(notifications).creer(eq(v), eq(TypeNotification.ORIENTATION), any(), any(),
                eq("SIGNALEMENT"), any());
        verify(notifications).creer(eq(eleveur), eq(TypeNotification.ORIENTATION),
                eq("Un vétérinaire a reçu votre signalement"), any(), eq("SIGNALEMENT"), any());
    }
}
