package ml.moncheptel.api.controleur;

import java.util.List;
import ml.moncheptel.api.dto.reponse.TypeElevageReponse;
import ml.moncheptel.api.dto.reponse.VeterinaireResumeReponse;
import ml.moncheptel.api.dto.reponse.ZoneReponse;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.securite.UtilisateurConnecte;
import ml.moncheptel.api.service.ReferentielService;
import ml.moncheptel.api.service.UtilisateurService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Annuaire veterinaire et liste des zones.
 *
 * <p>Les zones sont servies SANS authentification : le formulaire d'inscription
 * en a besoin pour remplir sa liste deroulante, avant qu'un compte n'existe.
 * C'est une liste de communes, elle ne revele rien.
 */
@RestController
@RequestMapping("/api")
public class AnnuaireControleur {

    private final UtilisateurService utilisateurService;
    private final ReferentielService referentielService;

    public AnnuaireControleur(UtilisateurService utilisateurService,
                              ReferentielService referentielService) {
        this.utilisateurService = utilisateurService;
        this.referentielService = referentielService;
    }

    @GetMapping("/zones")
    public List<ZoneReponse> zones() {
        return referentielService.zones();
    }

    /**
     * Sans {@code zoneId}, l'annuaire de la zone de l'appelant : c'est ce que
     * la maquette affiche, et un eleveur de Koutiala n'appellera pas un
     * praticien de Gao.
     */
    @GetMapping("/veterinaires")
    public List<VeterinaireResumeReponse> annuaire(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @RequestParam(required = false) Long zoneId) {
        Long zone = zoneId != null ? zoneId
                : (connecte.utilisateur().getZone() == null
                        ? null : connecte.utilisateur().getZone().getId());
        return utilisateurService.annuaire(zone);
    }

    /**
     * Types d'elevage proposes a l'eleveur : formulaires « Ajouter un lot »,
     * « Ajouter un animal », ecrans Catalogue et Logistique.
     *
     * <p>Jusqu'ici seule la route d'administration les listait : l'application
     * eleveur ne pouvait donc pas remplir {@code typeElevageId}, obligatoire a
     * la creation d'un lot ou d'un animal. Seuls les types ACTIFS sont rendus :
     * un type desactive par l'administration ne doit plus etre propose.
     */
    @GetMapping("/types-elevage")
    public List<TypeElevageReponse> typesElevage(@RequestParam(required = false) Filiere filiere) {
        return referentielService.typesElevageActifs(filiere);
    }
}
