package ml.moncheptel.api.controleur;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Demarre le contexte Spring complet sur H2 : toutes les requetes {@code @Query}
 * sont analysees par Hibernate, tous les beans sont cables. Puis verifie que
 * les routes ajoutees le 7 octobre sont fermees sans jeton (401 depuis
 * l'audit global : un jeton absent n'est pas un refus de droits).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecuriteRoutesTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("les routes publiques repondent sans jeton")
    void routesPubliques() throws Exception {
        mvc.perform(get("/api/zones")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("les nouvelles routes veterinaire et administration exigent un jeton")
    void nouvellesRoutesFermees() throws Exception {
        mvc.perform(get("/api/veterinaires/accueil")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/veterinaires/competences")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/signalements/maladies-catalogue")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/conseils/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/conseils")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/conseils").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/maladies/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/veterinaires/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/utilisateurs/1/fiche")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/utilisateurs/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/signalements")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/signalements/1/candidats")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/signalements/1/orientation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"veterinaireId\":1}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/statistiques/series")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/types-elevage/1/reactivation")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("les routes de l'app mobile ajoutees le 7 octobre exigent un jeton")
    void routesMobileFermees() throws Exception {
        mvc.perform(get("/api/signalements/mes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/signalements/1")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/cheptel/1/sorties/2")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/cheptel/1/cloture")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/veterinaires/disponibilite").contentType(MediaType.APPLICATION_JSON)
                .content("{\"disponible\":false}")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Swagger est ferme hors du profil dev")
    void swaggerFerme() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }
}
