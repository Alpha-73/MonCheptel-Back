package ml.moncheptel.api.controleur;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Audit console v2 (A31) : un jeton valide d'eleveur ou de veterinaire ne
 * doit ouvrir aucune route /api/admin. Un administrateur, lui, passe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecuriteRolesAdministrationTest {

    @Autowired
    private MockMvc mvc;

    private void routesRefusees() throws Exception {
        mvc.perform(get("/api/admin/tableau-de-bord")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/utilisateurs")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/alertes")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/alertes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"maladieId\":1,\"zoneId\":1,\"message\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/signalements/1/orientation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"veterinaireId\":1}")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ELEVEUR")
    @DisplayName("un eleveur recoit 403 sur /api/admin")
    void eleveurRefuse() throws Exception {
        routesRefusees();
    }

    @Test
    @WithMockUser(roles = "VETERINAIRE")
    @DisplayName("un veterinaire recoit 403 sur /api/admin")
    void veterinaireRefuse() throws Exception {
        routesRefusees();
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATEUR")
    @DisplayName("un administrateur lit la liste des alertes")
    void administrateurAccepte() throws Exception {
        mvc.perform(get("/api/admin/alertes")).andExpect(status().isOk());
    }
}
