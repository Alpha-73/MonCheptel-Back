package ml.moncheptel.api.modele;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.modele.enumeration.Sexe;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import ml.moncheptel.api.modele.enumeration.TypeMouvement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * La regle la plus importante du modele : on ne sort pas plus de tetes qu'il
 * n'y en a, et le statut decoule des sorties.
 *
 * <p>Ces tests ne touchent ni la base ni Spring. Ils s'executent en quelques
 * millisecondes parce que la regle vit sur l'entite : c'est la contrepartie
 * concrete du choix de ne pas la mettre dans un service.
 */
class RegleDeSortieTest {

    private static final LocalDate MISE_EN_PLACE = LocalDate.of(2026, 9, 1);

    private static TypeElevage poulet() {
        TypeElevage t = new TypeElevage(Filiere.VOLAILLE, "Poulet de chair",
                ModeEvaluation.PAR_PERIODE);
        t.setDureeCycleJours(45);
        t.setTauxMortalite(new java.math.BigDecimal("0.08"));
        return t;
    }

    private static TypeElevage bovin() {
        return new TypeElevage(Filiere.BOVINS, "Veau d'embouche", ModeEvaluation.PAR_ENTREE);
    }

    private static Eleveur eleveur() {
        return new Eleveur("Balla Kone", "70000000", "empreinte",
                null, Set.of(Filiere.VOLAILLE, Filiere.BOVINS));
    }

    private static Lot lot(int effectif) {
        return new Lot("LOT-014", eleveur(), poulet(), effectif, MISE_EN_PLACE);
    }

    @Nested
    @DisplayName("Un lot")
    class DUnLot {

        @Test
        @DisplayName("refuse de sortir plus de tetes qu'il n'en compte")
        void refuseLaSurvente() {
            Lot lot = lot(180);
            assertThatThrownBy(() ->
                    lot.retirer(200, TypeMouvement.VENTE, LocalDate.of(2026, 10, 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    // Le message doit etre lisible PAR L'ELEVEUR : c'est lui
                    // qui le verra, le gestionnaire d'exceptions le recopie tel
                    // quel dans la reponse HTTP.
                    .hasMessageContaining("LOT-014")
                    .hasMessageContaining("180");
            assertThat(lot.getEffectifRestant()).isEqualTo(180);
            assertThat(lot.getStatut()).isEqualTo(StatutUnite.ACTIF);
        }

        @Test
        @DisplayName("refuse un type d'ENTREE comme motif de sortie")
        void refuseUnTypeDEntree() {
            Lot lot = lot(180);
            assertThatThrownBy(() ->
                    lot.retirer(10, TypeMouvement.MISE_EN_PLACE, LocalDate.of(2026, 10, 1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuse une quantite nulle ou negative")
        void refuseUneQuantiteAbsurde() {
            Lot lot = lot(180);
            assertThatThrownBy(() -> lot.retirer(0, TypeMouvement.VENTE, MISE_EN_PLACE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> lot.retirer(-5, TypeMouvement.VENTE, MISE_EN_PLACE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("se cloture, jamais ne se « vend », meme apres une vente totale")
        void seClotureEtNeSeVendPas() {
            Lot lot = lot(180);
            lot.retirer(180, TypeMouvement.VENTE, LocalDate.of(2026, 10, 15));

            // Un lot vendu en trois fois n'est pas « VENDU » : il est CLOTURE.
            // VENDU est un statut d'ANIMAL, une bete a un acheteur.
            assertThat(lot.getStatut()).isEqualTo(StatutUnite.CLOTURE);
            assertThat(lot.getEffectifRestant()).isZero();
            assertThat(lot.getDateSortie()).isEqualTo(LocalDate.of(2026, 10, 15));
        }

        @Test
        @DisplayName("reste actif tant qu'il lui reste une tete")
        void resteActifTantQuIlEnReste() {
            Lot lot = lot(180);
            lot.retirer(100, TypeMouvement.VENTE, LocalDate.of(2026, 10, 1));
            lot.retirer(79, TypeMouvement.MORT, LocalDate.of(2026, 10, 5));

            assertThat(lot.getEffectifRestant()).isEqualTo(1);
            assertThat(lot.getStatut()).isEqualTo(StatutUnite.ACTIF);
            assertThat(lot.getDateSortie()).isNull();
        }

        @Test
        @DisplayName("refuse toute sortie une fois cloture")
        void refuseApresCloture() {
            Lot lot = lot(50);
            lot.retirer(50, TypeMouvement.VENTE, LocalDate.of(2026, 10, 1));
            assertThatThrownBy(() ->
                    lot.retirer(1, TypeMouvement.VENTE, LocalDate.of(2026, 10, 2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("déjà sorti");
        }

        @Test
        @DisplayName("porte une date de cloture prevue calculee a la creation")
        void calculeSaDateDeCloture() {
            // 1er septembre + 45 jours
            assertThat(lot(180).getDateCloturePrevue()).isEqualTo(LocalDate.of(2026, 10, 16));
        }
    }

    @Nested
    @DisplayName("Un animal")
    class DUnAnimal {

        private Animal veau() {
            return new Animal("BOV-009", eleveur(), bovin(),
                    LocalDate.of(2026, 3, 1), "Zebu", Sexe.MALE, null);
        }

        @Test
        @DisplayName("prend le statut correspondant au motif de sa sortie")
        void prendLeStatutDuMotif() {
            Animal vendu = veau();
            vendu.retirer(1, TypeMouvement.VENTE, LocalDate.of(2026, 6, 14));
            assertThat(vendu.getStatut()).isEqualTo(StatutUnite.VENDU);

            Animal mort = veau();
            mort.retirer(1, TypeMouvement.MORT, LocalDate.of(2026, 6, 14));
            assertThat(mort.getStatut()).isEqualTo(StatutUnite.MORT);

            Animal transfere = veau();
            transfere.retirer(1, TypeMouvement.TRANSFERT, LocalDate.of(2026, 6, 14));
            assertThat(transfere.getStatut()).isEqualTo(StatutUnite.TRANSFERE);
        }

        @Test
        @DisplayName("ne peut pas sortir deux tetes : il n'en a qu'une")
        void nAQuUneTete() {
            assertThatThrownBy(() -> veau().retirer(2, TypeMouvement.VENTE, LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
