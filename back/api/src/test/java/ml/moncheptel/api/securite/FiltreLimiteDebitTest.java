package ml.moncheptel.api.securite;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Limitation de debit des routes d'authentification (audit global G2). */
class FiltreLimiteDebitTest {

    /** Horloge qu'on avance a la main. */
    private static final class Horloge extends Clock {
        private Instant maintenant = Instant.parse("2026-10-08T10:00:00Z");
        void avancer(Duration d) { maintenant = maintenant.plus(d); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return maintenant; }
    }

    private final Horloge horloge = new Horloge();

    private FiltreLimiteDebit filtre(FiltreLimiteDebit.Regle... regles) {
        return new FiltreLimiteDebit(List.of(regles), new ObjectMapper().findAndRegisterModules(), horloge);
    }

    private static int appeler(FiltreLimiteDebit f, String methode, String chemin, String ip)
            throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest(methode, chemin);
        r.setRemoteAddr(ip);
        MockHttpServletResponse rep = new MockHttpServletResponse();
        f.doFilter(r, rep, new MockFilterChain());
        return rep.getStatus();
    }

    @Test
    @DisplayName("au-dela de la capacite, 429 avec Retry-After ; la fenetre suivante repart a zero")
    void fenetreFixe() throws Exception {
        FiltreLimiteDebit f = filtre(new FiltreLimiteDebit.Regle(
                "/api/auth/connexion", 3, Duration.ofMinutes(1), false));
        for (int i = 0; i < 3; i++) {
            assertThat(appeler(f, "POST", "/api/auth/connexion", "10.0.0.1")).isEqualTo(200);
        }
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/auth/connexion/");
        r.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse rep = new MockHttpServletResponse();
        f.doFilter(r, rep, new MockFilterChain());
        assertThat(rep.getStatus()).isEqualTo(429);
        assertThat(rep.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rep.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("Trop d'essais depuis ce réseau");

        // Une autre adresse n'est pas touchee.
        assertThat(appeler(f, "POST", "/api/auth/connexion", "10.0.0.2")).isEqualTo(200);

        horloge.avancer(Duration.ofSeconds(61));
        assertThat(appeler(f, "POST", "/api/auth/connexion", "10.0.0.1")).isEqualTo(200);
    }

    @Test
    @DisplayName("seuls les POST des routes listees sont comptes")
    void horsPerimetre() throws Exception {
        FiltreLimiteDebit f = filtre(new FiltreLimiteDebit.Regle(
                "/api/auth/connexion", 1, Duration.ofMinutes(1), false));
        for (int i = 0; i < 5; i++) {
            assertThat(appeler(f, "GET", "/api/auth/connexion", "10.0.0.1")).isEqualTo(200);
            assertThat(appeler(f, "POST", "/api/cheptel", "10.0.0.1")).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("une regle globale compte toutes les adresses ensemble")
    void regleGlobale() throws Exception {
        FiltreLimiteDebit f = filtre(new FiltreLimiteDebit.Regle(
                "/api/auth/mot-de-passe/oublie", 2, Duration.ofHours(1), true));
        assertThat(appeler(f, "POST", "/api/auth/mot-de-passe/oublie", "10.0.0.1")).isEqualTo(200);
        assertThat(appeler(f, "POST", "/api/auth/mot-de-passe/oublie", "10.0.0.2")).isEqualTo(200);
        assertThat(appeler(f, "POST", "/api/auth/mot-de-passe/oublie", "10.0.0.3")).isEqualTo(429);
    }

    @Test
    @DisplayName("les regles par defaut couvrent les cinq routes publiques")
    void reglesParDefaut() {
        assertThat(FiltreLimiteDebit.reglesParDefaut()).extracting(FiltreLimiteDebit.Regle::chemin)
                .contains("/api/auth/connexion", "/api/auth/rafraichir", "/api/auth/inscription",
                        "/api/auth/mot-de-passe/oublie", "/api/auth/mot-de-passe/reinitialiser");
    }
}
