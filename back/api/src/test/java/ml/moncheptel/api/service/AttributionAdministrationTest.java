package ml.moncheptel.api.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.modele.SignalementMaladie;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;
import ml.moncheptel.api.repository.SignalementMaladieRepository;
import ml.moncheptel.api.repository.VeterinaireRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Attribution manuelle d'un cas par l'administration (audit console v2, A31). */
class AttributionAdministrationTest {

    private SignalementMaladieRepository signalements;
    private VeterinaireRepository veterinaires;
    private OrientationService orientation;
    private SuiviSignalementService service;
    private SignalementMaladie cas;
    private Veterinaire veto;

    @BeforeEach
    void preparer() {
        signalements = mock(SignalementMaladieRepository.class);
        veterinaires = mock(VeterinaireRepository.class);
        orientation = mock(OrientationService.class);
        service = new SuiviSignalementService(signalements, veterinaires, orientation);
        cas = mock(SignalementMaladie.class);
        veto = mock(Veterinaire.class);
        when(veto.getId()).thenReturn(7L);
        when(veto.getNom()).thenReturn("Dr Sow");
        when(veto.isActif()).thenReturn(true);
        when(signalements.findById(1L)).thenReturn(Optional.of(cas));
        when(veterinaires.findById(7L)).thenReturn(Optional.of(veto));
    }

    @Test
    @DisplayName("un cas traite ne se reattribue plus")
    void casTraite() {
        when(cas.getStatut()).thenReturn(StatutSignalement.TRAITE);
        assertThatThrownBy(() -> service.attribuer(1L, 7L)).isInstanceOf(RegleMetierException.class);
        verify(orientation, never()).attribuer(any(), any());
    }

    @Test
    @DisplayName("un veterinaire desactive est refuse")
    void veterinaireDesactive() {
        when(cas.getStatut()).thenReturn(StatutSignalement.NOUVEAU);
        when(veto.isActif()).thenReturn(false);
        assertThatThrownBy(() -> service.attribuer(1L, 7L)).isInstanceOf(RegleMetierException.class);
        verify(orientation, never()).attribuer(any(), any());
    }

    @Test
    @DisplayName("le meme veterinaire est refuse")
    void memeVeterinaire() {
        when(cas.getStatut()).thenReturn(StatutSignalement.ORIENTE);
        when(cas.getVeterinaire()).thenReturn(veto);
        assertThatThrownBy(() -> service.attribuer(1L, 7L)).isInstanceOf(RegleMetierException.class);
        verify(orientation, never()).attribuer(any(), any());
    }
}
