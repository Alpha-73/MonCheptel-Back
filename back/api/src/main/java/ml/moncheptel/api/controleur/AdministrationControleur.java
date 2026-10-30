package ml.moncheptel.api.controleur;

import jakarta.validation.Valid;
import java.util.List;
import ml.moncheptel.api.dto.reponse.AlerteReponse;
import ml.moncheptel.api.dto.reponse.ConseilDetailReponse;
import ml.moncheptel.api.dto.reponse.EtapeProtocoleReponse;
import ml.moncheptel.api.dto.reponse.FicheUtilisateurReponse;
import ml.moncheptel.api.dto.reponse.MaladieAdminReponse;
import ml.moncheptel.api.dto.reponse.PageReponse;
import ml.moncheptel.api.dto.reponse.ProfilReponse;
import ml.moncheptel.api.dto.reponse.SeriesStatistiquesReponse;
import ml.moncheptel.api.dto.reponse.SignalementAdminReponse;
import ml.moncheptel.api.dto.reponse.CandidatVeterinaireReponse;
import ml.moncheptel.api.dto.reponse.StatistiquesReponse;
import ml.moncheptel.api.dto.reponse.TableauDeBordAdminReponse;
import ml.moncheptel.api.dto.reponse.TypeElevageReponse;
import ml.moncheptel.api.dto.reponse.UtilisateurResumeReponse;
import ml.moncheptel.api.dto.reponse.VeterinaireAdminReponse;
import ml.moncheptel.api.dto.requete.AlerteRequete;
import ml.moncheptel.api.dto.requete.AttributionRequete;
import ml.moncheptel.api.dto.requete.ConseilRequete;
import ml.moncheptel.api.dto.requete.MaladieRequete;
import ml.moncheptel.api.dto.requete.MessageAlerteRequete;
import ml.moncheptel.api.dto.requete.ModerationRequete;
import ml.moncheptel.api.dto.requete.ProfilRequete;
import ml.moncheptel.api.dto.requete.ProtocoleRequete;
import ml.moncheptel.api.dto.requete.TypeElevageRequete;
import ml.moncheptel.api.dto.requete.VeterinaireRequete;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutConseil;
import ml.moncheptel.api.securite.UtilisateurConnecte;
import ml.moncheptel.api.service.AlerteService;
import ml.moncheptel.api.service.CatalogueAdministrationService;
import ml.moncheptel.api.service.ConseilService;
import ml.moncheptel.api.service.FicheAdministrationService;
import ml.moncheptel.api.service.ReferentielService;
import ml.moncheptel.api.service.StatistiqueService;
import ml.moncheptel.api.service.SuiviSignalementService;
import ml.moncheptel.api.service.SuppressionCompteService;
import ml.moncheptel.api.service.UtilisateurService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Console d'administration — c'est le front Angular qui consomme ces routes.
 *
 * <p>{@code @PreAuthorize} est pose une fois sur la CLASSE. Toutes les routes
 * heritent de la regle : aucune ne peut etre ajoutee sans protection par
 * oubli, ce qui est exactement ce qui arrive quand la regle se repete methode
 * par methode.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AdministrationControleur {

    /**
     * Lignes par page du tableau des utilisateurs.
     *
     * <p>Dix, a la demande du proprietaire du projet (7 octobre) : la page
     * tient sans defilement sur un ecran portable. Mille deux cents comptes
     * font cent vingt pages, ce qui impose une recherche — c'est bien ainsi, on
     * ne trouve pas un eleveur en feuilletant.
     */
    private static final int TAILLE_PAGE = 10;

    private final ReferentielService referentielService;
    private final ConseilService conseilService;
    private final AlerteService alerteService;
    private final UtilisateurService utilisateurService;
    private final StatistiqueService statistiqueService;
    private final FicheAdministrationService ficheService;
    private final SuppressionCompteService suppressionService;
    private final SuiviSignalementService suiviService;
    private final CatalogueAdministrationService catalogueService;

    public AdministrationControleur(ReferentielService referentielService,
                                    ConseilService conseilService,
                                    AlerteService alerteService,
                                    UtilisateurService utilisateurService,
                                    StatistiqueService statistiqueService,
                                    FicheAdministrationService ficheService,
                                    SuppressionCompteService suppressionService,
                                    SuiviSignalementService suiviService,
                                    CatalogueAdministrationService catalogueService) {
        this.referentielService = referentielService;
        this.conseilService = conseilService;
        this.alerteService = alerteService;
        this.utilisateurService = utilisateurService;
        this.statistiqueService = statistiqueService;
        this.ficheService = ficheService;
        this.suppressionService = suppressionService;
        this.suiviService = suiviService;
        this.catalogueService = catalogueService;
    }

    // ── referentiel des types d'elevage ─────────────────────────────────────

    @GetMapping("/types-elevage")
    public List<TypeElevageReponse> typesElevage(@RequestParam(required = false) Filiere filiere) {
        return referentielService.typesElevage(filiere);
    }

    @GetMapping("/types-elevage/{id}")
    public TypeElevageReponse typeElevage(@PathVariable Long id) {
        return referentielService.typeElevage(id);
    }

    @PostMapping("/types-elevage")
    @ResponseStatus(HttpStatus.CREATED)
    public TypeElevageReponse creerTypeElevage(@Valid @RequestBody TypeElevageRequete requete) {
        return referentielService.creerTypeElevage(requete);
    }

    @PutMapping("/types-elevage/{id}")
    public TypeElevageReponse modifierTypeElevage(@PathVariable Long id,
                                                  @Valid @RequestBody TypeElevageRequete requete) {
        return referentielService.modifierTypeElevage(id, requete);
    }

    /**
     * Suppression DEFINITIVE ; 409 si un elevage emploie ce type (voir
     * ReferentielService.supprimerTypeElevage).
     */
    @DeleteMapping("/types-elevage/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerTypeElevage(@PathVariable Long id) {
        referentielService.supprimerTypeElevage(id);
    }

    /** Retrait (suppression logique, reversible). */
    @PostMapping("/types-elevage/{id}/retrait")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirerTypeElevage(@PathVariable Long id) {
        referentielService.retirerTypeElevage(id);
    }

    @PostMapping("/types-elevage/{id}/reactivation")
    public TypeElevageReponse reactiverTypeElevage(@PathVariable Long id) {
        return referentielService.reactiverTypeElevage(id);
    }

    /** Protocole sanitaire du type : les rappels engendres a la creation d'un elevage. */
    @GetMapping("/types-elevage/{id}/protocole")
    public List<EtapeProtocoleReponse> protocole(@PathVariable Long id) {
        return catalogueService.protocole(id);
    }

    /** Remplace le protocole ; voir CatalogueAdministrationService.remplacerProtocole. */
    @PutMapping("/types-elevage/{id}/protocole")
    public List<EtapeProtocoleReponse> remplacerProtocole(@PathVariable Long id,
                                                          @Valid @RequestBody ProtocoleRequete requete) {
        return catalogueService.remplacerProtocole(id, requete.etapes());
    }

    // ── catalogue des maladies ──────────────────────────────────────────────

    /** Vue administration : le traitement complet, meme sur une maladie orientee. */
    /** {@code retirees=true} : avec les maladies retirées, pour pouvoir les réactiver. */
    @GetMapping("/maladies")
    public List<MaladieAdminReponse> maladies(
            @RequestParam(defaultValue = "false") boolean retirees) {
        return referentielService.maladiesPourAdministration(retirees);
    }

    /** Remet au catalogue une maladie retirée. */
    @PostMapping("/maladies/{id}/reactivation")
    public MaladieAdminReponse reactiverMaladie(@PathVariable Long id) {
        return referentielService.reactiverMaladie(id);
    }

    @GetMapping("/maladies/{id}")
    public MaladieAdminReponse maladie(@PathVariable Long id) {
        return referentielService.maladiePourAdministration(id);
    }

    @PostMapping("/maladies")
    @ResponseStatus(HttpStatus.CREATED)
    public MaladieAdminReponse creerMaladie(@Valid @RequestBody MaladieRequete requete) {
        return referentielService.creerMaladie(requete);
    }

    // ── moderation ──────────────────────────────────────────────────────────

    /** Ecran « Conseils » : tous statuts sauf brouillon, filtrable. */
    @GetMapping("/conseils")
    public List<ConseilDetailReponse> conseils(
            @RequestParam(required = false) StatutConseil statut) {
        return conseilService.pourAdministration(statut);
    }

    /** Ecran « Ajouter un conseil » : publie directement. */
    @PostMapping("/conseils")
    @ResponseStatus(HttpStatus.CREATED)
    public ConseilDetailReponse publierConseil(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @Valid @RequestBody ConseilRequete requete) {
        return conseilService.publierDirectement(connecte.administrateur(), requete);
    }

    @GetMapping("/moderation")
    public List<ConseilDetailReponse> fileDeModeration() {
        return conseilService.fileDeModeration();
    }

    @PostMapping("/moderation/{conseilId}")
    public ConseilDetailReponse moderer(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                        @PathVariable Long conseilId,
                                        @Valid @RequestBody ModerationRequete requete) {
        return conseilService.moderer(connecte.administrateur(), conseilId, requete);
    }

    // ── signalements ────────────────────────────────────────────────────────

    /** Ecran « Signalements » : vue A_ATTRIBUER, ORIENTES, EN_SUIVI, TRAITES ou TOUS. */
    @GetMapping("/signalements")
    public PageReponse<SignalementAdminReponse> signalements(
            @RequestParam(defaultValue = "TOUS") SuiviSignalementService.Vue vue,
            @RequestParam(defaultValue = "0") int page) {
        return suiviService.lister(vue, Pagination.de(page, TAILLE_PAGE));
    }

    /** Les veterinaires a qui confier ce cas, les mieux places d'abord. */
    @GetMapping("/signalements/{signalementId}/candidats")
    public List<CandidatVeterinaireReponse> candidats(@PathVariable Long signalementId) {
        return suiviService.candidats(signalementId);
    }

    /** Attribution manuelle d'un cas a un veterinaire precis. */
    @PostMapping("/signalements/{signalementId}/orientation")
    public SignalementAdminReponse attribuer(@PathVariable Long signalementId,
                                             @Valid @RequestBody AttributionRequete requete) {
        return suiviService.attribuer(signalementId, requete.veterinaireId());
    }

    // ── alertes ─────────────────────────────────────────────────────────────

    @GetMapping("/alertes")
    public List<AlerteReponse> alertes() {
        return alerteService.toutes();
    }

    /**
     * Ce que l'ecran de declenchement affiche AVANT confirmation : combien de
     * signalements recents, et combien d'eleveurs seront notifies. Ces deux
     * nombres ne declenchent rien — ils informent la decision.
     */
    @GetMapping("/alertes/apercu")
    public ApercuAlerte apercu(@RequestParam Long maladieId,
                               @RequestParam Long zoneId,
                               @RequestParam(required = false) Filiere filiere) {
        return new ApercuAlerte(
                alerteService.compterSignalementsRecents(maladieId, zoneId),
                alerteService.compterEleveursCibles(maladieId, zoneId, filiere));
    }

    @GetMapping("/alertes/{alerteId}")
    public AlerteReponse alerte(@PathVariable Long alerteId) {
        return alerteService.une(alerteId);
    }

    @PostMapping("/alertes")
    @ResponseStatus(HttpStatus.CREATED)
    public AlerteReponse declencher(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                    @Valid @RequestBody AlerteRequete requete) {
        return alerteService.declencher(connecte.administrateur(), requete);
    }

    @PostMapping("/alertes/{alerteId}/cloture")
    public AlerteReponse cloturer(@PathVariable Long alerteId) {
        return alerteService.cloturer(alerteId);
    }

    // ── comptes veterinaires ────────────────────────────────────────────────

    /**
     * Cree le compte et envoie un mot de passe provisoire par courriel.
     * L'administration ne choisit pas de mot de passe : un mot de passe saisi
     * par un tiers est un mot de passe connu d'un tiers.
     */
    @PostMapping("/veterinaires")
    @ResponseStatus(HttpStatus.CREATED)
    public VeterinaireAdminReponse creerVeterinaire(
            @Valid @RequestBody VeterinaireRequete requete) {
        return utilisateurService.creerVeterinaire(requete);
    }

    /** Les deux nombres affiches avant de declencher une alerte. */
    // ── comptes utilisateurs ────────────────────────────────────────────────

    /**
     * Le tableau « Utilisateurs » : les eleveurs inscrits.
     *
     * <p>Les veterinaires ont leur propre ecran — la maquette intitule celui-ci
     * « Éleveurs inscrits sur la plateforme ». Les trois filtres sont ceux
     * qu'elle affiche : filiere, zone, statut.
     *
     * <p>{@code actif} vaut {@code null} par defaut, et rend donc les comptes
     * actifs ET desactives. C'est ce que montre la maquette : la derniere ligne
     * du tableau porte le badge « Désactivé ». Filtrer sur les actifs par defaut
     * cacherait a l'administrateur exactement les comptes sur lesquels on
     * l'appelle.
     */
    @GetMapping("/utilisateurs")
    public PageReponse<UtilisateurResumeReponse> utilisateurs(
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) Filiere filiere,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Boolean actif,
            @RequestParam(defaultValue = "0") int page) {
        return utilisateurService.rechercherEleveurs(
                recherche, filiere, zoneId, actif, Pagination.de(page, TAILLE_PAGE));
    }

    /**
     * Remet a zero le mot de passe d'un compte, et envoie le provisoire a son
     * titulaire par courriel.
     *
     * <p>Fonctionne pour TOUT role — eleveur, veterinaire, autre administrateur.
     * C'est voulu : un veterinaire qui perd son acces a le meme besoin qu'un
     * eleveur, et la creation de compte veterinaire passe deja par un mot de
     * passe provisoire envoye de la meme facon.
     *
     * <p>204 et aucun corps : surtout <b>pas le mot de passe</b>. Le Javadoc de
     * {@code UtilisateurService.reinitialiserMotDePasse} explique pourquoi
     * l'administrateur ne doit jamais le lire.
     *
     * <p>Chemin en {@code /mot-de-passe} et non {@code /reinitialiser-mot-de-passe}
     * pour rester sur la ressource : c'est le mot de passe du compte qu'on
     * remplace.
     */
    @PostMapping("/utilisateurs/{id}/mot-de-passe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reinitialiserMotDePasse(@PathVariable Long id) {
        utilisateurService.reinitialiserMotDePasse(id);
    }

    /**
     * Active ou desactive un compte. La maquette l'appelle « Désactiver ce
     * compte », avec confirmation.
     *
     * <p>Une seule route pour les deux sens, avec un parametre : deux routes
     * {@code /desactiver} et {@code /reactiver} dupliqueraient la revocation des
     * sessions et finiraient par divergeer. L'etat voulu est passe en clair.
     */
    @PostMapping("/utilisateurs/{id}/activation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changerActivation(@PathVariable Long id, @RequestParam boolean actif) {
        utilisateurService.changerActivation(id, actif);
    }

    /**
     * Supprime definitivement un compte eleveur ou veterinaire, avec ce qui
     * lui appartient. {@link SuppressionCompteService} detaille ce qui part
     * et ce qui reste. La console demande confirmation avant d'appeler.
     */
    @DeleteMapping("/utilisateurs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerUtilisateur(@PathVariable Long id) {
        suppressionService.supprimer(id);
    }

    /** La fiche d'un compte : ecran « Fiche utilisateur ». */
    @GetMapping("/utilisateurs/{id}/fiche")
    public FicheUtilisateurReponse ficheUtilisateur(@PathVariable Long id) {
        return ficheService.fiche(id);
    }

    @GetMapping("/utilisateurs/{id}")
    public ProfilReponse utilisateur(@PathVariable Long id) {
        return utilisateurService.fichePourAdministration(id);
    }

    /**
     * Corrige la fiche d'un compte : ecran « Modifier un utilisateur ».
     *
     * <p>C'est par ici que passe la reparation d'un compte dont l'adresse est
     * perdue : on corrige l'adresse, puis on reinitialise le mot de passe, qui
     * part alors a la bonne boite. L'ANCIENNE adresse est prevenue du
     * changement — y compris quand c'est l'administration qui le fait.
     */
    @PutMapping("/utilisateurs/{id}")
    public ProfilReponse modifierUtilisateur(@PathVariable Long id,
                                             @Valid @RequestBody ProfilRequete requete) {
        return utilisateurService.modifierParAdministration(id, requete);
    }

    /**
     * Le tableau « Vétérinaires ».
     *
     * <p>Distinct de {@code GET /api/veterinaires}, qui est l'annuaire des
     * eleveurs : celui-la ne montre que les comptes actifs d'une zone, ce qui
     * cacherait a l'administration exactement ceux sur lesquels on l'appelle.
     */
    @GetMapping("/veterinaires")
    public List<VeterinaireAdminReponse> veterinaires(
            @RequestParam(required = false) Long zoneId) {
        return utilisateurService.veterinairesPourAdministration(zoneId);
    }

    /** Ecran « Modifier un vétérinaire ». */
    @GetMapping("/veterinaires/{id}")
    public VeterinaireAdminReponse veterinaire(@PathVariable Long id) {
        return utilisateurService.veterinairePourAdministration(id);
    }

    @PutMapping("/veterinaires/{id}")
    public VeterinaireAdminReponse modifierVeterinaire(
            @PathVariable Long id, @Valid @RequestBody VeterinaireRequete requete) {
        return utilisateurService.modifierVeterinaire(id, requete);
    }

    /**
     * Declare qu'un veterinaire traite une maladie, depuis l'administration.
     *
     * <p>Les memes deux routes existent dans l'espace du veterinaire, sur son
     * propre compte. Celles-ci portent un identifiant parce que
     * l'administration agit sur le compte d'un autre — ecran « Modifier un
     * vétérinaire », bloc « Maladies traitées ».
     */
    @PostMapping("/veterinaires/{id}/competences/{maladieId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declarerCompetence(@PathVariable Long id, @PathVariable Long maladieId) {
        utilisateurService.declarerCompetencePourVeterinaire(id, maladieId);
    }

    @DeleteMapping("/veterinaires/{id}/competences/{maladieId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirerCompetence(@PathVariable Long id, @PathVariable Long maladieId) {
        utilisateurService.retirerCompetencePourVeterinaire(id, maladieId);
    }

    /** Ecran « Modifier une maladie ». */
    @PutMapping("/maladies/{id}")
    public MaladieAdminReponse modifierMaladie(@PathVariable Long id,
                                          @Valid @RequestBody MaladieRequete requete) {
        return referentielService.modifierMaladie(id, requete);
    }

    /**
     * « Retirer du catalogue » : la maladie est DESACTIVEE, pas supprimee.
     *
     * <p>Elle est citee par des signalements, des alertes et des competences.
     * Le service explique pourquoi la supprimer emporterait l'historique
     * sanitaire des eleveurs qui l'ont signalee.
     */
    @DeleteMapping("/maladies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirerMaladie(@PathVariable Long id) {
        // Retire aussi les competences declarees sur la maladie.
        catalogueService.retirerMaladie(id);
    }

    /**
     * Reformule le message d'une alerte en cours : ecran « Modifier une
     * alerte ».
     *
     * <p>Le message et la date de fin. La maladie, la zone et la filiere sont
     * figees au declenchement, avec la portee qui en decoule.
     */
    @PutMapping("/alertes/{alerteId}")
    public AlerteReponse modifierAlerte(@PathVariable Long alerteId,
                                        @Valid @RequestBody MessageAlerteRequete requete) {
        return alerteService.modifierMessage(alerteId, requete.message(), requete.dateFin());
    }

    // ── pilotage ────────────────────────────────────────────────────────────

    /**
     * L'ecran d'accueil de l'administration : huit tuiles et deux tableaux.
     *
     * <p>Une vingtaine d'agregats en une reponse, volontairement. Huit appels
     * feraient apparaitre l'ecran par morceaux, et deux tuiles pourraient
     * montrer des chiffres pris a des instants differents — « 94 signalements »
     * a cote de « 93 » une seconde plus tard.
     */
    @GetMapping("/tableau-de-bord")
    public TableauDeBordAdminReponse tableauDeBord() {
        return statistiqueService.tableauDeBord();
    }

    /**
     * L'ecran « Statistiques ».
     *
     * <p>Deux chiffres portent une definition, pas seulement un calcul : le
     * taux de retention et le delai moyen de prise en charge. Elles sont
     * ecrites dans {@code StatistiquesReponse}, et doivent etre enoncees avec
     * les chiffres — un taux de retention sans sa definition ne veut rien dire.
     */
    /** Les series des graphiques de l'ecran « Statistiques ». */
    @GetMapping("/statistiques/series")
    public SeriesStatistiquesReponse seriesStatistiques() {
        return statistiqueService.series();
    }

    @GetMapping("/statistiques")
    public StatistiquesReponse statistiques() {
        return statistiqueService.statistiques();
    }

    public record ApercuAlerte(long signalementsRecents, long eleveursCibles) { }
}
