package ml.moncheptel.api.controleur;

import jakarta.validation.Valid;
import java.util.List;
import ml.moncheptel.api.dto.reponse.ConseilDetailReponse;
import ml.moncheptel.api.dto.reponse.ConseilResumeReponse;
import ml.moncheptel.api.dto.reponse.PageReponse;
import ml.moncheptel.api.dto.requete.ConseilRequete;
import ml.moncheptel.api.modele.enumeration.CategorieConseil;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.securite.UtilisateurConnecte;
import ml.moncheptel.api.service.ConseilService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conseils")
public class ConseilControleur {

    private static final int TAILLE_PAGE = 10;

    private final ConseilService conseilService;

    public ConseilControleur(ConseilService conseilService) {
        this.conseilService = conseilService;
    }

    /**
     * Fil des conseils. Ouvert a tous les comptes : un veterinaire lit ceux de
     * ses confreres, un administrateur verifie ce qui est en ligne.
     */
    @GetMapping
    public PageReponse<ConseilResumeReponse> lire(
            @RequestParam(required = false) Filiere filiere,
            @RequestParam(required = false) CategorieConseil categorie,
            @RequestParam(defaultValue = "0") int page) {
        return conseilService.lire(filiere, categorie, Pagination.de(page, TAILLE_PAGE));
    }

    /** Compte une lecture. Le compteur s'incremente en base, sans relire l'entite. */
    @GetMapping("/{conseilId}")
    public ConseilDetailReponse lireUn(@PathVariable Long conseilId) {
        return conseilService.lireUn(conseilId);
    }

    // ── cote veterinaire ────────────────────────────────────────────────────

    @GetMapping("/mes-conseils")
    @PreAuthorize("hasRole('VETERINAIRE')")
    public List<ConseilDetailReponse> mesConseils(
            @AuthenticationPrincipal UtilisateurConnecte connecte) {
        return conseilService.mesConseils(connecte.veterinaire());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VETERINAIRE')")
    public ConseilDetailReponse rediger(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                        @Valid @RequestBody ConseilRequete requete) {
        return conseilService.rediger(connecte.veterinaire(), requete);
    }

    @PutMapping("/{conseilId}")
    @PreAuthorize("hasRole('VETERINAIRE')")
    public ConseilDetailReponse modifier(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                         @PathVariable Long conseilId,
                                         @Valid @RequestBody ConseilRequete requete) {
        return conseilService.modifier(connecte.veterinaire(), conseilId, requete);
    }

    /** Envoie le brouillon en moderation. L'auteur ne se publie pas lui-meme. */
    @DeleteMapping("/{conseilId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('VETERINAIRE')")
    public void supprimer(@AuthenticationPrincipal UtilisateurConnecte connecte,
                          @PathVariable Long conseilId) {
        conseilService.supprimer(connecte.veterinaire(), conseilId);
    }

    @PostMapping("/{conseilId}/soumission")
    @PreAuthorize("hasRole('VETERINAIRE')")
    public ConseilDetailReponse soumettre(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                          @PathVariable Long conseilId) {
        return conseilService.soumettre(connecte.veterinaire(), conseilId);
    }
}
