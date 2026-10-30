package ml.moncheptel.api.modele;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ml.moncheptel.api.modele.enumeration.CategorieConseil;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutConseil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Le cycle de moderation d'un conseil, et ses deux raccourcis. */
class CycleConseilTest {

    private static Veterinaire veterinaire() {
        return new Veterinaire("Aissata Traore", "aissata.traore@example.ml", "empreinte",
                null, null);
    }

    private static Administrateur administrateur() {
        return new Administrateur("Administrateur", "admin@moncheptel.ml", "empreinte", null);
    }

    private static Conseil publie() {
        Conseil conseil = new Conseil("Reduire les risques de Newcastle", "Vaccinez a J7.",
                Filiere.VOLAILLE, CategorieConseil.SANTE, veterinaire());
        conseil.soumettre();
        conseil.publier(administrateur());
        return conseil;
    }

    @Test
    @DisplayName("un conseil publie et corrige repasse en moderation")
    void unConseilCorrigeRepasseEnModeration() {
        Conseil conseil = publie();

        conseil.reviser();

        assertThat(conseil.getStatut()).isEqualTo(StatutConseil.EN_MODERATION);
        assertThat(conseil.getStatut().visiblePourEleveur()).isFalse();
        assertThat(conseil.getDateSoumission()).isNotNull();
    }

    @Test
    @DisplayName("corriger un brouillon ne le soumet pas")
    void corrigerUnBrouillonNeLeSoumetPas() {
        Conseil brouillon = new Conseil("Titre", "Texte", Filiere.BOVINS,
                CategorieConseil.HYGIENE, veterinaire());

        brouillon.reviser();

        assertThat(brouillon.getStatut()).isEqualTo(StatutConseil.BROUILLON);
    }

    @Test
    @DisplayName("un conseil de l'administration est publie sans auteur ni moderation")
    void conseilDeLAdministration() {
        Administrateur admin = administrateur();
        Conseil conseil = Conseil.publieParAdministration("Prevenir la coccidiose",
                "Litiere seche.", Filiere.VOLAILLE, CategorieConseil.SANTE, admin);

        assertThat(conseil.getStatut()).isEqualTo(StatutConseil.PUBLIE);
        assertThat(conseil.getAuteur()).isNull();
        assertThat(conseil.getModerateur()).isSameAs(admin);
        assertThat(conseil.getDatePublication()).isNotNull();
    }

    @Test
    @DisplayName("un retrait exige toujours un motif")
    void unRetraitExigeUnMotif() {
        Conseil conseil = publie();

        assertThatThrownBy(() -> conseil.retirer(administrateur(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
