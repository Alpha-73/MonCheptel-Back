package ml.moncheptel.api.modele;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;
import ml.moncheptel.api.modele.enumeration.TrancheEffectif;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La gravite d'un signalement ne vient pas de la maladie seule.
 *
 * <p>Un sujet malade sur cent-quatre-vingts n'est pas une epidemie ; cinquante
 * sujets atteints d'une maladie benigne le sont presque. C'est le croisement
 * des deux qui decide de l'orientation.
 */
class DiagnosticEtGraviteTest {

    private static Eleveur eleveur() {
        return new Eleveur("Balla Kone", "70000000", "empreinte", null, Set.of(Filiere.VOLAILLE));
    }

    private static Lot lot() {
        TypeElevage t = new TypeElevage(Filiere.VOLAILLE, "Poulet de chair",
                ModeEvaluation.PAR_PERIODE);
        t.setDureeCycleJours(45);
        return new Lot("LOT-014", eleveur(), t, 180, java.time.LocalDate.of(2026, 9, 1));
    }

    private static SignalementMaladie signalement(TrancheEffectif tranche) {
        return new SignalementMaladie(eleveur(), lot(), Set.of(1L, 2L, 4L), tranche, null);
    }

    private static Maladie maladie(String nom, NiveauGravite gravite, boolean oriente) {
        Maladie m = new Maladie(nom, gravite, Set.of(Filiere.VOLAILLE));
        m.setOrienteVersVeterinaire(oriente);
        m.setTraitement("Protocole reserve au praticien");
        return m;
    }

    @Test
    @DisplayName("la gravite retenue est la plus forte des deux")
    void croiseLaGraviteEtLAmpleur() {
        SignalementMaladie peu = signalement(TrancheEffectif.MOINS_DE_10);
        peu.enregistrerDiagnostic(maladie("Newcastle", NiveauGravite.ELEVE, true),
                new BigDecimal("0.889"), null, null);
        assertThat(peu.getGravite()).isEqualTo(NiveauGravite.ELEVE);

        SignalementMaladie beaucoup = signalement(TrancheEffectif.PLUS_DE_50);
        beaucoup.enregistrerDiagnostic(maladie("Coccidiose", NiveauGravite.MOYEN, false),
                new BigDecimal("0.750"), null, null);
        // Maladie moyenne, mais plus de cinquante sujets touches : l'ampleur
        // fait monter la gravite.
        assertThat(beaucoup.getGravite()).isEqualTo(NiveauGravite.ELEVE);
    }

    @Test
    @DisplayName("oriente d'office une maladie qui exige un veterinaire, meme sur peu de sujets")
    void orienteSurLaMaladie() {
        SignalementMaladie s = signalement(TrancheEffectif.MOINS_DE_10);
        s.enregistrerDiagnostic(maladie("Newcastle", NiveauGravite.ELEVE, true),
                new BigDecimal("0.889"), null, null);
        assertThat(s.doitEtreOriente()).isTrue();
    }

    @Test
    @DisplayName("n'oriente pas une maladie benigne sur peu de sujets")
    void nOrientePasUnCasBenin() {
        SignalementMaladie s = signalement(TrancheEffectif.MOINS_DE_10);
        s.enregistrerDiagnostic(maladie("Coccidiose", NiveauGravite.MOYEN, false),
                new BigDecimal("0.750"), null, null);
        assertThat(s.doitEtreOriente()).isFalse();
    }

    @Test
    @DisplayName("le traitement d'une maladie orientee ne sort pas du serveur")
    void masqueLeTraitementDUneMaladieOrientee() {
        assertThat(maladie("Newcastle", NiveauGravite.ELEVE, true).traitementAffichable()).isNull();
        assertThat(maladie("Coccidiose", NiveauGravite.MOYEN, false).traitementAffichable())
                .isEqualTo("Protocole reserve au praticien");
    }

    @Test
    @DisplayName("sans maladie probable, la gravite vient de la seule ampleur")
    void graviteSansMaladie() {
        SignalementMaladie s = signalement(TrancheEffectif.PLUS_DE_50);
        s.enregistrerDiagnostic(null, null, null, null);
        assertThat(s.getGravite()).isEqualTo(TrancheEffectif.PLUS_DE_50.graviteInduite());
    }
}
