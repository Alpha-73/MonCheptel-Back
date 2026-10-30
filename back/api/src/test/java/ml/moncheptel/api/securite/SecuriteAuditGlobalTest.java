package ml.moncheptel.api.securite;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Set;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Securite ajoutee par l'audit global, contexte complet sur H2 : 401 sans
 * jeton, 429 sur la connexion, mot de passe provisoire impose par le
 * serveur (G4), verrou qui ne coupe plus les jetons deja valides (G2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecuriteAuditGlobalTest {

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private FabriqueJeton fabrique;
    @Autowired private FiltreLimiteDebit limite;

    @AfterEach
    void vider() {
        limite.reinitialiser();
    }

    private Eleveur eleveur(String email) {
        Eleveur e = new Eleveur("Awa Traore", email, "{noop}x", null, Set.of(Filiere.VOLAILLE));
        return utilisateurs.saveAndFlush(e);
    }

    private String jeton(Eleveur e) {
        return "Bearer " + fabrique.creerJetonAcces(e, Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("sans jeton : 401 avec un message, pas 403")
    void sansJeton() throws Exception {
        mvc.perform(get("/api/signalements/mes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Session expirée. Reconnectez-vous."));
    }

    @Test
    @DisplayName("mot de passe provisoire : tout est refuse sauf moi, changement et deconnexion")
    void motDePasseProvisoire() throws Exception {
        Eleveur e = eleveur("provisoire-audit@example.ml");
        e.exigerChangementMotDePasse();
        utilisateurs.saveAndFlush(e);
        String j = jeton(e);

        mvc.perform(get("/api/signalements/mes").header("Authorization", j))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(FiltreJwt.MESSAGE_MOT_DE_PASSE_PROVISOIRE));
        mvc.perform(get("/api/auth/moi").header("Authorization", j))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un compte verrouille garde sa session en cours (le verrou ne vise que la connexion)")
    void verrouNeCoupePasLesJetons() throws Exception {
        Eleveur e = eleveur("verrou-audit@example.ml");
        for (int i = 0; i < 6; i++) {
            e.signalerEchecConnexion(5, 15);
        }
        utilisateurs.saveAndFlush(e);

        mvc.perform(get("/api/auth/moi").header("Authorization", jeton(e)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("la onzieme connexion de la minute depuis la meme adresse recoit 429")
    void limiteDeConnexion() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/connexion").with(r -> { r.setRemoteAddr("10.7.7.7"); return r; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"identifiant\":\"x@example.ml\",\"motDePasse\":\"faux\"}"));
        }
        mvc.perform(post("/api/auth/connexion").with(r -> { r.setRemoteAddr("10.7.7.7"); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifiant\":\"x@example.ml\",\"motDePasse\":\"faux\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
