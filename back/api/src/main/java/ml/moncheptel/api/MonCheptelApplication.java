package ml.moncheptel.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entree de l'API MonCheptel.
 *
 * <p>{@code @EnableScheduling} sert aux rappels sanitaires : le calendrier
 * d'un type d'elevage engendre des rappels dont l'echeance doit etre evaluee
 * chaque jour — « Rappel vaccination : LOT-014, echeance dans 3 jours ».
 *
 * <p>Le compte de secours {@code user} que Spring Boot fabrique pour les
 * applications sans {@code UserDetailsService} est neutralise dans
 * {@link ml.moncheptel.api.config.ConfigurationSecurite}, qui explique
 * pourquoi.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MonCheptelApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonCheptelApplication.class, args);
    }
}
