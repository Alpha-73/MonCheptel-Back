package ml.moncheptel.api.controleur;

import jakarta.validation.Valid;
import java.util.List;
import ml.moncheptel.api.dto.reponse.DisponibiliteReponse;
import ml.moncheptel.api.dto.requete.DisponibiliteRequete;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ml.moncheptel.api.dto.reponse.AccueilVeterinaireReponse;
import ml.moncheptel.api.dto.reponse.MaladieReponse;
import ml.moncheptel.api.dto.reponse.MaladieAdminReponse;
import ml.moncheptel.api.dto.requete.MaladieRequete;
import ml.moncheptel.api.service.ReferentielService;
import ml.moncheptel.api.mapper.MaladieMapper;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.modele.enumeration.StatutConseil;
import ml.moncheptel.api.securite.UtilisateurConnecte;
import ml.moncheptel.api.service.ConseilService;
import ml.moncheptel.api.service.SignalementService;
import ml.moncheptel.api.service.UtilisateurService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * L'espace propre du veterinaire : ce qu'il declare sur lui-meme.
 *
 * <h2>Pourquoi ce controleur existait en creux</h2>
 *
 * <p>{@code UtilisateurService.declarerCompetence} et
 * {@code retirerCompetence} etaient ecrites, annotees, couvertes par l'audit
 * statique — et <b>aucune route ne les appelait</b>. L'ecran
 * « Veto - Competences » de la maquette, avec sa liste de maladies a cocher et
 * son bouton Enregistrer, n'avait rien derriere lui.
 *
 * <p>C'est la forme de manque la plus facile a rater : rien n'echoue, rien ne
 * se compile mal, et le service passe tous les controles. Seul un
 * rapprochement entre les ecrans et les routes la fait apparaitre.
 *
 * <h2>Pourquoi une classe a part, et non {@code AnnuaireControleur}</h2>
 *
 * <p>L'annuaire est consulte par les ELEVEURS : il rend la liste des
 * veterinaires de leur zone. Ces deux routes-ci sont ecrites par le
 * VETERINAIRE sur son propre compte. Deux publics, deux regles d'acces — la
 * seconde est fermee par {@code @PreAuthorize} sur la classe entiere.
 *
 * <p>Les melanger obligerait a poser l'annotation methode par methode, et il
 * suffirait d'en oublier une pour ouvrir l'ecriture a tout le monde.
 *
 * <h2>Pourquoi POST et DELETE plutot qu'un PUT de la liste entiere</h2>
 *
 * <p>L'ecran coche et decoche des maladies une par une. Envoyer la liste
 * complete a chaque changement obligerait le client a la tenir a jour, et
 * ferait perdre une declaration faite entre-temps depuis un autre appareil :
 * le dernier qui enregistre ecraserait l'autre.
 *
 * <p>Une competence est une ligne, on l'ajoute ou on la retire. Le conflit
 * disparait.
 */
@RestController
@RequestMapping("/api/veterinaires")
@PreAuthorize("hasRole('VETERINAIRE')")
public class VeterinaireControleur {

    private final UtilisateurService utilisateurService;
    private final SignalementService signalementService;
    private final ConseilService conseilService;
    private final MaladieMapper maladieMapper;
    private final ReferentielService referentielService;

    public VeterinaireControleur(UtilisateurService utilisateurService,
                                 SignalementService signalementService,
                                 ConseilService conseilService,
                                 MaladieMapper maladieMapper,
                                 ReferentielService referentielService) {
        this.utilisateurService = utilisateurService;
        this.signalementService = signalementService;
        this.conseilService = conseilService;
        this.maladieMapper = maladieMapper;
        this.referentielService = referentielService;
    }

    /** Ecran « Veto - Accueil » : les compteurs du haut de page. */
    @GetMapping("/accueil")
    public AccueilVeterinaireReponse accueil(@AuthenticationPrincipal UtilisateurConnecte connecte) {
        Veterinaire veterinaire = connecte.veterinaire();
        return signalementService.accueil(veterinaire,
                conseilService.compterPourAuteur(veterinaire, StatutConseil.PUBLIE),
                conseilService.compterPourAuteur(veterinaire, StatutConseil.EN_MODERATION));
    }

    /**
     * Interrupteur « Disponible / Indisponible ». Indisponible, le veterinaire
     * ne peut plus etre choisi par les eleveurs et rend les cas qu'il
     * n'a pas encore ouverts.
     */
    @PutMapping("/disponibilite")
    public DisponibiliteReponse changerDisponibilite(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @Valid @RequestBody DisponibiliteRequete requete) {
        return new DisponibiliteReponse(
                utilisateurService.changerDisponibilite(connecte.veterinaire(), requete.disponible()));
    }

    /** Ecran « Mes compétences » : les maladies deja cochees. */
    @GetMapping("/competences")
    public List<MaladieReponse> competences(@AuthenticationPrincipal UtilisateurConnecte connecte) {
        return utilisateurService.competencesDe(connecte.veterinaire(),
                maladieMapper::versReponseAdmin);
    }

    /**
     * Declare que ce veterinaire traite cette maladie.
     *
     * <p>409 si la competence est deja declaree : le service leve
     * {@code ConflitException}. Ce n'est pas un detail de confort — sans la
     * contrainte d'unicite et ce refus, un double clic creerait deux lignes,
     * et l'ecran afficherait « 4 competences » pour trois maladies.
     */
    @PostMapping("/competences/{maladieId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declarerCompetence(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                   @PathVariable Long maladieId) {
        utilisateurService.declarerCompetence(connecte.veterinaire(), maladieId);
    }

    /**
     * Le veterinaire ajoute une maladie au catalogue, avec ses symptomes
     * ponderes. Elle est utilisable tout de suite, et il en devient
     * automatiquement competent. L'administration est prevenue, voit son
     * auteur, et peut la corriger ou la retirer ensuite.
     */
    @PostMapping("/maladies")
    @ResponseStatus(HttpStatus.CREATED)
    public MaladieAdminReponse creerMaladie(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                            @Valid @RequestBody MaladieRequete requete) {
        MaladieAdminReponse creee = referentielService.creerMaladieParVeterinaire(
                requete, connecte.veterinaire().getNom());
        utilisateurService.declarerCompetence(connecte.veterinaire(), creee.id());
        return creee;
    }

    /**
     * Retire la competence.
     *
     * <p>204 meme si elle n'existait pas : une suppression est idempotente.
     * Rendre 404 ferait echouer un second appel declenche par un reseau lent,
     * alors que l'etat voulu — la competence n'est plus la — est atteint.
     */
    @DeleteMapping("/competences/{maladieId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirerCompetence(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                  @PathVariable Long maladieId) {
        utilisateurService.retirerCompetence(connecte.veterinaire(), maladieId);
    }
}
