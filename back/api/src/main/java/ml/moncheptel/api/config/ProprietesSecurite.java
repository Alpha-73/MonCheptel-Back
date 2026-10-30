package ml.moncheptel.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reglages de securite, lus depuis {@code application.yml} sous
 * {@code moncheptel.securite}.
 *
 * <p>Un record, donc immuable : ces valeurs sont fixees au demarrage et rien
 * ne doit pouvoir les changer a chaud — un seuil de verrouillage modifiable en
 * cours d'execution serait une porte derobee.
 *
 * <p>Les valeurs par defaut sont celles de la specification. Elles figurent
 * ici ET dans le fichier de configuration : ici pour qu'un fichier incomplet
 * ne fasse pas demarrer l'application avec un seuil a zero, la-bas pour que
 * l'exploitant les voie sans lire le code.
 */
@ConfigurationProperties(prefix = "moncheptel.securite")
public record ProprietesSecurite(

        /** Secret de signature des jetons. Jamais de valeur par defaut : voir plus bas. */
        String secretJwt,

        @DefaultValue("60") int dureeJetonMinutes,

        @DefaultValue("30") int dureeRafraichissementJours,

        @DefaultValue("5") int tentativesAvantVerrouillage,

        @DefaultValue("15") int dureeVerrouillageMinutes) {

    /**
     * Longueur minimale du secret, en octets. HMAC-SHA256 exige une cle d'au
     * moins 256 bits ; jjwt refuse de signer en deca, et c'est une bonne chose.
     */
    private static final int LONGUEUR_MINIMALE_SECRET = 32;

    public ProprietesSecurite {
        // Volontairement aucune valeur par defaut pour le secret. Un secret
        // par defaut finit toujours en production : il serait dans ce depot,
        // donc public, et tous les jetons de toutes les installations
        // seraient forgeables. Mieux vaut que l'application refuse de
        // demarrer.
        if (secretJwt == null || secretJwt.isBlank()) {
            throw new IllegalStateException(
                    "moncheptel.securite.secret-jwt est obligatoire. "
                    + "Definissez la variable d'environnement MONCHEPTEL_JWT_SECRET, "
                    + "ou lancez avec le profil dev : "
                    + "mvn spring-boot:run \"-Dspring-boot.run.profiles=dev\"");
        }
        // Une chaine de remplacement non resolue arrive ici TELLE QUELLE :
        // Spring n'echoue pas sur un ${...} introuvable, il laisse le texte.
        // Sans ce test, le message parlerait de longueur de cle — et enverrait
        // chercher au mauvais endroit, alors que la vraie cause est une
        // variable d'environnement absente ou un profil non actif.
        if (secretJwt.startsWith("${") && secretJwt.endsWith("}")) {
            String variable = secretJwt.substring(2, secretJwt.length() - 1);
            throw new IllegalStateException(
                    "La variable d'environnement " + variable + " n'est pas definie : "
                    + "moncheptel.securite.secret-jwt vaut encore le texte litteral \""
                    + secretJwt + "\".\n"
                    + "  - En developpement, lancez avec le profil dev, qui porte son "
                    + "propre secret :\n"
                    + "        mvn spring-boot:run \"-Dspring-boot.run.profiles=dev\"\n"
                    + "    (les guillemets sont obligatoires sous PowerShell)\n"
                    + "  - Sinon, definissez " + variable + " avec au moins "
                    + LONGUEUR_MINIMALE_SECRET + " caracteres.");
        }
        if (secretJwt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                < LONGUEUR_MINIMALE_SECRET) {
            throw new IllegalStateException(
                    "moncheptel.securite.secret-jwt doit faire au moins "
                    + LONGUEUR_MINIMALE_SECRET + " octets (256 bits) pour HMAC-SHA256 ; "
                    + "il en fait "
                    + secretJwt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    + ".");
        }
        if (tentativesAvantVerrouillage < 1) {
            throw new IllegalStateException(
                    "tentatives-avant-verrouillage doit valoir au moins 1");
        }
    }
}
