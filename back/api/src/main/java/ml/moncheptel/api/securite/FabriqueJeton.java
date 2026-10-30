package ml.moncheptel.api.securite;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import ml.moncheptel.api.config.ProprietesSecurite;
import ml.moncheptel.api.modele.Utilisateur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fabrication et lecture des jetons JWT.
 *
 * <h2>Ce que le jeton contient, et ce qu'il ne contient pas</h2>
 *
 * <p>Il porte l'identifiant et le role. Rien d'autre : ni nom, ni e-mail,
 * ni zone. Un JWT n'est PAS chiffre — il est signe. Son contenu se lit sans
 * la cle, en collant le jeton dans n'importe quel decodeur en ligne. Y mettre
 * l'adresse e-mail d'un eleveur reviendrait a la publier.
 *
 * <h2>Pourquoi le jeton de rafraichissement est stocke en empreinte</h2>
 *
 * <p>La table {@code session} ne garde qu'un SHA-256 du jeton, jamais le jeton
 * lui-meme. Une base volee ne donne alors aucune session utilisable : c'est le
 * meme raisonnement que pour les mots de passe. Verifier revient a re-hacher
 * ce que le client presente et comparer.
 */
@Component
public class FabriqueJeton {

    private static final Logger JOURNAL = LoggerFactory.getLogger(FabriqueJeton.class);

    private static final String CLE_ROLE = "role";
    private static final String CLE_TYPE = "typ";
    private static final String CLE_VERSION_MDP = "pv";
    private static final String TYPE_ACCES = "acces";
    private static final String TYPE_RAFRAICHISSEMENT = "rafraichissement";

    private final SecretKey cle;
    private final int dureeRafraichissementJours;

    public FabriqueJeton(ProprietesSecurite proprietes) {
        this.cle = Keys.hmacShaKeyFor(
                proprietes.secretJwt().getBytes(StandardCharsets.UTF_8));
        this.dureeRafraichissementJours = proprietes.dureeRafraichissementJours();
    }

    public String creerJetonAcces(Utilisateur utilisateur, Duration duree) {
        return construire(utilisateur, duree, TYPE_ACCES);
    }

    public String creerJetonRafraichissement(Utilisateur utilisateur) {
        return construire(utilisateur, Duration.ofDays(dureeRafraichissementJours),
                          TYPE_RAFRAICHISSEMENT);
    }

