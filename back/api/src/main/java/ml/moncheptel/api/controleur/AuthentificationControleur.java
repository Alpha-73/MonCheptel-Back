package ml.moncheptel.api.controleur;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import ml.moncheptel.api.dto.reponse.JetonReponse;
import ml.moncheptel.api.dto.reponse.MessageReponse;
import ml.moncheptel.api.dto.reponse.ProfilReponse;
import ml.moncheptel.api.dto.requete.ChangementMotDePasseRequete;
import ml.moncheptel.api.dto.requete.ConnexionRequete;
import ml.moncheptel.api.dto.requete.DemandeReinitialisationRequete;
import ml.moncheptel.api.dto.requete.InscriptionRequete;
import ml.moncheptel.api.dto.requete.ProfilRequete;
import ml.moncheptel.api.dto.requete.RafraichissementRequete;
import ml.moncheptel.api.dto.requete.ReinitialisationRequete;
import ml.moncheptel.api.securite.UtilisateurConnecte;
import ml.moncheptel.api.service.AuthentificationService;
import ml.moncheptel.api.service.UtilisateurService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthentificationControleur {

    private final AuthentificationService authentificationService;
    private final UtilisateurService utilisateurService;

    public AuthentificationControleur(AuthentificationService authentificationService,
                                      UtilisateurService utilisateurService) {
        this.authentificationService = authentificationService;
        this.utilisateurService = utilisateurService;
    }

    @PostMapping("/connexion")
    public JetonReponse connexion(@Valid @RequestBody ConnexionRequete requete,
                                  HttpServletRequest http) {
        return authentificationService.connecter(requete, appareil(http), adresse(http));
    }

    @PostMapping("/inscription")
    @ResponseStatus(HttpStatus.CREATED)
    public JetonReponse inscription(@Valid @RequestBody InscriptionRequete requete,
                                    HttpServletRequest http) {
        return authentificationService.inscrire(requete, appareil(http), adresse(http));
    }

    @GetMapping("/moi")
    public ProfilReponse moi(@AuthenticationPrincipal UtilisateurConnecte connecte) {
        return utilisateurService.profil(connecte.utilisateur());
    }

    /**
     * L'utilisateur corrige son profil : ecrans « Mes informations » et
     * « Langue ».
     *
     * <p>PUT et non PATCH : le corps porte l'etat complet du profil, et c'est
     * ce que l'ecran envoie — il affiche tous les champs remplis. Un PATCH
     * obligerait a distinguer « champ absent » de « champ vide », ce qui est
     * precisement le piege du telephone, qu'on doit pouvoir effacer.
     *
     * <p>Rend le profil a jour plutot que 204 : l'ecran peut ainsi se
     * rafraichir sans un second appel, et le client voit l'adresse telle que
     * le serveur l'a normalisee — en minuscules.
     */
    @PutMapping("/moi")
    public ProfilReponse modifierProfil(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                        @Valid @RequestBody ProfilRequete requete) {
        return utilisateurService.modifierProfil(connecte.utilisateur(), requete);
    }

    /**
     * Change le mot de passe. Toutes les sessions sont revoquees, y compris
     * celle qui fait la demande : l'application doit redemander la connexion.
     */
    @PostMapping("/mot-de-passe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changerMotDePasse(@AuthenticationPrincipal UtilisateurConnecte connecte,
                                  @Valid @RequestBody ChangementMotDePasseRequete requete) {
        authentificationService.changerMotDePasse(connecte.id(), requete);
    }

    /**
     * « Mot de passe oublie », premiere etape : demande l'envoi d'un code.
     *
     * <p>Route PUBLIQUE, par necessite : celui qui a oublie son mot de passe
     * n'a pas de jeton.
     *
     * <h3>202 et non 200, toujours, et un corps fixe</h3>
     *
     * <p>202 Accepted dit exactement ce qui se passe : la demande est prise,
     * un courriel partira peut-etre. 200 laisserait croire que l'envoi est
     * confirme, ce que le serveur ne sait pas — le SMTP est asynchrone et peut
     * echouer apres.
     *
     * <p>Le corps est le MEME message dans tous les cas, adresse connue ou non.
     * Voir {@code AuthentificationService.demanderReinitialisation} pour
     * pourquoi. Le texte dit « si cette adresse correspond a un compte » : il ne
     * doit pas affirmer qu'un courriel a ete envoye.
     */
    @PostMapping("/mot-de-passe/oublie")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageReponse motDePasseOublie(
            @Valid @RequestBody DemandeReinitialisationRequete requete) {
        authentificationService.demanderReinitialisation(requete);
        return new MessageReponse(
                "Si cette adresse correspond à un compte, un code de "
                + "réinitialisation vient d'y être envoyé. Il est valable "
                + "15 minutes.");
    }

    /**
     * « Mot de passe oublie », seconde etape : le code et le nouveau mot de
     * passe.
     *
     * <p>Route PUBLIQUE elle aussi. Ce qui autorise le changement, c'est le
     * code recu dans la boite du titulaire, pas un jeton.
     *
     * <p>204 : il n'y a rien a rendre. En particulier <b>pas de jeton de
     * session</b> — on ne connecte pas automatiquement apres une
     * reinitialisation. L'application renvoie vers l'ecran de connexion, et
     * l'utilisateur saisit le mot de passe qu'il vient de choisir. C'est la
     * seule facon de verifier qu'il l'a bien retenu, et cela evite d'ouvrir une
     * session a partir d'une route publique.
     */
    @PostMapping("/mot-de-passe/reinitialiser")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reinitialiserMotDePasse(
            @Valid @RequestBody ReinitialisationRequete requete) {
        authentificationService.reinitialiser(requete);
    }

    /**
     * Renouvelle la session quand le jeton d'acces a expire.
     *
     * <p>Route PUBLIQUE : on l'appelle justement parce que le jeton d'acces
     * n'est plus bon. Le jeton de rafraichissement est a usage unique ; la
     * reponse en porte un nouveau, que l'application doit conserver.
     */
    @PostMapping("/rafraichir")
    public JetonReponse rafraichir(@Valid @RequestBody RafraichissementRequete requete,
                                   HttpServletRequest http) {
        return authentificationService.rafraichir(requete, appareil(http), adresse(http));
    }

    @PostMapping("/deconnexion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deconnexion(@RequestHeader(name = "X-Refresh-Token", required = false) String jeton) {
        if (jeton != null && !jeton.isBlank()) {
            authentificationService.deconnecter(jeton);
        }
    }

    /**
     * Identification de l'appareil, pour la liste des sessions actives.
     * Purement indicatif : l'en-tete est choisi par le client et ne prouve
     * rien. Il sert a ce qu'un eleveur reconnaisse « son vieux telephone »
     * dans la liste, pas a une decision de securite.
     */
    private static String appareil(HttpServletRequest http) {
        String agent = http.getHeader("User-Agent");
        return agent == null ? "Inconnu" : agent.substring(0, Math.min(agent.length(), 180));
    }

    /**
     * L'adresse du client, telle que Tomcat l'a etablie.
     *
     * <p>L'en-tete {@code X-Forwarded-For} n'est plus lu ici (audit global
     * G16) : n'importe quel client peut l'ecrire. Derriere le proxy HTTPS,
     * {@code server.forward-headers-strategy=native} (application.yml) fait
     * reecrire {@code getRemoteAddr()} par Tomcat, qui ne croit l'en-tete que
     * s'il vient d'un proxy du reseau interne.
     */
    private static String adresse(HttpServletRequest http) {
        return http.getRemoteAddr();
    }
}
