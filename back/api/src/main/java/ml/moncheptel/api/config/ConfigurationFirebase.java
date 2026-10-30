package ml.moncheptel.api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Initialisation du SDK Firebase.
 *
 * <h2>L'application demarre meme sans Firebase</h2>
 *
 * <p>Si le fichier d'identifiants est absent, le SDK n'est pas initialise et
 * l'application demarre quand meme, avec un avertissement. C'est volontaire :
 * en developpement, personne n'a de projet Firebase, et exiger un fichier de
 * cle de service pour lancer le back rendrait le projet impossible a demarrer
 * pour un nouvel arrivant.
 *
 * <p>Les notifications sont alors enregistrees en base sans etre poussees.
 * L'utilisateur les voit en ouvrant l'application : la fonctionnalite est
 * degradee, pas cassee.
 *
 * <h2>Le fichier de cle n'est PAS dans le depot</h2>
 *
 * <p>Son chemin vient de la configuration. Une cle de service Firebase donne
 * le droit d'envoyer des notifications a tous les utilisateurs de
 * l'application : commise dans un depot, elle y reste dans l'historique meme
 * apres suppression.
 */
@Configuration
public class ConfigurationFirebase {

    private static final Logger JOURNAL = LoggerFactory.getLogger(ConfigurationFirebase.class);

    private final String cheminCle;

    public ConfigurationFirebase(
            @Value("${moncheptel.firebase.fichier-cle:}") String cheminCle) {
        this.cheminCle = cheminCle;
    }

    @Bean
    public FirebaseApp firebaseApp() {
        if (cheminCle == null || cheminCle.isBlank()) {
            JOURNAL.warn("Firebase non configure (moncheptel.firebase.fichier-cle vide) : "
                       + "les notifications seront enregistrees sans etre poussees.");
            return null;
        }
        Path chemin = Path.of(cheminCle);
        if (!Files.exists(chemin)) {
            JOURNAL.warn("Fichier de cle Firebase introuvable : {} — "
                       + "les notifications seront enregistrees sans etre poussees.", chemin);
            return null;
        }
        try (InputStream flux = Files.newInputStream(chemin)) {
            if (!FirebaseApp.getApps().isEmpty()) {
                // Deja initialise : une seconde initialisation leverait une
                // IllegalStateException. Le cas se produit au rechargement a
                // chaud pendant le developpement.
                return FirebaseApp.getApps().get(0);
            }
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(flux))
                    .build();
            JOURNAL.info("Firebase initialise.");
            return FirebaseApp.initializeApp(options);
        } catch (IOException e) {
            JOURNAL.error("Lecture de la cle Firebase impossible : {}", e.getMessage());
            return null;
        }
    }
}