    private String construire(Utilisateur utilisateur, Duration duree, String type) {
        Instant maintenant = Instant.now();
        return Jwts.builder()
                // Identifiant unique : deux jetons emis pour le meme compte dans
                // la meme seconde auraient sinon exactement les memes champs,
                // donc la meme chaine, donc la meme empreinte en base. La table
                // session impose l'unicite de l'empreinte : un rafraichissement
                // fait dans la seconde qui suit la connexion echouerait.
                .claim("jti", UUID.randomUUID().toString())
                .subject(String.valueOf(utilisateur.getId()))
                .claim(CLE_ROLE, utilisateur.getRole().name())
                .claim(CLE_TYPE, type)
                .claim(CLE_VERSION_MDP, versionMotDePasse(utilisateur))
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(duree)))
                .signWith(cle)
                .compact();
    }

    /**
     * Ce que le filtre doit savoir d'un jeton d'acces : QUI il designe, et avec
     * QUEL mot de passe il a ete emis.
     */
    public record JetonLu(Long id, String versionMotDePasse) {
    }

    /**
     * Lit un jeton d'acces.
     *
     * <p>Rend {@code null} sur tout probleme — signature invalide, jeton
     * expire, format inattendu, ou jeton de rafraichissement presente a la
     * place d'un jeton d'acces. Le filtre traite ce {@code null} comme « non
     * authentifie » : une exception a cet endroit remonterait une trace dans
     * la reponse HTTP et renseignerait l'attaquant sur ce qui a echoue.
     */
    public JetonLu lireJetonAcces(String jeton) {
        Claims claims = lire(jeton, TYPE_ACCES);
        if (claims == null) {
            return null;
        }
        return new JetonLu(
                Long.valueOf(claims.getSubject()),
                claims.get(CLE_VERSION_MDP, String.class));
    }

    /**
     * Lit un jeton de rafraichissement. Meme contrat que
     * {@link #lireJetonAcces} : {@code null} sur tout probleme, y compris un
     * jeton d'acces presente a la place.
     */
    public JetonLu lireJetonRafraichissement(String jeton) {
        Claims claims = lire(jeton, TYPE_RAFRAICHISSEMENT);
        if (claims == null) {
            return null;
        }
        return new JetonLu(
                Long.valueOf(claims.getSubject()),
                claims.get(CLE_VERSION_MDP, String.class));
    }

    /**
     * Ce jeton a-t-il ete emis avec le mot de passe ACTUEL du compte ?
     *
     * <h3>Le defaut que cette methode corrige</h3>
     *
     * <p>Changer son mot de passe appelait
     * {@code SessionRepository.revoquerToutesLesSessions}, qui positionne
     * {@code Session.revoquee}. <b>Ce champ n'etait lu nulle part.</b> Le jeton
     * d'acces, lui, est autonome : il restait valable jusqu'a son expiration,
     * soit une heure, apres le geste meme qui devait couper l'acces. Quelqu'un
     * qui avait vole un telephone et son jeton gardait la main pendant que la
     * victime changeait son mot de passe.
     *
     * <h3>Pourquoi l'empreinte du mot de passe, et pas un compteur</h3>
     *
     * <p>Un compteur de version sur le compte aurait demande une colonne, une
     * migration, et surtout <b>de penser a l'incrementer</b> dans chacun des
     * trois endroits qui changent un mot de passe (changement, reinitialisation
     * par courriel, reinitialisation par l'administration). Le jour ou un
     * quatrieme apparait et l'oublie, la faille revient sans qu'aucun test ne
     * le dise.
     *
     * <p>Ici, rien a penser : toute modification du mot de passe change son
     * empreinte BCrypt — le sel est tire a chaque encodage — donc invalide les
     * jetons existants, quel que soit le chemin emprunte.
     *
     * <h3>Ce que le jeton contient, et pourquoi c'est sans danger</h3>
     *
     * <p>Seulement les seize premiers caracteres d'un SHA-256 de l'empreinte
     * BCrypt : un condense d'un condense. Il ne permet ni de retrouver le mot
     * de passe, ni l'empreinte, ni de fabriquer un jeton — la signature reste
     * l'affaire de la cle.
     *
     * <h3>Ce que cela ne couvre pas</h3>
     *
     * <p>Une deconnexion revoque le jeton de rafraichissement, pas le jeton
     * d'acces en cours : celui-ci vit jusqu'a son expiration. C'est le
     * compromis habituel d'un jeton autonome ; seul un changement de mot de
     * passe coupe tout.
     *
     * <p>Un jeton sans ce champ — emis avant ce correctif — est refuse. C'est
     * voulu : accepter l'absence reviendrait a laisser un contournement.
     */
    public boolean versionCorrespond(Utilisateur utilisateur, String presentee) {
        if (presentee == null) {
            return false;
        }
        return MessageDigest.isEqual(
                versionMotDePasse(utilisateur).getBytes(StandardCharsets.UTF_8),
                presentee.getBytes(StandardCharsets.UTF_8));
    }

    private String versionMotDePasse(Utilisateur utilisateur) {
        return empreinte(utilisateur.getMotDePasse()).substring(0, 16);
    }

    /**
     * Lecture d'un jeton, avec la raison du refus au journal.
     *
     * <h3>Le defaut que cette methode corrige</h3>
     *
     * <p>Les deux lectures etaient ecrites separement et se terminaient par
     * {@code catch (JwtException | IllegalArgumentException e) { return null; }}.
     * <b>La raison du refus etait detruite a cet endroit precis</b> : la
     * variable {@code e} portait « signature invalide », « jeton expire » ou
     * « format illisible », et elle disparaissait sans laisser de trace.
     *
     * <p>En aval, {@code FiltreJwt} ne voyait qu'un {@code null}, laissait la
     * requete anonyme, et Spring Security repondait <b>403 avec un corps
     * vide</b>. Rien dans le journal, rien dans la reponse. Un refus
     * indebogable : c'est arrive pendant les tests du 29 septembre et il a
     * fallu contourner par des appels PowerShell pour savoir si le jeton
     * partait ou non.
     *
     * <p>Les deux methodes sont desormais une seule : le meme corps etait
     * ecrit deux fois, et une correction faite d'un cote aurait pu manquer
     * l'autre.
     *
     * <h3>Pourquoi DEBUG, et pourquoi jamais le jeton</h3>
     *
     * <p>DEBUG et non WARN : un jeton expire est le cas NORMAL d'une
     * application mobile dont l'utilisateur revient le lendemain. En WARN, le
     * journal de production se remplirait d'alertes pour un evenement
     * quotidien, et les vraies alertes s'y noieraient.
     *
     * <p><b>Le jeton n'est jamais journalise</b>, ni meme un fragment. Un
     * journal se copie, s'exporte, se partage : un jeton qui y figure est un
     * jeton utilisable par quiconque lit le fichier, jusqu'a son expiration.
     * Seules la classe de l'exception et son message sortent — ils disent la
     * cause sans donner le moyen.
     *
     * @return les revendications du jeton, ou {@code null} s'il est invalide,
     *         expire, ou n'est pas du type attendu
     */
    private Claims lire(String jeton, String typeAttendu) {
        try {
            Claims claims = Jwts.parser().verifyWith(cle).build()
                    .parseSignedClaims(jeton).getPayload();

            String type = claims.get(CLE_TYPE, String.class);
            if (!typeAttendu.equals(type)) {
                // Cas frequent et deroutant : le client envoie son jeton de
                // rafraichissement dans l'en-tete Authorization. La signature
                // est valide, le jeton n'est pas expire, et il est refuse.
                // Sans cette ligne, le refus est incomprehensible.
                JOURNAL.debug("Jeton de type « {} » presente ou « {} » etait attendu : refuse",
                        type, typeAttendu);
                return null;
            }
            return claims;

        } catch (JwtException | IllegalArgumentException e) {
            JOURNAL.debug("Jeton {} refuse — {} : {}",
                    typeAttendu, e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /** SHA-256 du jeton, en base64 sans remplissage : ce qui est stocke en base. */
    public String empreinte(String jeton) {
        try {
            byte[] condensat = MessageDigest.getInstance("SHA-256")
                    .digest(jeton.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(condensat);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est exige par la specification de la plateforme Java :
            // cette branche ne peut pas etre atteinte sur une JVM conforme.
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
