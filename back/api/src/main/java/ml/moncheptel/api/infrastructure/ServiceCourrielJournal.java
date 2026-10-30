package ml.moncheptel.api.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import ml.moncheptel.api.modele.Utilisateur;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.service.ServiceCourriel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remplacant de developpement : le courriel est ECRIT DANS LA CONSOLE au lieu
 * d'etre envoye.
 *
 * <h2>A quoi il sert</h2>
 *
 * <p>Le parcours « mot de passe oublie » etait intestable sans serveur SMTP :
 * le code part par courriel, et il est stocke hache, donc illisible en base.
 * Le scenario H09 du plan de test etait bloque sur l'obtention d'un compte
 * d'envoi.
 *
 * <p>Avec cette implementation, le code s'affiche dans la console du serveur.
 * Le parcours complet se joue sous Swagger en quelques minutes, sans compte,
 * sans reseau, sans attendre.
 *
 * <h2>QUAND il s'active, et pourquoi deux conditions</h2>
 *
 * <p>Il faut que les DEUX soient vraies :
 * <ul>
 *   <li>aucun identifiant SMTP n'est configure ;</li>
 *   <li>le profil {@code dev} est actif.</li>
 * </ul>
 *
 * <p>La premiere seule ne suffirait pas. Une mise en service ou l'on aurait
 * oublie de renseigner le SMTP basculerait alors ici en silence, et <b>les
 * codes de reinitialisation de vrais comptes s'ecriraient dans les journaux
 * de production</b> — lisibles par quiconque y a acces, conserves des mois.
 *
 * <p>Avec les deux conditions, une production sans SMTP garde l'implementation
 * reelle, qui echoue bruyamment a l'envoi. Un echec visible vaut mieux qu'un
 * secret recopie dans un fichier de journal.
 *
 * <h2>Ce que cela contredit, assume</h2>
 *
 * <p>{@code ServiceCourriel} dit que le code « ne doit apparaitre dans aucun
 * journal ». C'est la regle, et elle tient pour l'implementation reelle. Ceci
 * en est l'exception explicite, bornee au poste de developpement, et ecrite
 * ici pour que personne ne la decouvre par surprise. Une regle de securite
 * qu'on contourne en silence est pire qu'une exception documentee.
 *
 * <p>Le bandeau imprime a chaque message rappelle cette situation a qui lit la
 * console sans avoir lu ce fichier.
 */
public class ServiceCourrielJournal implements ServiceCourriel {

