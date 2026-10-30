package ml.moncheptel.api.controleur;

import jakarta.validation.Valid;
import ml.moncheptel.api.dto.reponse.NotificationReponse;
import ml.moncheptel.api.dto.reponse.PageReponse;
import ml.moncheptel.api.dto.requete.JetonAppareilRequete;
import ml.moncheptel.api.securite.UtilisateurConnecte;
import ml.moncheptel.api.service.NotificationService;
import ml.moncheptel.api.service.UtilisateurService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationControleur {

    private static final int TAILLE_PAGE = 20;

    private final NotificationService notificationService;
    private final UtilisateurService utilisateurService;

    public NotificationControleur(NotificationService notificationService,
                                  UtilisateurService utilisateurService) {
        this.notificationService = notificationService;
        this.utilisateurService = utilisateurService;
    }

    @GetMapping
    public PageReponse<NotificationReponse> lister(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @RequestParam(defaultValue = "0") int page) {
        return notificationService.lister(connecte.id(), Pagination.de(page, TAILLE_PAGE));
    }

    /** La pastille de la cloche, dans l'en-tete de chaque ecran. */
    @GetMapping("/non-lues")
    public long compterNonLues(@AuthenticationPrincipal UtilisateurConnecte connecte) {
        return notificationService.compterNonLues(connecte.id());
    }

    @PostMapping("/lecture")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marquerToutesLues(@AuthenticationPrincipal UtilisateurConnecte connecte) {
        notificationService.marquerToutesLues(connecte.id());
    }

    /**
     * Enregistre le jeton Firebase de l'appareil.
     *
     * <p>Appele au demarrage de l'application ET a chaque renouvellement du
     * jeton par Firebase — qui peut survenir sans intervention de
     * l'utilisateur. Un client qui ne rappellerait cette route qu'a
     * l'installation cesserait de recevoir les alertes sans le savoir.
     */
    @PostMapping("/appareils")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enregistrerAppareil(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                    @Valid @RequestBody JetonAppareilRequete requete) {
        utilisateurService.enregistrerAppareil(connecte.utilisateur(), requete);
    }

    @DeleteMapping("/appareils")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void oublierAppareil(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                @RequestParam String jeton) {
        utilisateurService.oublierAppareil(connecte.utilisateur(), jeton);
    }
}
