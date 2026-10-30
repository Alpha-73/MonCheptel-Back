package ml.moncheptel.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrat d'API expose sur {@code /swagger-ui.html}.
 *
 * <p>C'est le document de reference des equipes Flutter et Angular : il est
 * genere depuis le code, donc il ne peut pas diverger de ce que le serveur
 * fait reellement — a la difference d'une documentation ecrite a la main, qui
 * se perime des la premiere route modifiee.
 *
 * <p><b>A fermer avant toute mise en service reelle</b> : la documentation
 * d'une API est la carte de ses routes et de ses champs.
 */
@Configuration
public class ConfigurationOpenApi {

    /**
     * Nom interne du schema de securite. Il n'apparait pas a l'ecran : il
     * relie la definition du schema ci-dessous a l'exigence declaree sur les
     * operations.
     */
    private static final String SCHEMA_JETON = "jetonBearer";

    /**
     * Contrat d'API, avec son schema d'authentification.
     *
     * <h3>Pourquoi le schema de securite est indispensable ici</h3>
     *
     * <p>Sans lui, Swagger UI <b>n'affiche pas le bouton « Authorize »</b> :
     * l'interface n'a aucun moyen de savoir que cette API attend un jeton, ni
     * ou le mettre. Toutes les routes protegees repondent alors {@code 403},
     * et le contrat devient intestable depuis le navigateur — ce qui vide
     * {@code /swagger-ui.html} de son interet.
     *
     * <p>{@code Type.HTTP} avec {@code scheme("bearer")} decrit exactement ce
     * que {@code FiltreJwt} lit : un en-tete
     * {@code Authorization: Bearer <jeton>}. C'est pourquoi on colle dans
     * Swagger le jeton <b>seul</b> : le mot {@code Bearer} est ajoute par
     * l'interface. Le coller soi-meme produirait
     * {@code Bearer Bearer eyJ...} et un {@code 403}.
     *
     * <p>{@code bearerFormat("JWT")} est purement documentaire : rien ne le
     * verifie, il indique au lecteur du contrat quelle sorte de jeton
     * attendre.
     *
     * <h3>Ce que cette declaration globale simplifie — et ce qu'elle coute</h3>
     *
     * <p>{@code addSecurityItem} applique l'exigence a <b>toutes</b> les
     * operations. C'est vrai pour la quasi-totalite d'entre elles : les
     * seules routes ouvertes sont {@code /api/auth/connexion},
     * {@code /api/auth/inscription}, {@code /api/auth/rafraichir},
     * les deux routes de mot de passe oublie, {@code /api/zones} et
     * {@code /actuator/health}.
     *
     * <p><b>Le prix :</b> celles-la portent elles aussi un cadenas a
     * l'ecran, alors qu'elles n'exigent rien. C'est une imprecision du
     * document, pas du serveur — {@code ConfigurationSecurite} reste seul
     * juge de ce qui est ouvert. La corriger demanderait une annotation
     * {@code @SecurityRequirement} sur chacun des onze controleurs ; le
     * rapport entre le travail et le gain ne le justifie pas.
     */
    @Bean
    public OpenAPI apiMonCheptel() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(SCHEMA_JETON,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("""
                                        Collez ICI le champ « jetonAcces » renvoye par
                                        POST /api/auth/connexion, et LUI SEUL : le prefixe
                                        « Bearer » est ajoute automatiquement.
                                        """)))
                .addSecurityItem(new SecurityRequirement().addList(SCHEMA_JETON))
                .info(new Info()
                .title("MonCheptel API")
                .version("1.0.0")
                .description("""
                        API de la plateforme de gestion d'elevage MonCheptel.

                        Authentification : jeton JWT dans l'en-tete
                        Authorization, prefixe « Bearer ».

                        Montants : entiers de FCFA. Le franc CFA n'a pas de
                        subdivision en circulation.

                        Enumerations : renvoyees en CODE (ELEVE, VENDU), jamais
                        en libelle traduit — l'affichage appartient au client.
                        """));
    }
}
