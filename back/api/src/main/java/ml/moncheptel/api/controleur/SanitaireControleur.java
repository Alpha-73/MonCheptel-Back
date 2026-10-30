package ml.moncheptel.api.controleur;

import jakarta.validation.Valid;
import java.util.List;
import ml.moncheptel.api.dto.reponse.EvenementReponse;
import ml.moncheptel.api.dto.reponse.RappelReponse;
import ml.moncheptel.api.dto.requete.EvenementSanitaireRequete;
import ml.moncheptel.api.securite.UtilisateurConnecte;
import ml.moncheptel.api.service.SanitaireService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sanitaire")
public class SanitaireControleur {

    /** Horizon par defaut des rappels affiches, en jours. */
    private static final int HORIZON_DEFAUT = 30;

    private final SanitaireService sanitaireService;

    public SanitaireControleur(SanitaireService sanitaireService) {
        this.sanitaireService = sanitaireService;
    }

    /**
     * Ouvert aux eleveurs ET aux veterinaires : un veterinaire doit pouvoir
     * consigner l'acte qu'il vient de poser. Le controle fin — proprietaire
     * ou praticien — est dans le service.
     */
    @PostMapping("/unites/{uniteId}/evenements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ELEVEUR','VETERINAIRE')")
    public EvenementReponse enregistrer(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                        @PathVariable Long uniteId,
                                        @Valid @RequestBody EvenementSanitaireRequete requete) {
        return sanitaireService.enregistrer(connecte.utilisateur(), uniteId, requete);
    }

    /**
     * Corrige un evenement : ecran « Modifier l'evenement ».
     *
     * <p>Le chemin porte l'identifiant de l'EVENEMENT, pas celui de l'unite :
     * un evenement ne change pas d'elevage, le service explique pourquoi.
     */
    @PutMapping("/evenements/{evenementId}")
    public EvenementReponse modifierEvenement(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @PathVariable Long evenementId,
            @Valid @RequestBody EvenementSanitaireRequete requete) {
        return sanitaireService.modifier(connecte.utilisateur(), evenementId, requete);
    }

    /**
     * Supprime un evenement, et ROUVRE le rappel qu'il avait solde.
     *
     * <p>Sans cette reouverture, un acte saisi par erreur puis supprime
     * laisserait le rappel marque « fait » : l'eleveur croirait son lot
     * vaccine et plus personne ne le relancerait.
     */
    @DeleteMapping("/evenements/{evenementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerEvenement(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                   @PathVariable Long evenementId) {
        sanitaireService.supprimerEvenement(connecte.utilisateur(), evenementId);
    }

    @GetMapping("/rappels")
    @PreAuthorize("hasRole('ELEVEUR')")
    public List<RappelReponse> rappels(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                       @RequestParam(required = false) Integer horizonJours) {
        return sanitaireService.rappelsDuTableauDeBord(
                connecte.id(), horizonJours == null ? HORIZON_DEFAUT : horizonJours);
    }

    @PostMapping("/rappels/{rappelId}/ignorer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ELEVEUR')")
    public void ignorer(@AuthenticationPrincipal UtilisateurConnecte connecte,
                        @PathVariable Long rappelId) {
        sanitaireService.ignorerRappel(connecte.utilisateur(), rappelId);
    }
}
