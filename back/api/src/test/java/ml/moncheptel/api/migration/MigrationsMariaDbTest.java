package ml.moncheptel.api.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import ml.moncheptel.api.MonCheptelApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Les migrations Flyway sur une VRAIE base MariaDB/MySQL (audit global G12).
 *
 * <p>Les autres tests tournent sur H2, schema cree par Hibernate : ils ne
 * voient ni le SQL des migrations ni l'ecart entre ce SQL et les entites.
 * Ce test cree une base JETABLE, y joue V1 a la derniere, demarre le contexte
 * avec {@code ddl-auto=validate} (Hibernate refuse toute colonne manquante ou
 * de mauvais type), puis supprime la base. La base de developpement n'est
 * jamais touchee.
 *
 * <p>Sans dependance nouvelle : {@code flyway-core}, {@code flyway-mysql} et
 * le pilote MySQL sont deja dans le projet. Sans serveur joignable, le test
 * est IGNORE (pas en echec) : la CI sans base reste verte. Connexion par
 * defaut {@code root} sans mot de passe sur 127.0.0.1:3306, modifiable par
 * MONCHEPTEL_TEST_DB_URL, MONCHEPTEL_TEST_DB_USER, MONCHEPTEL_TEST_DB_PASSWORD ;
 * MONCHEPTEL_TEST_DB=non le desactive.
 */
class MigrationsMariaDbTest {

    private static final String SERVEUR = env("MONCHEPTEL_TEST_DB_URL",
            "jdbc:mysql://127.0.0.1:3306/");
    private static final String UTILISATEUR = env("MONCHEPTEL_TEST_DB_USER", "root");
    private static final String MOT_DE_PASSE = env("MONCHEPTEL_TEST_DB_PASSWORD", "");
    private static final String BASE = "moncheptel_migration_test_" + ProcessHandle.current().pid();
    private static final String OPTIONS =
            "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false";

    private static boolean creee;

    private static String env(String nom, String defaut) {
        String v = System.getenv(nom);
        return v == null || v.isBlank() ? defaut : v;
    }

    @BeforeAll
    static void creerBase() {
        Assumptions.assumeFalse("non".equalsIgnoreCase(System.getenv("MONCHEPTEL_TEST_DB")),
                "MONCHEPTEL_TEST_DB=non");
        try (Connection c = DriverManager.getConnection(SERVEUR + OPTIONS, UTILISATEUR, MOT_DE_PASSE);
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE `" + BASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            creee = true;
        } catch (SQLException e) {
            Assumptions.abort("Pas de serveur MariaDB/MySQL joignable (" + e.getMessage()
                    + ") : test des migrations ignore.");
        }
    }

    @AfterAll
    static void supprimerBase() throws SQLException {
        if (!creee) {
            return;
        }
        try (Connection c = DriverManager.getConnection(SERVEUR + OPTIONS, UTILISATEUR, MOT_DE_PASSE);
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS `" + BASE + "`");
        }
    }

    @Test
    @DisplayName("V1 a la derniere passent sur MariaDB et collent aux entites (ddl-auto=validate)")
    void migrationsEtSchema() throws SQLException {
        String url = SERVEUR + BASE + OPTIONS;
        Flyway flyway = Flyway.configure()
                .dataSource(url, UTILISATEUR, MOT_DE_PASSE)
                .locations("classpath:db/migration")
                .load();
        var resultat = flyway.migrate();
        assertThat(resultat.success).isTrue();
        assertThat(resultat.migrationsExecuted).isGreaterThanOrEqualTo(12);
        flyway.validate();

        try (ConfigurableApplicationContext contexte = new SpringApplicationBuilder(MonCheptelApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .properties(
                        "spring.datasource.url=" + url,
                        "spring.datasource.username=" + UTILISATEUR,
                        "spring.datasource.password=" + MOT_DE_PASSE,
                        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.flyway.enabled=false",
                        "spring.task.scheduling.enabled=false")
                .run()) {
            assertThat(contexte.isActive()).isTrue();
        }

        // Les accents de V12 sont bien en base, en UTF-8.
        try (Connection c = DriverManager.getConnection(url, UTILISATEUR, MOT_DE_PASSE);
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*) FROM zone WHERE nom = 'Ségou'")) {
            r.next();
            assertThat(r.getInt(1)).isGreaterThanOrEqualTo(1);
        }
    }
}
