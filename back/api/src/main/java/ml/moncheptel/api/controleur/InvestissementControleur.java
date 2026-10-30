package ml.moncheptel.api.controleur;

import jakarta.validation.Valid;
import ml.moncheptel.api.dto.reponse.DetailSimulationReponse;
import ml.moncheptel.api.dto.reponse.SimulationReponse;
import ml.moncheptel.api.dto.requete.SimulationRequete;
import ml.moncheptel.api.service.InvestissementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulateur d'investissement.
 *
 * <p>{@code POST} pour une simulation qui n'ecrit rien : le corps de requete
 * porte cinq parametres, dont un budget. Les passer en parametres d'URL les
 * ferait apparaitre dans les journaux du serveur et l'historique du
 * navigateur — le budget d'un eleveur n'a pas a s'y trouver.
 */
@RestController
@RequestMapping("/api/investissement")
@PreAuthorize("hasRole('ELEVEUR')")
public class InvestissementControleur {

    private final InvestissementService investissementService;

    public InvestissementControleur(InvestissementService investissementService) {
        this.investissementService = investissementService;
    }

    @PostMapping("/simulation")
    public SimulationReponse simuler(@Valid @RequestBody SimulationRequete requete) {
        return investissementService.simuler(requete);
    }

    @PostMapping("/simulation/{typeElevageId}")
    public DetailSimulationReponse detailler(@PathVariable Long typeElevageId,
                                             @Valid @RequestBody SimulationRequete requete) {
        return investissementService.detailler(typeElevageId, requete);
    }
}
