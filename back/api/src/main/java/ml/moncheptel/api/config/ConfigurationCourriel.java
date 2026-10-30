package ml.moncheptel.api.config;

import java.util.Arrays;
import ml.moncheptel.api.infrastructure.ServiceCourrielJournal;
import ml.moncheptel.api.infrastructure.ServiceCourrielSmtp;
import ml.moncheptel.api.service.ServiceCourriel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailSender;

/**
 * Choisit comment les courriels partent : vraiment, ou dans la console.
 *
 * <h2>Pourquoi un {@code @Bean} et non deux {@code @Component} annotes</h2>
 *
 * <p>Deux classes annotees {@code @Component} implementant la meme interface
 * font echouer le demarrage sur une ambiguite, sauf a les separer par des
 * {@code @Profile} ou des {@code @ConditionalOnProperty}. Ces annotations ont
 * des regles fines — ce que fait exactement {@code havingValue = ""}, par
 * exemple — qu'il faudrait verifier plutot que supposer.
 *
 * <p>Un {@code if} en Java ne demande rien a verifier : il se lit. Et la
 * decision est au meme endroit que son explication.
 *
 * <h2>La regle</h2>
 *
 * <pre>
 *   identifiant SMTP renseigne        -> envoi reel
 *   sinon, ET profil dev actif        -> affichage console
 *   sinon                             -> envoi reel, qui echouera
 * </pre>
 *
 * <p>Le troisieme cas est voulu. Une mise en service ou l'on aurait oublie le
 * SMTP doit ECHOUER, visiblement, a chaque envoi. Basculer en silence vers la
 * console y ecrirait des codes de reinitialisation de vrais comptes, dans des
 * journaux conserves des mois.
 *
 * <p>L'echec, lui, est deja rattrape : {@code demanderReinitialisation} attrape
 * l'exception, la journalise en ERROR, et repond quand meme 202 — pour ne pas
 * reveler quels comptes existent. Le code reste enregistre, l'utilisateur peut
 * redemander.
 */
@Configuration
public class ConfigurationCourriel {

    private static final Logger JOURNAL = LoggerFactory.getLogger(ConfigurationCourriel.class);

    @Bean
    public ServiceCourriel serviceCourriel(
            MailSender expediteur,
            Environment environnement,
            @Value("${spring.mail.username:}") String identifiantSmtp,
            @Value("${moncheptel.courriel.expediteur:no-reply@moncheptel.ml}")
            String adresseExpediteur) {

        boolean smtpConfigure = identifiantSmtp != null && !identifiantSmtp.isBlank();
        boolean enDeveloppement =
                Arrays.asList(environnement.getActiveProfiles()).contains("dev");

        if (!smtpConfigure && enDeveloppement) {
            JOURNAL.warn("Aucun identifiant SMTP et profil dev actif : les courriels "
                    + "seront AFFICHES DANS CETTE CONSOLE au lieu d'etre envoyes. "
                    + "Renseignez MONCHEPTEL_SMTP_USER et MONCHEPTEL_SMTP_PASSWORD "
                    + "pour un envoi reel.");
            return new ServiceCourrielJournal();
        }
        if (!smtpConfigure) {
            // Ni SMTP, ni profil dev : on garde l'implementation reelle, qui
            // echouera. Un echec bruyant vaut mieux qu'un secret dans un journal.
            JOURNAL.error("Aucun identifiant SMTP configure hors profil dev : "
                    + "tout envoi de courriel echouera. Renseignez "
                    + "MONCHEPTEL_SMTP_USER et MONCHEPTEL_SMTP_PASSWORD.");
        } else {
            JOURNAL.info("Envoi de courriel par SMTP, compte {}", identifiantSmtp);
        }
        ServiceCourrielSmtp smtp = new ServiceCourrielSmtp(expediteur, adresseExpediteur);
        return smtpConfigure ? smtp : smtp.sansIdentifiants();
    }
}
