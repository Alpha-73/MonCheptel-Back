package ml.moncheptel.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ml.moncheptel.api.modele.Administrateur;
import ml.moncheptel.api.repository.AdministrateurRepository;
import ml.moncheptel.api.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

/** Premier administrateur cree depuis l'environnement (audit global G1). */
class InitialisationAdministrateurTest {

    private AdministrateurRepository admins;
    private UtilisateurRepository utilisateurs;

    @BeforeEach
    void preparer() {
        admins = mock(AdministrateurRepository.class);
        utilisateurs = mock(UtilisateurRepository.class);
        when(admins.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @SuppressWarnings("deprecation")
    private InitialisationAdministrateur init(String email, String mdp) {
        return new InitialisationAdministrateur(admins, utilisateurs,
                NoOpPasswordEncoder.getInstance(), email, mdp, "Direction");
    }

    @Test
    @DisplayName("base sans administrateur : le compte est cree avec un mot de passe provisoire")
    void creation() {
        Administrateur a = init(" Chef@Example.ML ", "un-mot-de-passe-long").initialiser();

        assertThat(a).isNotNull();
        assertThat(a.getEmail()).isEqualTo("chef@example.ml");
        assertThat(a.isDoitChangerMotDePasse()).isTrue();
        assertThat(a.getZone()).isNull();
        verify(admins).save(a);
    }

    @Test
    @DisplayName("un administrateur existe deja (la demo par exemple) : rien n'est touche")
    void dejaUnAdministrateur() {
        when(admins.count()).thenReturn(1L);
        assertThat(init("chef@example.ml", "un-mot-de-passe-long").initialiser()).isNull();
        verify(admins, never()).save(any());
    }

    @Test
    @DisplayName("variables absentes : simple avertissement, l'API demarre")
    void variablesAbsentes() {
        assertThat(init("", "").initialiser()).isNull();
        verify(admins, never()).save(any());
    }

    @Test
    @DisplayName("mot de passe trop court, adresse invalide ou deja prise : refus explicite")
    void refus() {
        assertThatThrownBy(() -> init("chef@example.ml", "court").initialiser())
                .hasMessageContaining("entre 12 et 72");
        assertThatThrownBy(() -> init("pas-une-adresse", "un-mot-de-passe-long").initialiser())
                .hasMessageContaining("adresse e-mail valide");
        when(utilisateurs.existsByEmail("pris@example.ml")).thenReturn(true);
        assertThatThrownBy(() -> init("pris@example.ml", "un-mot-de-passe-long").initialiser())
                .hasMessageContaining("deja l'adresse");
        verify(admins, never()).save(any());
    }
}