    private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceCourrielJournal.class);

    private static final String BARRE =
            "═══════════════════════════════════════════════════════════════";

    @Override
    public void envoyerMotDePasseProvisoire(Veterinaire veterinaire, String motDePasseProvisoire) {
        afficher("MOT DE PASSE PROVISOIRE", veterinaire.getEmail(),
                "Compte veterinaire cree pour " + veterinaire.getNom(),
                "Mot de passe : " + motDePasseProvisoire);
    }

    @Override
    public void envoyerCodeReinitialisation(Utilisateur utilisateur, String code,
                                            int validiteMinutes) {
        afficher("CODE DE REINITIALISATION", utilisateur.getEmail(),
                "Pour " + utilisateur.getNom(),
                "Code : " + code + "   (valable " + validiteMinutes + " minutes)");
    }

    @Override
    public void envoyerMotDePasseReinitialiseParAdmin(Utilisateur utilisateur,
                                                      String motDePasseProvisoire) {
        afficher("MOT DE PASSE REINITIALISE PAR L'ADMINISTRATION",
                utilisateur.getEmail(),
                "Pour " + utilisateur.getNom(),
                "Mot de passe : " + motDePasseProvisoire);
    }

    @Override
    public void avertirChangementAdresse(Utilisateur utilisateur, String ancienneAdresse) {
        afficher("CHANGEMENT D'ADRESSE DE CONNEXION", ancienneAdresse,
                "Compte : " + utilisateur.getNom(),
                "Nouvelle adresse : " + utilisateur.getEmail());
    }

    /**
     * Un seul format pour les quatre messages, en WARN.
     *
     * <p>WARN et non INFO : le niveau doit sauter aux yeux dans une console qui
     * defile, et rappeler que cette sortie n'a rien de normal. En INFO, le code
     * se perdrait entre deux lignes d'Hibernate.
     */
    private static void afficher(String objet, String destinataire, String... lignes) {
        StringBuilder sb = new StringBuilder("\n").append(BARRE)
                .append("\n  COURRIEL NON ENVOYE — affiche ici faute de SMTP configure")
                .append("\n  ").append(objet)
                .append("\n  Destinataire : ").append(destinataire);
        for (String l : lignes) {
            sb.append("\n  ").append(l);
        }
        sb.append("\n").append(BARRE);
        JOURNAL.warn(sb.toString());
        deposer(objet, destinataire, lignes);
    }

    /**
     * Fichier d'appoint : {@code target/courriels-dev.log}, une ligne par envoi.
     *
     * <h3>Pourquoi ce fichier existe</h3>
     *
     * <p>La console du serveur est le seul endroit ou le code a six chiffres
     * apparait, et elle est illisible des qu'on y travaille vraiment : le
     * serveur tourne des heures, les cadres s'empilent, et apres trois
     * executions du plan de test il y en a une douzaine. Un humain doit alors
     * deviner lequel est le bon.
     *
     * <p>Mesure du 4 octobre : les scenarios H03, H04, H05 et H12b ont echoue
     * DEUX FOIS de suite sur un code refuse, sur deux comptes differents. La
     * cause la plus probable n'etait pas le serveur mais cette ambiguite — et
     * je n'avais aucun moyen de le prouver.
     *
     * <p>Une ligne par envoi, horodatee, avec le destinataire : un script de
     * test retrouve LA ligne qui le concerne sans qu'on lui lise rien. Le
     * format est volontairement plat et stable :
     *
     * <pre>
     * 2026-10-04T22:05:11Z | CODE DE REINITIALISATION | reinit-x@example.ml | Code : 482913   (valable 15 minutes)
     * </pre>
     *
     * <h3>Pourquoi c'est acceptable, et seulement ici</h3>
     *
     * <p>Cette classe n'est active que lorsque AUCUN identifiant SMTP n'est
     * configure ET que le profil {@code dev} est actif — c'est
     * {@code ConfigurationCourriel} qui en decide. En production, c'est
     * {@code ServiceCourrielSmtp} qui est en place, et il n'ecrit aucun
     * fichier. Le code en clair ne touche donc jamais le disque d'un serveur
     * reel.
     *
     * <p>Le fichier va dans {@code target/}, que Maven efface a chaque
     * {@code clean} et qu'aucun depot ne suit.
     *
     * <h3>Pourquoi l'echec d'ecriture est tu</h3>
     *
     * <p>Un disque plein ou un dossier en lecture seule ne doit pas faire
     * echouer une reinitialisation de mot de passe. Ce fichier est un
     * confort de developpement, pas une fonctionnalite : la console reste la
     * source de verite, et elle a deja recu le message juste au-dessus.
     */
    private static void deposer(String objet, String destinataire, String... lignes) {
        try {
            Path dossier = Path.of("target");
            Files.createDirectories(dossier);
            StringBuilder ligne = new StringBuilder()
                    .append(Instant.now()).append(" | ")
                    .append(objet).append(" | ")
                    .append(destinataire);
            for (String l : lignes) {
                ligne.append(" | ").append(l);
            }
            ligne.append(System.lineSeparator());
            Files.writeString(dossier.resolve("courriels-dev.log"), ligne.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            JOURNAL.debug("Fichier d'appoint des courriels non ecrit : {}", e.getMessage());
        }
    }
}