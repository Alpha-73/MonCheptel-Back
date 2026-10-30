package ml.moncheptel.api.modele;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Une alerte avec une date de fin se clot le lendemain de ce jour, pas avant. */
class FinAlerteTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 7);

    private static Alerte alerte(LocalDate fin) {
        Alerte a = new Alerte(mock(Maladie.class), mock(Zone.class), null, "Message", 3,
                mock(Administrateur.class));
        a.setDateFin(fin);
        return a;
    }

    @Test
    @DisplayName("sans date de fin, une alerte n'echoit jamais")
    void sansDate() {
        assertThat(alerte(null).estEchue(AUJOURDHUI)).isFalse();
    }

    @Test
    @DisplayName("le dernier jour, l'alerte est encore active")
    void dernierJour() {
        assertThat(alerte(AUJOURDHUI).estEchue(AUJOURDHUI)).isFalse();
    }

    @Test
    @DisplayName("le lendemain, elle est echue ; une fois close, elle ne l'est plus")
    void lendemain() {
        Alerte a = alerte(AUJOURDHUI.minusDays(1));
        assertThat(a.estEchue(AUJOURDHUI)).isTrue();
        a.cloturer();
        assertThat(a.estEchue(AUJOURDHUI)).isFalse();
    }
}
