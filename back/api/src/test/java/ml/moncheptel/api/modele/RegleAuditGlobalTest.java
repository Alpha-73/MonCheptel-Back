package ml.moncheptel.api.modele;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import ml.moncheptel.api.modele.enumeration.TypeMouvement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regles de modele ajoutees par l'audit global (G2, complement 11). */
class RegleAuditGlobalTest {

    private static final LocalDate ENTREE = LocalDate.of(2026, 9, 1);

    private static Lot lot(int effectif) {
        Eleveur e = new Eleveur("Balla Kone", "70000000", "empreinte", null, Set.of(Filiere.VOLAILLE));
        TypeElevage t = new TypeElevage(Filiere.VOLAILLE, "Poulet de chair", ModeEvaluation.PAR_PERIODE);
        t.setDureeCycleJours(45);
        return new Lot("LOT-014", e, t, effectif, ENTREE);
    }

    @Test
    @DisplayName("verrou progressif : 1, 2, 4, 8 minutes puis plafond")
    void verrouProgressif() {
        assertThat(Utilisateur.dureeVerrouillage(5, 5, 15)).isEqualTo(1);
        assertThat(Utilisateur.dureeVerrouillage(6, 5, 15)).isEqualTo(2);
        assertThat(Utilisateur.dureeVerrouillage(7, 5, 15)).isEqualTo(4);
        assertThat(Utilisateur.dureeVerrouillage(8, 5, 15)).isEqualTo(8);
        assertThat(Utilisateur.dureeVerrouillage(9, 5, 15)).isEqualTo(15);
        assertThat(Utilisateur.dureeVerrouillage(500, 5, 15)).isEqualTo(15);
        assertThat(Utilisateur.dureeVerrouillage(5, 5, 0)).isEqualTo(1);
    }

    @Test
    @DisplayName("annuler une vente anterieure ne rouvre pas un lot cloture a la main")
    void clotureManuelleConservee() {
        Lot lot = lot(50);
        lot.retirer(10, TypeMouvement.VENTE, ENTREE.plusDays(20));
        lot.terminer(ENTREE.plusDays(46));

        lot.restituer(10);

        assertThat(lot.getStatut()).isEqualTo(StatutUnite.CLOTURE);
        assertThat(lot.getEffectifRestant()).isEqualTo(50);
        assertThat(lot.getDateSortie()).isEqualTo(ENTREE.plusDays(46));
    }

    @Test
    @DisplayName("annuler la sortie qui avait vide le lot le rouvre")
    void sortieQuiACloture() {
        Lot lot = lot(50);
        lot.retirer(50, TypeMouvement.MORT, ENTREE.plusDays(20));

        lot.restituer(50);

        assertThat(lot.getStatut()).isEqualTo(StatutUnite.ACTIF);
    }
}
