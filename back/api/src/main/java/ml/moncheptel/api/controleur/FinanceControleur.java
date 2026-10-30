package ml.moncheptel.api.controleur;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import ml.moncheptel.api.dto.reponse.OperationReponse;
import ml.moncheptel.api.dto.reponse.RapportReponse;
import ml.moncheptel.api.dto.reponse.SoldeReponse;
import ml.moncheptel.api.dto.requete.OperationRequete;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.securite.UtilisateurConnecte;
import ml.moncheptel.api.service.FinanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finances")
@PreAuthorize("hasRole('ELEVEUR')")
public class FinanceControleur {

    private final FinanceService financeService;

    public FinanceControleur(FinanceService financeService) {
        this.financeService = financeService;
    }

    @GetMapping("/operations")
    public List<OperationReponse> journal(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @RequestParam(required = false) Filiere filiere,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return financeService.journal(connecte.eleveur(), filiere, debut, fin);
    }

    @PostMapping("/operations")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationReponse enregistrer(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                        @Valid @RequestBody OperationRequete requete) {
        return financeService.enregistrer(connecte.eleveur(), requete);
    }

    /**
     * Corrige une ecriture : ecran « Modifier l'operation ».
     *
     * <p>Refuse une recette nee d'une vente : c'est la sortie du cheptel qu'il
     * faut corriger, sans quoi le journal et la comptabilite divergeraient.
     */
    @PutMapping("/operations/{operationId}")
    public OperationReponse modifierOperation(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @PathVariable Long operationId,
            @Valid @RequestBody OperationRequete requete) {
        return financeService.modifier(connecte.eleveur(), operationId, requete);
    }

    @DeleteMapping("/operations/{operationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@AuthenticationPrincipal UtilisateurConnecte connecte,
                          @PathVariable Long operationId) {
        financeService.supprimer(connecte.eleveur(), operationId);
    }

    /** « Solde du mois — toutes filieres ». Sans parametre : le mois en cours. */
    @GetMapping("/solde")
    public SoldeReponse solde(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @RequestParam(required = false) Filiere filiere,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mois) {
        return financeService.soldeDuMois(connecte.eleveur(), filiere,
                mois == null ? LocalDate.now() : mois);
    }

    @GetMapping("/rapport")
    public RapportReponse rapport(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @RequestParam(required = false) Filiere filiere,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return financeService.rapport(connecte.eleveur(), filiere, debut, fin);
    }

    @GetMapping("/rentabilite/{uniteId}")
    public SoldeReponse rentabilite(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                    @PathVariable Long uniteId) {
        return financeService.rentabilite(connecte.eleveur(), uniteId);
    }
}
