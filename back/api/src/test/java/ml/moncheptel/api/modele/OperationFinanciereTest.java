package ml.moncheptel.api.modele;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.CategorieOperation;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.modele.enumeration.TypeOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Le signe vient du type, jamais du montant. */
class OperationFinanciereTest {

    private static Eleveur eleveur() {
        return new Eleveur("Balla Kone", "70000000", "empreinte", null,
                Set.of(Filiere.VOLAILLE));
    }

    @Test
    @DisplayName("refuse une categorie qui n'appartient pas au type")
    void refuseUneCategorieIncoherente() {
        // « Vente d'animaux » est une categorie de REVENU. La classer en
        // depense ferait compter une recette en negatif, et le solde du mois
        // serait faux de deux fois le montant.
        assertThatThrownBy(() -> new OperationFinanciere(
                eleveur(), TypeOperation.DEPENSE, CategorieOperation.VENTE_ANIMAUX,
                Filiere.VOLAILLE, null, 74_000L, LocalDate.now(), "incoherent"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("le montant reste positif, le signe vient du type")
    void leSigneVientDuType() {
        OperationFinanciere depense = new OperationFinanciere(
                eleveur(), TypeOperation.DEPENSE, CategorieOperation.ALIMENT,
                Filiere.VOLAILLE, null, 52_000L, LocalDate.now(), "Sac d'aliment");
        OperationFinanciere revenu = new OperationFinanciere(
                eleveur(), TypeOperation.REVENU, CategorieOperation.VENTE_ANIMAUX,
                Filiere.VOLAILLE, null, 126_000L, LocalDate.now(), "Vente LOT-003");

        assertThat(depense.getMontant()).isEqualTo(52_000L);
        assertThat(depense.montantSigne()).isEqualTo(-52_000L);
        assertThat(revenu.montantSigne()).isEqualTo(126_000L);
        assertThat(revenu.montantSigne() + depense.montantSigne()).isEqualTo(74_000L);
    }

    @Test
    @DisplayName("une operation sans unite est une depense partagee")
    void reconnaitUneDepensePartagee() {
        // Le sac d'aliment achete en gros : il compte dans le solde du mois,
        // jamais dans la rentabilite d'un lot.
        OperationFinanciere partagee = new OperationFinanciere(
                eleveur(), TypeOperation.DEPENSE, CategorieOperation.ALIMENT,
                Filiere.VOLAILLE, null, 52_000L, LocalDate.now(), "Sac de 50 kg");
        assertThat(partagee.estPartagee()).isTrue();
    }

    @Test
    @DisplayName("une vente engendre sa recette, du meme montant et rattachee au lot")
    void laVenteEngendreSaRecette() {
        TypeElevage type = new TypeElevage(Filiere.VOLAILLE, "Poulet de chair",
                ModeEvaluation.PAR_PERIODE);
        type.setDureeCycleJours(45);
        Lot lot = new Lot("LOT-003", eleveur(), type, 150, LocalDate.of(2026, 7, 1));

        MouvementCheptel vente = new MouvementCheptel(lot,
                ml.moncheptel.api.modele.enumeration.TypeMouvement.VENTE,
                LocalDate.of(2026, 8, 15), 150, "Vente au marche", 126_000L, eleveur());

        OperationFinanciere recette = OperationFinanciere.revenuDeVente(vente, "Vente LOT-003");

        assertThat(recette.getType()).isEqualTo(TypeOperation.REVENU);
        assertThat(recette.getMontant()).isEqualTo(126_000L);
        assertThat(recette.getUnite()).isSameAs(lot);
        assertThat(recette.getMouvement()).isSameAs(vente);
        // C'est ce lien qui empeche de compter deux fois la meme vente.
        assertThat(recette.estPartagee()).isFalse();
    }
}
