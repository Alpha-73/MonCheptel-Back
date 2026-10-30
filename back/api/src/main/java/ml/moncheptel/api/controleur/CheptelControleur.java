package ml.moncheptel.api.controleur;

import jakarta.validation.Valid;
import java.util.List;
import ml.moncheptel.api.dto.reponse.LotDetailReponse;
import ml.moncheptel.api.dto.reponse.UniteResumeReponse;
import ml.moncheptel.api.dto.requete.AnimalRequete;
import ml.moncheptel.api.dto.requete.ClotureRequete;
import ml.moncheptel.api.dto.requete.LotRequete;
import ml.moncheptel.api.dto.requete.SortieRequete;
import ml.moncheptel.api.dto.requete.UniteRequete;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import ml.moncheptel.api.securite.UtilisateurConnecte;
import ml.moncheptel.api.service.CheptelService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cheptel de l'eleveur : lots, animaux, sorties.
 *
 * <p>{@code @PreAuthorize("hasRole('ELEVEUR')")} dit seulement « l'appelant
 * est un eleveur ». Que le lot demande soit LE SIEN est verifie dans
 * {@link CheptelService} — la ou l'identifiant est resolu. Un controleur ne
 * peut pas le savoir sans charger la donnee, et s'il la chargeait il ferait le
 * travail du service.
 */
@RestController
@RequestMapping("/api/cheptel")
@PreAuthorize("hasRole('ELEVEUR')")
public class CheptelControleur {

    private final CheptelService cheptelService;

    public CheptelControleur(CheptelService cheptelService) {
        this.cheptelService = cheptelService;
    }

    /** Ecran « Mes animaux ». {@code filiere} et {@code statut} facultatifs. */
    @GetMapping
    public List<UniteResumeReponse> lister(
            @AuthenticationPrincipal UtilisateurConnecte connecte,
            @RequestParam(required = false) Filiere filiere,
            @RequestParam(required = false) StatutUnite statut) {
        return cheptelService.lister(connecte.eleveur(), filiere, statut);
    }

    @GetMapping("/{uniteId}")
    public LotDetailReponse detailler(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                      @PathVariable Long uniteId) {
        return cheptelService.detailler(connecte.eleveur(), uniteId);
    }

    @PostMapping("/lots")
    @ResponseStatus(HttpStatus.CREATED)
    public UniteResumeReponse creerLot(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                       @Valid @RequestBody LotRequete requete) {
        return cheptelService.creerLot(connecte.eleveur(), requete);
    }

    @PostMapping("/animaux")
    @ResponseStatus(HttpStatus.CREATED)
    public UniteResumeReponse creerAnimal(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                          @Valid @RequestBody AnimalRequete requete) {
        return cheptelService.creerAnimal(connecte.eleveur(), requete);
    }

    /**
     * Vente, mortalite ou transfert.
     *
     * <p>{@code POST} et non {@code PATCH} sur l'unite : une sortie n'est pas
     * une modification de l'unite, c'est un EVENEMENT qu'on lui ajoute. L'URL
     * le dit, et cela evite de laisser croire qu'on pourrait poser
     * l'effectif a la main.
     */
    /**
     * Corrige un lot ou un animal : ecran « Modifier le lot ».
     *
     * <p>PUT et non PATCH : l'ecran affiche tous les champs remplis et renvoie
     * l'etat complet. L'EFFECTIF n'est pas dans le corps, et ce n'est pas un
     * oubli — voir {@link UniteRequete}.
     */
    @PutMapping("/{uniteId}")
    public UniteResumeReponse modifier(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                       @PathVariable Long uniteId,
                                       @Valid @RequestBody UniteRequete requete) {
        return cheptelService.modifier(connecte.eleveur(), uniteId, requete);
    }

    /**
     * Supprime un elevage, et refuse des qu'il a une histoire.
     *
     * <p>422 avec la raison dans la plupart des cas : un lot qui porte un
     * carnet sanitaire, des operations ou des mouvements ne se supprime pas,
     * il se cloture. Le service explique pourquoi en detail.
     */
    @DeleteMapping("/{uniteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@AuthenticationPrincipal UtilisateurConnecte connecte,
                          @PathVariable Long uniteId) {
        cheptelService.supprimer(connecte.eleveur(), uniteId);
    }

    @PostMapping("/{uniteId}/sorties")
    @ResponseStatus(HttpStatus.CREATED)
    public UniteResumeReponse sortir(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                     @PathVariable Long uniteId,
                                     @Valid @RequestBody SortieRequete requete) {
        return cheptelService.sortir(connecte.eleveur(), uniteId, requete);
    }

    /**
     * Annule une sortie saisie par erreur : 204. {@code sortieId} est l'id du
     * mouvement de sortie ({@code sorties[].id} du detail). Les tetes
     * reviennent, le revenu d'une vente est supprime.
     */
    @DeleteMapping("/{uniteId}/sorties/{sortieId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void annulerSortie(@AuthenticationPrincipal UtilisateurConnecte connecte,
                              @PathVariable Long uniteId,
                              @PathVariable Long sortieId) {
        cheptelService.annulerSortie(connecte.eleveur(), uniteId, sortieId);
    }

    /**
     * Termine le lot ou l'animal (« Terminer » dans le carnet) : 200 avec
     * l'unite, statut CLOTURE. Ses rappels ouverts sont ecartes.
     *
     * <p>S'il reste des tetes, le corps {@link ClotureRequete} dit ce qu'elles
     * sont devenues ; sans lui, 422 (audit global G5).
     */
    @PostMapping("/{uniteId}/cloture")
    public UniteResumeReponse terminer(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                       @PathVariable Long uniteId,
                                       @Valid @RequestBody(required = false)
                                       ClotureRequete requete) {
        return cheptelService.terminer(connecte.eleveur(), uniteId, requete);
    }
}
