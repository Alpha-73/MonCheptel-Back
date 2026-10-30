package ml.moncheptel.api.securite;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import ml.moncheptel.api.dto.reponse.ErreurReponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limitation de debit des routes publiques d'authentification (audit global
 * G2), en memoire, sans dependance.
 *
 * <h2>Ce qui est limite</h2>
 *
 * <p>Les POST vers la connexion, l'inscription, le rafraichissement et les
 * deux routes de « mot de passe oublie ». Chaque regle compte par ADRESSE IP,
 * sur une fenetre fixe ; « mot de passe oublie » a en plus un plafond GLOBAL,
 * pour que parcourir des milliers de comptes depuis des milliers d'adresses
 * n'epuise pas le quota du serveur de courriel. Le plafond par COMPTE existe
 * deja plus loin : verrou progressif apres cinq mauvais mots de passe, trois
 * codes par heure.
 *
 * <h2>Des seuils larges, a dessein</h2>
 *
 * <p>Au Mali, beaucoup de telephones sortent par la meme adresse de
 * l'operateur (NAT). Un seuil serre bloquerait un village entier pour un
 * seul abus. Les valeurs ci-dessous ralentissent une machine, pas un
 * eleveur qui se trompe trois fois.
 *
 * <h2>L'adresse du client</h2>
 *
 * <p>{@code getRemoteAddr()}, jamais l'en-tete {@code X-Forwarded-For} lu a
 * la main : n'importe quel client peut l'ecrire, et la limite par IP
 * deviendrait une limite par valeur inventee. Derriere le proxy HTTPS,
 * {@code server.forward-headers-strategy=native} fait reecrire l'adresse par
 * Tomcat, qui ne fait confiance qu'a un proxy du reseau interne.
 *
 * <h2>Limite assumee</h2>
 *
 * <p>Les compteurs vivent dans la memoire d'UNE instance. Avec plusieurs
 * instances, chacune compte de son cote : la limite effective est multipliee
 * d'autant. Le proxy (exemple nginx {@code limit_req}) reste alors la
 * premiere barriere.
 */
public class FiltreLimiteDebit extends OncePerRequestFilter {

    private static final Logger JOURNAL = LoggerFactory.getLogger(FiltreLimiteDebit.class);

    /** Au-dela, on purge les fenetres expirees avant d'en creer d'autres. */
    private static final int TAILLE_PURGE = 10_000;

    /**
     * Une regle : au plus {@code capacite} requetes par {@code fenetre}, par
     * adresse IP ou pour tous ({@code globale}).
     */
    public record Regle(String chemin, int capacite, Duration fenetre, boolean globale) {
    }

    /** Les regles par defaut. Voir le Javadoc de la classe pour les seuils. */
    public static List<Regle> reglesParDefaut() {
        return List.of(
                new Regle("/api/auth/connexion", 10, Duration.ofMinutes(1), false),
                new Regle("/api/auth/rafraichir", 30, Duration.ofMinutes(1), false),
                new Regle("/api/auth/inscription", 10, Duration.ofHours(1), false),
                new Regle("/api/auth/mot-de-passe/oublie", 5, Duration.ofMinutes(15), false),
                new Regle("/api/auth/mot-de-passe/oublie", 200, Duration.ofHours(1), true),
                new Regle("/api/auth/mot-de-passe/reinitialiser", 10, Duration.ofMinutes(15), false));
    }

    /** Fenetre fixe : nombre de requetes vues, et instant de fin en ms. */
    private record Fenetre(long fin, int vues) {
    }

    private final List<Regle> regles;
    private final ObjectMapper json;
    private final Clock horloge;
    private final ConcurrentHashMap<String, Fenetre> fenetres = new ConcurrentHashMap<>();
    private final AtomicLong appels = new AtomicLong();

    public FiltreLimiteDebit(List<Regle> regles, ObjectMapper json, Clock horloge) {
        this.regles = List.copyOf(regles);
        this.json = json;
        this.horloge = horloge;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest requete) {
        return !"POST".equals(requete.getMethod());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest requete,
                                    @NonNull HttpServletResponse reponse,
                                    @NonNull FilterChain chaine)
            throws ServletException, IOException {
        String chemin = chemin(requete);
        long maintenant = horloge.millis();
        purgerDeTempsEnTemps(maintenant);

        for (Regle regle : regles) {
            if (!regle.chemin().equals(chemin)) {
                continue;
            }
            String cle = regle.chemin() + '|' + regle.fenetre().toSeconds() + '|'
                    + (regle.globale() ? "*" : requete.getRemoteAddr());
            Fenetre f = fenetres.compute(cle, (k, v) -> v == null || v.fin() <= maintenant
                    ? new Fenetre(maintenant + regle.fenetre().toMillis(), 1)
                    : new Fenetre(v.fin(), v.vues() + 1));
            if (f.vues() > regle.capacite()) {
                long attente = Math.max(1, (f.fin() - maintenant + 999) / 1000);
                JOURNAL.warn("Limite de debit atteinte sur {} ({}) : {} requetes, attente {} s",
                        chemin, regle.globale() ? "toutes adresses" : requete.getRemoteAddr(),
                        f.vues(), attente);
                refuser(requete, reponse, attente);
                return;
            }
        }
        chaine.doFilter(requete, reponse);
    }

    /** Vide toutes les fenetres. Sert aux tests. */
    public void reinitialiser() {
        fenetres.clear();
    }

    private void purgerDeTempsEnTemps(long maintenant) {
        if (appels.incrementAndGet() % 500 == 0 || fenetres.size() > TAILLE_PURGE) {
            fenetres.values().removeIf(f -> f.fin() <= maintenant);
        }
    }

    private void refuser(HttpServletRequest requete, HttpServletResponse reponse, long attente)
            throws IOException {
        reponse.setStatus(429);
        reponse.setHeader("Retry-After", String.valueOf(attente));
        reponse.setCharacterEncoding("UTF-8");
        reponse.setContentType("application/json");
        String minutes = attente < 60 ? "une minute"
                : ((attente + 59) / 60) + " minutes";
        reponse.getWriter().write(json.writeValueAsString(ErreurReponse.simple(
                429, "Trop de requetes",
                "Trop d'essais depuis ce réseau. Réessayez dans " + minutes + ".",
                requete.getRequestURI())));
    }

    private static String chemin(HttpServletRequest requete) {
        String chemin = requete.getRequestURI();
        String contexte = requete.getContextPath();
        if (contexte != null && !contexte.isEmpty() && chemin.startsWith(contexte)) {
            chemin = chemin.substring(contexte.length());
        }
        // « /api/auth/connexion/ » ne doit pas echapper a la regle.
        while (chemin.length() > 1 && chemin.endsWith("/")) {
            chemin = chemin.substring(0, chemin.length() - 1);
        }
        return chemin;
    }
}
