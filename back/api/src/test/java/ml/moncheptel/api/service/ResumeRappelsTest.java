package ml.moncheptel.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Corps de la notification quotidienne des rappels (audit du 10 octobre).
 *
 * <p>La liste complete depassait les 500 caracteres de Notification.corps des
 * environ treize rappels le meme jour, et l'exception annulait la tache pour
 * TOUS les eleveurs.
 */
class ResumeRappelsTest {

    @Test
    @DisplayName("trois soins ou moins : tous nommes, rien d'autre")
    void peuDeSoins() {
        assertThat(SanitaireService.resumerSoins(List.of(
                "Vaccination Newcastle (J21) (LOT-014)", "Deparasitage (BOV-003)")))
                .isEqualTo("Vaccination Newcastle (J21) (LOT-014), Deparasitage (BOV-003)");
    }

    @Test
    @DisplayName("quinze soins : trois nommes, les douze autres comptes")
    void beaucoupDeSoins() {
        List<String> soins = IntStream.rangeClosed(1, 15)
                .mapToObj(i -> "Deparasitage (BOV-%03d)".formatted(i))
                .toList();
        String resume = SanitaireService.resumerSoins(soins);
        assertThat(resume).endsWith(" et 12 autres soins");
        assertThat(resume).contains("BOV-001", "BOV-003").doesNotContain("BOV-004");
    }

    @Test
    @DisplayName("libelles tres longs, mille soins : toujours sous la limite de 500")
    void sousLaLimite() {
        String long_ = "x".repeat(400);
        List<String> soins = IntStream.range(0, 1000).mapToObj(i -> long_).toList();
        // + 1 : le point final ajoute par notifierRappels.
        assertThat(SanitaireService.resumerSoins(soins).length() + 1).isLessThanOrEqualTo(500);
    }

    @Test
    @DisplayName("un seul soin restant : singulier")
    void singulier() {
        assertThat(SanitaireService.resumerSoins(List.of("a", "b", "c", "d")))
                .isEqualTo("a, b, c et 1 autre soin");
    }
}
