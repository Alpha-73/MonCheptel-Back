package ml.moncheptel.api.controleur;

import ml.moncheptel.api.dto.reponse.TableauDeBordReponse;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.securite.UtilisateurConnecte;
import ml.moncheptel.api.service.TableauDeBordService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tableau-de-bord")
@PreAuthorize("hasRole('ELEVEUR')")
public class TableauDeBordControleur {

    private final TableauDeBordService tableauDeBordService;

    public TableauDeBordControleur(TableauDeBordService tableauDeBordService) {
        this.tableauDeBordService = tableauDeBordService;
    }

    /** Sans {@code filiere}, la premiere que l'eleveur pratique. */
    @GetMapping
    public TableauDeBordReponse tableauDeBord(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @RequestParam(required = false) Filiere filiere) {
        return tableauDeBordService.pour(connecte.eleveur(), filiere);
    }
}
