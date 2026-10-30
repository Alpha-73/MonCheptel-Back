package ml.moncheptel.api.modele;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.modele.enumeration.ModeQuantite;
import ml.moncheptel.api.modele.enumeration.PhaseCout;
import ml.moncheptel.api.modele.enumeration.StatutRappel;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import ml.moncheptel.api.modele.enumeration.TypeEvenement;
import ml.moncheptel.api.modele.enumeration.TypeMouvement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regles ajoutees le 7 octobre (audit eleveur F1, F2, F10, F12, L10). */
class AnnulationEtClotureTest {

    private static final LocalDate ENTREE = LocalDate.of(2026, 9, 1);

    private static Eleveur eleveur() {
        return new Eleveur("Balla Kone", "70000000", "empreinte", null,
                Set.of(Filiere.VOLAILLE, Filiere.BOVINS));
    }

    private static Lot lot(int effectif) {
        TypeElevage t = new TypeElevage(Filiere.VOLAILLE, "Poulet de chair", ModeEvaluation.PAR_PERIODE);
        t.setDureeCycleJours(45);
        return new Lot("LOT-014", eleveur(), t, effectif, ENTREE);
    }

    private static Animal vache() {
        TypeElevage t = new TypeElevage(Filiere.BOVINS, "Veau d'embouche", ModeEvaluation.PAR_ENTREE);
        return new Animal("BOV-001", eleveur(), t, ENTREE, null, null, null);
    }

    @Test
    @DisplayName("annuler une vente rend les tetes et reactive un lot que la vente avait termine")
    void restituerReactiveLeLot() {
        Lot lot = lot(50);
        lot.retirer(50, TypeMouvement.VENTE, ENTREE.plusDays(40));
        assertThat(lot.getStatut()).isEqualTo(StatutUnite.CLOTURE);

        lot.restituer(50);

        assertThat(lot.getEffectifRestant()).isEqualTo(50);
        assertThat(lot.getStatut()).isEqualTo(StatutUnite.ACTIF);
        assertThat(lot.getDateSortie()).isNull();
    }

    @Test
    @DisplayName("annuler la vente d'un animal le remet au cheptel")
    void restituerUnAnimal() {
        Animal vache = vache();
        vache.retirer(1, TypeMouvement.VENTE, ENTREE.plusDays(10));
        assertThat(vache.getStatut()).isEqualTo(StatutUnite.VENDU);

        vache.restituer(1);

        assertThat(vache.getEffectifRestant()).isEqualTo(1);
        assertThat(vache.getStatut()).isEqualTo(StatutUnite.ACTIF);
    }

    @Test
    @DisplayName("on ne restitue jamais au-dela de l'effectif d'entree")
    void restitutionBornee() {
        Lot lot = lot(50);
        lot.retirer(10, TypeMouvement.MORT, ENTREE.plusDays(3));
        assertThatThrownBy(() -> lot.restituer(11))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("effectif d'entrée");
    }

    @Test
    @DisplayName("terminer un lot qui a encore des tetes le passe a Termine, une seule fois")
    void terminerALaMain() {
        Lot lot = lot(30);
        lot.terminer(ENTREE.plusDays(46));

        assertThat(lot.getStatut()).isEqualTo(StatutUnite.CLOTURE);
        assertThat(lot.getStatut().getLibelle()).isEqualTo("Clôturé");
        assertThat(lot.getEffectifRestant()).isEqualTo(30);
        assertThat(lot.getDateSortie()).isEqualTo(ENTREE.plusDays(46));
        assertThatThrownBy(() -> lot.terminer(ENTREE.plusDays(47)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà clôturé");
    }

    @Test
    @DisplayName("les messages de sortie sont accentues et accordes")
    void messagesAccentues() {
        Lot lot = lot(1);
        assertThatThrownBy(() -> lot.retirer(3, TypeMouvement.VENTE, ENTREE))
                .hasMessage("LOT-014 ne compte que 1 tête : impossible d'en retirer 3.");
        assertThat(UniteElevage.tetes(30)).isEqualTo("30 têtes");
    }

    @Test
    @DisplayName("un rappel libre « dans N jours » attend un acte de son type (F10)")
    void rappelLibreSoldable() {
        Lot lot = lot(30);
        RappelSanitaire r = RappelSanitaire.libre(lot, TypeEvenement.VACCINATION,
                "Rappel vaccination", ENTREE.plusDays(21));
        assertThat(r.attend(TypeEvenement.VACCINATION)).isTrue();
        assertThat(r.attend(TypeEvenement.TRAITEMENT)).isFalse();

        r.ignorer();
        assertThat(r.getStatut()).isEqualTo(StatutRappel.IGNORE);
        assertThat(r.attend(TypeEvenement.VACCINATION)).isFalse();
    }

    @Test
    @DisplayName("seul le mouvement d'entree se re-date (F12)")
    void seuleLEntreeSeRedate() {
        Lot lot = lot(30);
        MouvementCheptel entree = MouvementCheptel.entree(lot, TypeMouvement.MISE_EN_PLACE, 30, null);
        entree.setDateMouvement(ENTREE.minusDays(2));
        assertThat(entree.getDateMouvement()).isEqualTo(ENTREE.minusDays(2));

        MouvementCheptel vente = new MouvementCheptel(lot, TypeMouvement.VENTE, ENTREE.plusDays(5),
                5, null, 1000L, null);
        assertThatThrownBy(() -> vente.setDateMouvement(ENTREE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("le logement PAR_CAPACITE suit le nombre de batiments necessaires (L10)")
    void logementDimensionne() {
        TypeElevage t = new TypeElevage(Filiere.VOLAILLE, "Poulet de chair", ModeEvaluation.PAR_PERIODE);
        PosteCout logement = new PosteCout(t, "Logement", 1800, "m2",
                PhaseCout.INSTALLATION, ModeQuantite.PAR_CAPACITE, new BigDecimal("0.1000"), 1);

        assertThat(PosteCout.batiments(33, 100)).isEqualTo(1);
        assertThat(PosteCout.batiments(100, 100)).isEqualTo(1);
        assertThat(PosteCout.batiments(101, 100)).isEqualTo(2);
        assertThat(PosteCout.batiments(2000, 100)).isEqualTo(20);
        assertThat(PosteCout.batiments(0, 100)).isEqualTo(1);
        assertThat(PosteCout.batiments(500, 0)).isEqualTo(1);

        // 10 m2 a 1 800 FCFA pour 100 places ; 2 000 poulets en demandent 200 m2.
        assertThat(logement.calculer(33, 100)).isEqualTo(18_000L);
        assertThat(logement.calculer(2000, 100)).isEqualTo(360_000L);
    }
}
