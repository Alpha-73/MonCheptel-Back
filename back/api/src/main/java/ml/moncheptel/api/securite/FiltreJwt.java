package ml.moncheptel.api.securite;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import ml.moncheptel.api.dto.reponse.ErreurReponse;
import ml.moncheptel.api.modele.Utilisateur;
import ml.moncheptel.api.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lit le jeton de l'en-tete {@code Authorization} et pose l'utilisateur dans
 * le contexte de securite.
 *
 * <h2>Pourquoi l'utilisateur est relu en base a chaque requete</h2>
 *
 * <p>Le jeton porte deja l'identifiant et le role : on pourrait s'en contenter
 * et eviter une requete. Ce serait accepter qu'un compte desactive, ou dont
 * le mot de passe a change, continue de fonctionner jusqu'a l'expiration de son jeton — une
 * heure pendant laquelle une revocation ne revoque rien.
 *
 * <p>La lecture est un acces par cle primaire, index unique : son cout est
 * negligeable devant ce qu'elle garantit.
 *
 * <h2>Un jeton invalide ne fait pas echouer la requete</h2>
 *
 * <p>Le filtre laisse passer sans authentifier. C'est la configuration de
 * securite qui decide si la route exigeait une authentification, et repond 401
 * le cas echeant. Un filtre qui repondrait lui-meme casserait les routes
 * publiques — la liste des zones, dont le formulaire d'inscription a besoin.
 */
@Component
public class FiltreJwt extends OncePerRequestFilter {

    /**
     * Journaliseur propre a la classe, et non le champ {@code logger} herite de
     * {@code GenericFilterBean} : celui-la n'est renseigne qu'apres
     * l'initialisation du filtre par le conteneur, et c'est exactement le champ
     * dont l'absence provoquait la {@code NullPointerException} decrite plus
     * bas. Un champ {@code static final} est pret avant toute requete.
     */
    private static final Logger JOURNAL = LoggerFactory.getLogger(FiltreJwt.class);

    private static final String ENTETE = "Authorization";
    private static final String PREFIXE = "Bearer ";

    private final FabriqueJeton fabriqueJeton;
    private final UtilisateurRepository utilisateurRepository;
    private final ObjectMapper json;

    public FiltreJwt(FabriqueJeton fabriqueJeton, UtilisateurRepository utilisateurRepository,
                     ObjectMapper json) {
        this.fabriqueJeton = fabriqueJeton;
        this.utilisateurRepository = utilisateurRepository;
        this.json = json;
    }

    /**
     * <b>Pas de {@code @Transactional} ici, et c'est important.</b>
     *
     * <p>Cette annotation sur un filtre fait envelopper le bean dans un proxy
     * CGLIB. Or {@code GenericFilterBean.init(FilterConfig)} est
     * {@code final} : CGLIB ne peut pas la redefinir, le proxy n'initialise
     * jamais son champ {@code logger}, et Tomcat echoue au demarrage du
     * filtre sur une {@code NullPointerException} qui ne nomme meme pas la
     * cause. Le journal ne donne qu'un indice, en amont :
     *
     * <pre>
     *   Unable to proxy interface-implementing method
     *   [public final void GenericFilterBean.init(FilterConfig)]
     *   because it is marked as final
     * </pre>
     *
     * <p>Et le filtre n'en a pas besoin. {@code chercherAvecZone} est une
     * methode de repository Spring Data : elle s'execute dans sa propre
     * transaction en lecture seule, et sa jointure de chargement ramene la
     * zone avant que la session ne se ferme. L'entite sort detachee mais
     * complete — c'est exactement ce que cette requete est faite pour.
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest requete,
                                    @NonNull HttpServletResponse reponse,
                                    @NonNull FilterChain chaine)
            throws ServletException, IOException {

        String jeton = extraire(requete);
        if (jeton != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            FabriqueJeton.JetonLu lu = fabriqueJeton.lireJetonAcces(jeton);
            // lu nul : FabriqueJeton a deja journalise la raison (signature,
            // expiration, format, mauvais type). Rien a ajouter ici.
            if (lu != null) {
                Long id = lu.id();
                // chercherAvecZone, pas findById : l'entite sort de cette
                // transaction detachee, et les ecrans affichent la zone.
                // Sans le chargement groupe, chaque lecture de la zone
                // leverait une LazyInitializationException.
                var trouve = utilisateurRepository.chercherAvecZone(id);
                if (trouve.isEmpty()) {
                    JOURNAL.debug(
                            "Jeton valide portant l'identifiant {}, mais aucun utilisateur"
                            + " ne porte cet identifiant : compte supprime, ou jeton signe"
                            + " avec le secret d'une autre base. {} reste anonyme.",
                            id, requete.getRequestURI());
                } else if (authentifierOuExpliquer(trouve.get(), lu, requete)
                        && trouve.get().isDoitChangerMotDePasse()
                        && !permiseAvantChangement(requete)) {
                    // Mot de passe provisoire impose par le SERVEUR (audit
                    // global G4) : un mot de passe envoye par courriel ne
                    // donne acces a rien d'autre qu'a son propre changement.
                    refuserMotDePasseProvisoire(requete, reponse);
                    return;
                }
            }
        }
        chaine.doFilter(requete, reponse);
    }

    /**
     * Authentifie, ou dit pourquoi il ne le fait pas.
     *
     * <h3>Le defaut que cette methode corrige</h3>
     *
     * <p>Ces deux conditions etaient ecrites en chaine :
     *
     * <pre>
     *   .filter(Utilisateur::isActif)
     *   .filter(u -&gt; !u.estVerrouille())
     *   .ifPresent(FiltreJwt::authentifier);
     * </pre>
     *
     * <p>Elegant, et <b>muet</b>. Un {@code filter} qui ecarte une valeur ne
     * dit pas qu'il l'a ecartee : un eleveur verrouille apres cinq mauvais mots
     * de passe recevait 403 sans corps, et ni lui ni nous ne pouvions savoir si
     * c'etait son compte, son jeton, ou un droit manquant. La forme etendue est
     * plus longue de six lignes et rend chaque refus nommable.
     *
     * <p>Le chemin de refus reste identique : on laisse passer sans
     * authentifier. C'est la configuration de securite qui tranche ensuite.
     */
    private boolean authentifierOuExpliquer(Utilisateur utilisateur, FabriqueJeton.JetonLu lu,
                                            HttpServletRequest requete) {
        if (!utilisateur.isActif()) {
            JOURNAL.debug("Jeton valide mais compte {} desactive : {} reste anonyme.",
                    utilisateur.getId(), requete.getRequestURI());
            return false;
        }
        // Le verrou apres echecs de mot de passe n'est PLUS lu ici (audit
        // global G2). Il ne bloque que les nouvelles connexions par mot de
        // passe : sinon cinq mauvais essais sur l'adresse de quelqu'un
        // suffisaient a couper ses sessions en cours, sans fin.
        // Le jeton a-t-il ete emis avec le mot de passe ACTUEL ? Sinon le
        // mot de passe a change depuis : c'est le geste qui devait couper
        // l'acces. Voir FabriqueJeton.versionCorrespond.
        if (!fabriqueJeton.versionCorrespond(utilisateur, lu.versionMotDePasse())) {
            JOURNAL.debug("Jeton valide mais emis avant le dernier changement de mot de passe"
                    + " du compte {} : {} reste anonyme.",
                    utilisateur.getId(), requete.getRequestURI());
            return false;
        }
        authentifier(utilisateur);
        return true;
    }

