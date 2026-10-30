package ml.moncheptel.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import ml.moncheptel.api.dto.reponse.ErreurReponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Traduit toute exception en une reponse de forme unique.
 *
 * <h2>Pourquoi un seul endroit</h2>
 *
 * <p>Sans lui, chaque controleur attraperait ses propres erreurs et produirait
 * son propre format. Le client Flutter devrait alors connaitre autant de
 * formes d'erreur qu'il y a de routes, et afficherait « une erreur est
 * survenue » partout ou il n'a pas su lire.
 *
 * <h2>Deux niveaux de journalisation, deux publics</h2>
 *
 * <p>Une erreur METIER — effectif insuffisant, doublon — n'est pas un incident :
 * le systeme a fait exactement ce qu'il devait. Elle est journalisee en
 * {@code debug}, et son message part a l'utilisateur.
 *
 * <p>Une erreur INATTENDUE est un incident : trace complete en {@code error},
 * et un message neutre a l'utilisateur. La trace ne quitte JAMAIS le serveur —
 * elle nomme les classes, les tables et parfois les valeurs, c'est-a-dire une
 * carte du systeme offerte a qui provoque l'erreur.
 */
@RestControllerAdvice
public class GestionnaireExceptions {

    private static final Logger JOURNAL = LoggerFactory.getLogger(GestionnaireExceptions.class);

    /**
     * Vrai uniquement en profil {@code dev}. Decide si les traces des erreurs
     * inattendues sont AUSSI deposees dans {@code target/erreurs-dev.log} —
     * voir {@link #deposerLaTrace}.
     */
    private final boolean profilDev;

    public GestionnaireExceptions(Environment environnement) {
        this.profilDev = environnement.matchesProfiles("dev");
    }

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<ErreurReponse> introuvable(RessourceIntrouvableException e,
                                                     HttpServletRequest requete) {
        JOURNAL.debug("Ressource introuvable : {}", e.getMessage());
        return reponse(HttpStatus.NOT_FOUND, "Introuvable", e.getMessage(), requete);
    }

