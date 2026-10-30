package ml.moncheptel.api.controleur;

import jakarta.validation.Valid;
import java.util.List;
import ml.moncheptel.api.dto.reponse.MaladieReponse;
import ml.moncheptel.api.dto.reponse.PageReponse;
import ml.moncheptel.api.dto.reponse.SignalementReponse;
import ml.moncheptel.api.dto.reponse.SymptomeReponse;
import ml.moncheptel.api.dto.reponse.VeterinaireResumeReponse;
import ml.moncheptel.api.dto.requete.PriseEnChargeRequete;
import ml.moncheptel.api.dto.requete.SignalementRequete;
import ml.moncheptel.api.dto.requete.SymptomeRequete;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;
import ml.moncheptel.api.securite.UtilisateurConnecte;
import ml.moncheptel.api.service.ReferentielService;
import ml.moncheptel.api.service.SignalementService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signalement de maladie, cote eleveur et cote veterinaire.
 *
 * <p>Le parcours de la maquette — sujet, symptomes, ampleur, resultat — tient
 * en UN appel. L'application accumule les trois reponses et poste une fois ;
 * le diagnostic revient dans la meme reponse, et l'ecran de resultat n'a rien
 * a redemander.
 */
@RestController
@RequestMapping("/api/signalements")
public class SignalementControleur {

    private static final int TAILLE_PAGE = 20;

    private final SignalementService signalementService;
    private final ReferentielService referentielService;

    public SignalementControleur(SignalementService signalementService,
                                 ReferentielService referentielService) {
        this.signalementService = signalementService;
        this.referentielService = referentielService;
    }

    /** Ecran « Signalement - Symptomes » : la liste a cocher. */
    @GetMapping("/symptomes")
    @PreAuthorize("hasAnyRole('ELEVEUR','VETERINAIRE','ADMINISTRATEUR')")
    public List<SymptomeReponse> symptomes(@RequestParam(required = false) Filiere filiere) {
        return referentielService.symptomes(filiere);
    }

    /**
     * Ajoute un symptome a la liste a cocher : administration et veterinaires,
     * quand une maladie presente un signe absent de la liste. Un libelle deja
     * present rend le symptome existant.
     */
    @PostMapping("/symptomes")
    @PreAuthorize("hasAnyRole('VETERINAIRE','ADMINISTRATEUR')")
    public SymptomeReponse creerSymptome(@Valid @RequestBody SymptomeRequete requete) {
        return referentielService.creerSymptome(requete.libelle());
    }

    /**
     * Catalogue des maladies vu par un eleveur : le traitement des maladies
     * qui exigent un veterinaire n'y figure pas. Le masquage est fait dans le
     * mapper, cote serveur — le cacher a l'affichage laisserait le texte dans
     * la reponse JSON.
     */
    @GetMapping("/maladies")
    @PreAuthorize("hasRole('ELEVEUR')")
    public List<MaladieReponse> maladies(@RequestParam(required = false) Filiere filiere) {
        return referentielService.maladiesPourEleveur(filiere);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ELEVEUR')")
    public SignalementReponse signaler(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                       @Valid @RequestBody SignalementRequete requete) {
        return signalementService.signaler(connecte.eleveur(), requete);
    }

    /** « Mes signalements » de l'eleveur, le plus recent d'abord. */
    @GetMapping("/mes")
    @PreAuthorize("hasRole('ELEVEUR')")
    public List<SignalementReponse> mesSignalements(
            @AuthenticationPrincipal UtilisateurConnecte connecte) {
        return signalementService.mesSignalements(connecte.eleveur());
    }

    /**
     * Un signalement : l'eleveur qui l'a fait, ou le veterinaire a qui il
     * est confie (ouverture depuis une notification). 404 pour tout autre.
     */
    @GetMapping("/{signalementId:\\d+}")
    @PreAuthorize("hasAnyRole('ELEVEUR','VETERINAIRE')")
    public SignalementReponse detail(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                     @PathVariable Long signalementId) {
        return signalementService.detail(connecte.utilisateur(), signalementId);
    }

    /**
     * Les veterinaires que l'eleveur peut choisir : ceux de SA zone qui
     * traitent la maladie probable, dans un ordre aleatoire a chaque appel.
     */
    @GetMapping("/{signalementId:\\d+}/veterinaires")
    @PreAuthorize("hasRole('ELEVEUR')")
    public List<VeterinaireResumeReponse> veterinairesProposes(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @PathVariable Long signalementId) {
        return signalementService.veterinairesProposes(connecte.eleveur(), signalementId);
    }

    /** L'eleveur confie son signalement au veterinaire choisi dans la liste. */
    @PostMapping("/{signalementId:\\d+}/veterinaire/{veterinaireId:\\d+}")
    @PreAuthorize("hasRole('ELEVEUR')")
    public SignalementReponse choisirVeterinaire(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @PathVariable Long signalementId,
            @PathVariable Long veterinaireId) {
        return signalementService.choisirVeterinaire(
                connecte.eleveur(), signalementId, veterinaireId);
    }

    // ── cote veterinaire ────────────────────────────────────────────────────

    /**
     * Ecran « Mes compétences » : toutes les maladies actives, a cocher. Le
     * veterinaire lit le traitement en clair, comme l'administration.
     */
    @GetMapping("/maladies-catalogue")
    @PreAuthorize("hasRole('VETERINAIRE')")
    public List<MaladieReponse> catalogue() {
        return referentielService.maladiesCompletes();
    }

    @GetMapping("/file")
    @PreAuthorize("hasRole('VETERINAIRE')")
    public PageReponse<SignalementReponse> file(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @RequestParam(required = false) StatutSignalement statut,
            @RequestParam(defaultValue = "0") int page) {
        return signalementService.fileDuVeterinaire(
                connecte.veterinaire(), statut, Pagination.de(page, TAILLE_PAGE));
    }

    @PostMapping("/{signalementId}/prise-en-charge")
    @PreAuthorize("hasRole('VETERINAIRE')")
    public SignalementReponse prendreEnCharge(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @PathVariable Long signalementId) {
        return signalementService.prendreEnCharge(connecte.veterinaire(), signalementId);
    }

    /**
     * Le veterinaire refuse le cas qui lui est confie (V14). Corps
     * facultatif : {@code {"motif": "..."}}.
     */
    @PostMapping("/{signalementId}/refus")
    @PreAuthorize("hasRole('VETERINAIRE')")
    public SignalementReponse refuser(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                      @PathVariable Long signalementId,
                                      @Valid @RequestBody(required = false)
                                      ml.moncheptel.api.dto.requete.RefusRequete requete) {
        return signalementService.refuser(connecte.veterinaire(), signalementId,
                requete == null ? null : requete.motif());
    }

    @PostMapping("/{signalementId}/cloture")
    @PreAuthorize("hasRole('VETERINAIRE')")
    public SignalementReponse cloturer(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                       @PathVariable Long signalementId,
                                       @Valid @RequestBody PriseEnChargeRequete requete) {
        return signalementService.cloturer(connecte.veterinaire(), signalementId, requete);
    }
}