    /**
     * Routes permises tant que le mot de passe provisoire n'a pas ete change
     * (audit global G4) : lire son profil, changer le mot de passe, se
     * deconnecter. Tout le reste repond 403 avec un message explicite.
     */
    static boolean permiseAvantChangement(HttpServletRequest requete) {
        String chemin = requete.getRequestURI();
        String contexte = requete.getContextPath();
        if (contexte != null && !contexte.isEmpty() && chemin.startsWith(contexte)) {
            chemin = chemin.substring(contexte.length());
        }
        String methode = requete.getMethod();
        return ("GET".equals(methode) && "/api/auth/moi".equals(chemin))
                || ("POST".equals(methode) && "/api/auth/mot-de-passe".equals(chemin))
                || ("POST".equals(methode) && "/api/auth/deconnexion".equals(chemin))
                || "OPTIONS".equals(methode);
    }

    /** Message rendu a qui n'a pas encore remplace son mot de passe provisoire. */
    public static final String MESSAGE_MOT_DE_PASSE_PROVISOIRE =
            "Changez d'abord votre mot de passe provisoire.";

    private void refuserMotDePasseProvisoire(HttpServletRequest requete,
                                             HttpServletResponse reponse) throws IOException {
        SecurityContextHolder.clearContext();
        reponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
        reponse.setCharacterEncoding("UTF-8");
        reponse.setContentType("application/json");
        reponse.getWriter().write(json.writeValueAsString(ErreurReponse.simple(
                HttpServletResponse.SC_FORBIDDEN, "Mot de passe provisoire",
                MESSAGE_MOT_DE_PASSE_PROVISOIRE, requete.getRequestURI())));
    }

    private static void authentifier(Utilisateur utilisateur) {
        UtilisateurConnecte connecte = new UtilisateurConnecte(utilisateur);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        connecte, null, connecte.getAuthorities()));
    }

    /**
     * Extrait le jeton de l'en-tete, ou explique pourquoi il n'y en a pas.
     *
     * <p>L'absence totale d'en-tete n'est PAS journalisee : c'est le cas de
     * toute requete vers une route ouverte, et de tout appel du navigateur aux
     * fichiers de Swagger. La journaliser noierait le reste.
     *
     * <p>En revanche un en-tete <b>present et mal forme</b> est une erreur de
     * client qu'il faut pouvoir nommer — c'est le cas de celui qui envoie son
     * jeton nu, sans {@code Bearer }.
     */
    private static String extraire(HttpServletRequest requete) {
        String entete = requete.getHeader(ENTETE);
        if (entete == null) {
            return null;
        }
        if (!entete.startsWith(PREFIXE)) {
            // Le contenu de l'en-tete n'est pas journalise : il peut contenir
            // le jeton lui-meme. Seule sa longueur sort, elle suffit a
            // distinguer « vide » de « jeton sans prefixe ».
            JOURNAL.debug("En-tete Authorization present ({} caracteres) mais ne commence pas"
                    + " par « Bearer  » : ignore. {} reste anonyme.",
                    entete.length(), requete.getRequestURI());
            return null;
        }
        String jeton = entete.substring(PREFIXE.length()).strip();
        if (jeton.isEmpty()) {
            JOURNAL.debug("En-tete Authorization reduit au prefixe « Bearer  », sans jeton :"
                    + " {} reste anonyme.", requete.getRequestURI());
            return null;
        }
        return jeton;
    }
}
