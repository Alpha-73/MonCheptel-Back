package ml.moncheptel.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import ml.moncheptel.api.exception.ConflitException;
import ml.moncheptel.api.mapper.MaladieMapper;
import ml.moncheptel.api.mapper.ReferentielMapper;
import ml.moncheptel.api.mapper.ZoneMapper;
import ml.moncheptel.api.modele.TypeElevage;
import ml.moncheptel.api.repository.MaladieRepository;
import ml.moncheptel.api.repository.MaladieSymptomeRepository;
import ml.moncheptel.api.repository.SymptomeRepository;
import ml.moncheptel.api.repository.TypeElevageRepository;
import ml.moncheptel.api.repository.UniteElevageRepository;
import ml.moncheptel.api.repository.VeterinaireRepository;
import ml.moncheptel.api.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Type d'elevage : suppression definitive et retrait. */
class SuppressionTypeElevageTest {

    private TypeElevageRepository types;
    private UniteElevageRepository unites;
    private ReferentielService service;
    private TypeElevage type;

    @BeforeEach
    void preparer() {
        types = mock(TypeElevageRepository.class);
        unites = mock(UniteElevageRepository.class);
        service = new ReferentielService(types, mock(MaladieRepository.class),
                mock(MaladieSymptomeRepository.class), mock(SymptomeRepository.class),
                mock(ZoneRepository.class), mock(VeterinaireRepository.class), unites,
                mock(ReferentielMapper.class), mock(MaladieMapper.class), mock(ZoneMapper.class));
        type = mock(TypeElevage.class);
        when(type.getLibelle()).thenReturn("Poulet de chair");
        when(types.findById(1L)).thenReturn(Optional.of(type));
    }

    @Test
    @DisplayName("un type jamais employe est supprime definitivement")
    void suppressionDefinitive() {
        when(unites.countByTypeElevageId(1L)).thenReturn(0L);
        service.supprimerTypeElevage(1L);
        verify(types).delete(type);
    }

    @Test
    @DisplayName("un type employe par un elevage ne peut pas etre supprime : 409")
    void typeEmployeRefuse() {
        when(unites.countByTypeElevageId(1L)).thenReturn(4L);
        assertThatThrownBy(() -> service.supprimerTypeElevage(1L))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("4 élevages");
        verify(types, never()).delete(type);
    }

    @Test
    @DisplayName("le retrait reste possible et ne supprime rien")
    void retrait() {
        service.retirerTypeElevage(1L);
        verify(type).setActif(false);
        verify(types, never()).delete(type);
        assertThat(true).isTrue();
    }
}