    /**
     * Adresse inconnue (faute de frappe, Swagger ferme en production). Sans
     * ce gestionnaire, le filet general plus bas la transformait en 500 et
     * l'inscrivait comme une panne dans le journal des erreurs.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErreurReponse> adresseInconnue(NoResourceFoundException e,
                                                         HttpServletRequest requete) {
        return reponse(HttpStatus.NOT_FOUND, "Introuvable",
                "Cette adresse n'existe pas", requete);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErreurReponse> methodeRefusee(HttpRequestMethodNotSupportedException e,
                                                        HttpServletRequest requete) {
        return reponse(HttpStatus.METHOD_NOT_ALLOWED, "Methode non autorisee",
                "Méthode " + e.getMethod() + " non prise en charge sur cette adresse", requete);
    }

    @ExceptionHandler(RegleMetierException.class)
    public ResponseEntity<ErreurReponse> regleMetier(RegleMetierException e,
                                                     HttpServletRequest requete) {
        JOURNAL.debug("Regle metier : {}", e.getMessage());
        return reponse(HttpStatus.UNPROCESSABLE_ENTITY, "Regle metier", e.getMessage(), requete);
    }

    @ExceptionHandler(ConflitException.class)
    public ResponseEntity<ErreurReponse> conflit(ConflitException e, HttpServletRequest requete) {
        JOURNAL.debug("Conflit : {}", e.getMessage());
        return reponse(HttpStatus.CONFLICT, "Conflit", e.getMessage(), requete);
    }

    /**
     * Deux ecritures concurrentes sur la meme ligne versionnee (@Version) :
     * la seconde echoue. Typiquement une cloture et une reattribution
     * simultanees d'un signalement (audit veterinaire M4).
     */
    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class,
            jakarta.persistence.OptimisticLockException.class})
    public ResponseEntity<ErreurReponse> conflitConcurrent(Exception e, HttpServletRequest requete) {
        JOURNAL.info("Conflit de version sur {} : {}", requete.getRequestURI(), e.getMessage());
        String message = requete.getRequestURI().startsWith("/api/signalements")
                || requete.getRequestURI().contains("/signalements/")
                ? "Ce cas vient d'être modifié par quelqu'un d'autre (clôturé ou confié à un "
                  + "confrère). Rechargez-le avant de recommencer."
                : "Ces données viennent d'être modifiées par quelqu'un d'autre. "
                  + "Rechargez la page avant de recommencer.";
        return reponse(HttpStatus.CONFLICT, "Conflit", message, requete);
    }

    /**
     * Une contrainte de la base a refuse l'ecriture (audit global G12) :
     * typiquement deux inscriptions simultanees avec la meme adresse, que le
     * controle prealable du service n'a pas pu voir. 409 et non 500 : la
     * demande est valable, elle arrive juste apres une autre.
     *
     * <p>Le detail SQL n'est pas rendu : il nomme tables et valeurs.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErreurReponse> contrainteViolee(
            org.springframework.dao.DataIntegrityViolationException e,
            HttpServletRequest requete) {
        JOURNAL.info("Contrainte de la base violee sur {} : {}", requete.getRequestURI(),
                e.getMostSpecificCause().getMessage());
        return reponse(HttpStatus.CONFLICT, "Conflit",
                "Cette donnée existe déjà ou vient d'être modifiée par quelqu'un d'autre. "
                + "Rechargez puis réessayez.", requete);
    }

    @ExceptionHandler(AccesRefuseException.class)
    public ResponseEntity<ErreurReponse> accesRefuse(AccesRefuseException e,
                                                     HttpServletRequest requete) {
        // En warn, pas en debug : une tentative d'acces a la donnee d'autrui
        // merite d'etre vue dans les journaux, meme si elle a echoue.
        JOURNAL.warn("Acces refuse sur {} : {}", requete.getRequestURI(), e.getMessage());
        return reponse(HttpStatus.FORBIDDEN, "Acces refuse", e.getMessage(), requete);
    }

    /**
     * Erreurs de validation : un message par champ fautif.
     *
     * <p>C'est ce que le formulaire affiche sous chaque ligne. Une seule
     * phrase globale obligerait l'utilisateur a deviner lequel des huit champs
     * pose probleme.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurReponse> validation(MethodArgumentNotValidException e,
                                                    HttpServletRequest requete) {
        Map<String, String> champs = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(
                erreur -> champs.putIfAbsent(erreur.getField(), erreur.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErreurReponse.validation(requete.getRequestURI(), champs));
    }

    /**
     * Refus d'autorisation prononce par {@code @PreAuthorize}.
     *
     * <h3>Pourquoi ce gestionnaire est indispensable</h3>
     *
     * <p>Spring Security leve une {@code AuthorizationDeniedException} — qui
     * herite d'{@link AccessDeniedException} — quand une methode annotee
     * {@code @PreAuthorize} refuse l'appel. Sans gestionnaire dedie, elle
     * tombait dans le filet {@code Exception.class} ci-dessous et sortait en
     * <b>500</b>.
     *
     * <p>Consequence mesuree a l'execution le 4 octobre : les NEUF routes
     * reservees a un role rendaient « Erreur interne » au lieu de 403. Un
     * eleveur qui touchait une route d'administration voyait un plantage
     * serveur ; les journaux accumulaient des traces {@code error} pour un
     * fonctionnement parfaitement normal ; et un client Flutter n'avait aucun
     * moyen de distinguer « vous n'avez pas le droit » — qu'on affiche — d'un
     * incident serveur — qu'on reessaie.
     *
     * <p>Aucun controle statique ne pouvait le voir : les annotations etaient
     * correctes, les roles bien ecrits, l'audit ne signalait rien. Il fallait
     * appeler la route.
     *
     * <h3>Le message est fixe</h3>
     *
     * <p>{@code e.getMessage()} vaut « Access Denied » ou cite l'expression
     * SpEL evaluee. Ni l'un ni l'autre n'a sa place dans une reponse : le
     * premier n'est pas en francais, le second decrit la regle de securite a
     * qui vient de s'y heurter.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErreurReponse> autorisationRefusee(AccessDeniedException e,
                                                             HttpServletRequest requete) {
        // En warn, comme AccesRefuseException : un appel refuse par le role
        // merite d'etre vu, meme s'il a echoue.
        JOURNAL.warn("Autorisation refusee sur {} : {}", requete.getRequestURI(), e.getMessage());
        return reponse(HttpStatus.FORBIDDEN, "Acces refuse",
                "Cette opération n'est pas autorisée pour votre rôle.", requete);
    }

    /**
     * Parametre de requete absent, mal type, ou en-tete obligatoire manquant.
     *
     * <p>{@code ServletRequestBindingException} couvre
     * {@code MissingServletRequestParameterException} et
     * {@code MissingRequestHeaderException} : c'est leur parent commun.
     *
     * <p>Mesure du 4 octobre : {@code GET /api/finances/operations} sans le
     * parametre {@code debut} rendait <b>500</b>. C'est une faute de l'appelant,
     * pas du serveur, et le client n'avait aucun moyen de le savoir.
     */
    @ExceptionHandler({ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class})
    public ResponseEntity<ErreurReponse> parametreFautif(Exception e,
                                                         HttpServletRequest requete) {
        JOURNAL.debug("Parametre fautif sur {} : {}", requete.getRequestURI(), e.getMessage());
        return reponse(HttpStatus.BAD_REQUEST, "Requete invalide",
                "Un paramètre de la requête est absent ou mal formé.", requete);
    }

    /**
     * Corps de requete illisible : JSON malforme, ou valeur d'enumeration
     * inconnue.
     *
     * <p>Sans ce gestionnaire, envoyer {@code "type": "ABATTAGE"} — une
     * constante qui n'existe pas dans {@code TypeMouvement} — rendait 500 au
     * lieu de 400. L'appelant doit pouvoir corriger sa requete.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErreurReponse> corpsIllisible(HttpMessageNotReadableException e,
                                                        HttpServletRequest requete) {
        JOURNAL.debug("Corps illisible sur {} : {}", requete.getRequestURI(), e.getMessage());
        return reponse(HttpStatus.BAD_REQUEST, "Requete invalide",
                "Le corps de la requête est illisible, ou contient une valeur inconnue.",
                requete);
    }

    /**
     * Validation portee par un parametre, et non par un corps de requete.
     *
     * <p>{@code MethodArgumentNotValidException} ci-dessus ne couvre que les
     * {@code @RequestBody}. Un {@code @RequestParam} valide par {@code @Min}
     * leve une {@code ConstraintViolationException}, sans aucun rapport de
     * hierarchie avec la precedente.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErreurReponse> contrainteViolee(ConstraintViolationException e,
                                                          HttpServletRequest requete) {
        Map<String, String> champs = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(v -> champs.putIfAbsent(
                derniereEtape(v.getPropertyPath().toString()), v.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErreurReponse.validation(requete.getRequestURI(), champs));
    }

    /** « lire.filiere » -> « filiere » : le nom de la methode n'interesse personne. */
    private static String derniereEtape(String chemin) {
        int point = chemin.lastIndexOf('.');
        return point < 0 ? chemin : chemin.substring(point + 1);
    }

    /**
     * Filet de securite.
     *
     * <p>Le message envoye est fixe. Renvoyer {@code e.getMessage()} laisserait
     * fuir des noms de tables, de colonnes, parfois des fragments de requete
     * SQL — assez pour dessiner le schema depuis l'exterieur.
     *
     * <p><b>Ce filet est dangereux par nature</b> : il transforme en 500 toute
     * exception pour laquelle aucun gestionnaire plus precis n'existe, y
     * compris celles qui portaient deja leur propre code HTTP. Les cinq
     * gestionnaires ajoutes au-dessus sont exactement les cas qu'il avalait a
     * tort. Avant d'en ajouter un sixieme, verifier que l'exception n'a pas
     * deja un statut naturel.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErreurReponse> inattendue(Exception e, HttpServletRequest requete) {
        JOURNAL.error("Erreur inattendue sur {}", requete.getRequestURI(), e);
        deposerLaTrace(requete.getRequestURI(), e);
        return reponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne",
                "Une erreur est survenue. Réessayez dans un instant.", requete);
    }

    /**
     * En profil {@code dev} seulement : la trace va aussi dans
     * {@code target/erreurs-dev.log}.
     *
     * <h3>Pourquoi ce fichier existe</h3>
     *
     * <p>La trace part en {@code error} dans la console du serveur, et c'est
     * le bon endroit. Mais une console de developpement defile : le serveur
     * tourne des heures, Hibernate y ecrit chaque requete en DEBUG, et
     * retrouver LA trace d'un 500 survenu vingt minutes plus tot demande de
     * remonter des milliers de lignes. Sur un terminal Windows dont la
     * memoire tampon est bornee, elle a souvent deja disparu.
     *
     * <p>Mesure du 4 octobre : la route de signalement rendait 500, et il a
     * fallu trois demandes successives pour obtenir la trace — qui etait
     * pourtant ecrite depuis le debut. Un fichier qu'on envoie tel quel evite
     * cet aller-retour.
     *
     * <h3>Pourquoi c'est borne au profil dev</h3>
     *
     * <p>Une trace nomme les classes, les tables et parfois les valeurs : en
     * production elle appartient a l'infrastructure de journalisation, qui
     * sait la faire tourner, la chiffrer et la purger. Un fichier ecrit a la
     * main a cote de l'application n'a aucune de ces proprietes. Hors profil
     * {@code dev}, cette methode ne fait donc rien.
     *
     * <p>L'echec d'ecriture est tu pour la meme raison que dans
     * {@code ServiceCourrielJournal} : un disque plein ne doit pas transformer
     * un 500 en autre chose. La console a deja recu la trace.
     */
    private void deposerLaTrace(String chemin, Exception e) {
        if (!profilDev) {
            return;
        }
        try {
            StringWriter tampon = new StringWriter();
            e.printStackTrace(new PrintWriter(tampon));
            String bloc = System.lineSeparator()
                    + "================================================================"
                    + System.lineSeparator()
                    + Instant.now() + "  ERREUR INATTENDUE SUR " + chemin
                    + System.lineSeparator()
                    + "================================================================"
                    + System.lineSeparator()
                    + tampon;
            Path dossier = Path.of("target");
            Files.createDirectories(dossier);
            Files.writeString(dossier.resolve("erreurs-dev.log"), bloc,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignore) {
            JOURNAL.debug("Trace non deposee dans target/erreurs-dev.log : {}",
                    ignore.getMessage());
        }
    }

    private static ResponseEntity<ErreurReponse> reponse(HttpStatus statut, String libelle,
                                                         String message,
                                                         HttpServletRequest requete) {
        return ResponseEntity.status(statut).body(ErreurReponse.simple(
                statut.value(), libelle, message, requete.getRequestURI()));
    }
}