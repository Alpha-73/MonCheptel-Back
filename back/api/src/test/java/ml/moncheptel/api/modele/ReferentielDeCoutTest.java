package ml.moncheptel.api.modele;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.modele.enumeration.ModeQuantite;
import ml.moncheptel.api.modele.enumeration.PhaseCout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Les chiffres de l'ecran « Investir - Detail volaille », recalcules.
 *
 * <p>Ce test est la garantie que le simulateur ne derivera pas. Si quelqu'un
 * change une formule, ce sont ces cinq totaux qui tombent — et ils sont
 * verifiables : ils sont ecrits sur la maquette.
 */
class ReferentielDeCoutTest {

    /** Le referentiel volaille, tel que « Admin - Referentiel fiche » l'affiche. */
    private static TypeElevage pouletDeChair() {
        TypeElevage t = new TypeElevage(Filiere.VOLAILLE, "Poulet de chair",
                ModeEvaluation.PAR_PERIODE);
        t.setDureeCycleJours(45);
        t.setCapaciteReference(100);
        t.setEspaceParSujetM2(new BigDecimal("0.100"));
        t.setAlimentParSujetKg(new BigDecimal("1.800"));
        t.setTauxMortalite(new BigDecimal("0.0800"));

        t.ajouterPosteCout(new PosteCout(t, "Logement", 1800, "m2",
                PhaseCout.INSTALLATION, ModeQuantite.PAR_CAPACITE, new BigDecimal("0.1000"), 1));
        t.ajouterPosteCout(new PosteCout(t, "Abreuvoirs et mangeoires", 6000, "forfait",
                PhaseCout.INSTALLATION, ModeQuantite.FORFAIT, BigDecimal.ONE, 2));
        t.ajouterPosteCout(new PosteCout(t, "Litiere, mise en place", 2000, "forfait",
                PhaseCout.INSTALLATION, ModeQuantite.FORFAIT, BigDecimal.ONE, 3));

        t.ajouterPosteCout(new PosteCout(t, "Achat des sujets", 600, "sujet",
                PhaseCout.CYCLE, ModeQuantite.PAR_SUJET, BigDecimal.ONE, 4));
        t.ajouterPosteCout(new PosteCout(t, "Aliment", 345, "kg",
                PhaseCout.CYCLE, ModeQuantite.PAR_SUJET, new BigDecimal("1.8000"), 5));
        t.ajouterPosteCout(new PosteCout(t, "Vaccins et soins", 100, "sujet",
                PhaseCout.CYCLE, ModeQuantite.PAR_SUJET, BigDecimal.ONE, 6));
        t.ajouterPosteCout(new PosteCout(t, "Eau et energie", 2500, "forfait",
                PhaseCout.CYCLE, ModeQuantite.FORFAIT, BigDecimal.ONE, 7));
        t.ajouterPosteCout(new PosteCout(t, "Transport", 1500, "forfait",
                PhaseCout.CYCLE, ModeQuantite.FORFAIT, BigDecimal.ONE, 8));
        return t;
    }

    private static long totalDe(TypeElevage t, PhaseCout phase, int sujetsAchetes) {
        return t.getPostesCout().stream()
                .filter(p -> p.getPhase() == phase)
                .mapToLong(p -> p.calculer(sujetsAchetes, t.getCapaciteReference()))
                .sum();
    }

    @Test
    @DisplayName("33 sujets achetes pour 30 vendus, a 8 % de mortalite")
    void provisionneLaMortalite() {
        assertThat(pouletDeChair().sujetsAAcheter(30)).isEqualTo(33);
    }

    @Test
    @DisplayName("arrondit AU SUPERIEUR : viser 10 sujets en demande 11, pas 10")
    void arronditAuSuperieur() {
        // 10 x 1,08 = 10,8. Arrondi au plus proche, on acheterait 11 ; arrondi
        // a l'inferieur, 10 — et l'objectif serait rate une fois sur deux.
        assertThat(pouletDeChair().sujetsAAcheter(10)).isEqualTo(11);
    }

    @Test
    @DisplayName("n'applique PAS la mortalite a une filiere suivie bete par bete")
    void nAppliquePasLaMortaliteAUnAnimal() {
        TypeElevage chevre = new TypeElevage(Filiere.CAPRINS, "Chevre sahelienne",
                ModeEvaluation.PAR_ENTREE);
        chevre.setTauxMortalite(new BigDecimal("0.05"));

        // CEILING(1 x 1,05) vaudrait 2 : « achetez deux chevres pour en garder
        // une ». Le simulateur doublait alors tous les couts par sujet.
        assertThat(chevre.sujetsAAcheter(1)).isEqualTo(1);
        assertThat(chevre.sujetsAAcheter(4)).isEqualTo(4);
    }

    @Test
    @DisplayName("cout du cycle : 47 593 FCFA, comme la maquette")
    void coutDuCycle() {
        TypeElevage t = pouletDeChair();
        assertThat(totalDe(t, PhaseCout.CYCLE, t.sujetsAAcheter(30))).isEqualTo(47_593L);
    }

    @Test
    @DisplayName("cout de l'installation : 26 000 FCFA, comme la maquette")
    void coutDeLInstallation() {
        TypeElevage t = pouletDeChair();
        assertThat(totalDe(t, PhaseCout.INSTALLATION, t.sujetsAAcheter(30))).isEqualTo(26_000L);
    }

    @Test
    @DisplayName("depart de zero : 73 593 FCFA, et il reste 2 407 sur un budget de 50 000")
    void departDeZeroEtResteSurBudget() {
        TypeElevage t = pouletDeChair();
        int achetes = t.sujetsAAcheter(30);
        long cycle = totalDe(t, PhaseCout.CYCLE, achetes);
        long installation = totalDe(t, PhaseCout.INSTALLATION, achetes);

        assertThat(installation + cycle).isEqualTo(73_593L);
        assertThat(50_000L - cycle).isEqualTo(2_407L);
    }

    @Test
    @DisplayName("la ligne d'aliment se decompose en 59,4 kg x 345 FCFA")
    void decomposeLaLigneDAliment() {
        TypeElevage t = pouletDeChair();
        int achetes = t.sujetsAAcheter(30);

        PosteCout aliment = t.getPostesCout().stream()
                .filter(p -> p.getLibelle().equals("Aliment")).findFirst().orElseThrow();

        BigDecimal quantite = BigDecimal.valueOf(achetes).multiply(aliment.getCoefficient());
        assertThat(quantite).isEqualByComparingTo("59.4");
        assertThat(aliment.getMontant()).isEqualTo(345L);
        assertThat(aliment.calculer(achetes, t.getCapaciteReference())).isEqualTo(20_493L);
    }

    @Test
    @DisplayName("le batiment fait 10 m² pour la capacite de reference")
    void dimensionneLeBatiment() {
        TypeElevage t = pouletDeChair();
        BigDecimal surface = BigDecimal.valueOf(t.getCapaciteReference())
                .multiply(t.getEspaceParSujetM2());
        assertThat(surface).isEqualByComparingTo("10.0");
    }
}
