package ml.moncheptel.api.infrastructure;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ml.moncheptel.api.modele.Utilisateur;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.service.ServiceCourriel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

/**
 * Envoi du mot de passe provisoire par courriel.
 *
 * <p><b>Le mot de passe n'est journalise nulle part.</b> Ni en {@code info},
 * ni en {@code debug}, ni dans une trace d'erreur : les journaux sont lus par
 * plus de monde que la boite de reception du veterinaire, et ils sont souvent
 * conserves plus longtemps.
 *
 * <p>Un echec d'envoi est remonte en exception, ce qui annule la transaction
 * et donc la creation du compte. C'est le bon comportement : un compte cree
 * dont personne ne connait le mot de passe ne sert a rien et devrait etre
 * supprime a la main.
 */
public class ServiceCourrielSmtp implements ServiceCourriel {

    private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceCourrielSmtp.class);

    /**
     * Fil d'envoi du code de reinitialisation. Deux fils : une panne SMTP qui
     * bloque l'un ne retient pas l'autre. Fils DEMON : ils ne doivent jamais
     * empecher l'arret du serveur.
     *
     * <p>Un courriel en attente au moment de l'arret est perdu. C'est
     * acceptable ici : l'utilisateur redemande un code, et le plafond horaire
     * lui en laisse deux autres.
     */
    private static final ExecutorService ENVOI_CODE = Executors.newFixedThreadPool(2, tache -> {
        Thread fil = new Thread(tache, "courriel-code");
        fil.setDaemon(true);
        return fil;
    });

    private final MailSender expediteur;
    private final String adresseExpediteur;
    private final ModeleCourriel modeles;

    /** Faux sans identifiant SMTP : voir {@link #sansIdentifiants()}. */
    private boolean configure = true;

    /** Marque ce service comme inutilisable faute d'identifiants SMTP. */
    public ServiceCourrielSmtp sansIdentifiants() {
        this.configure = false;
        return this;
    }

    @Override
    public boolean estConfigure() {
        return configure;
    }

    public ServiceCourrielSmtp(MailSender expediteur, String adresseExpediteur) {
        this(expediteur, adresseExpediteur, new ModeleCourriel(Path.of("config", "courriels")));
    }

    public ServiceCourrielSmtp(MailSender expediteur, String adresseExpediteur,
                               ModeleCourriel modeles) {
        this.expediteur = expediteur;
        this.adresseExpediteur = adresseExpediteur;
        this.modeles = modeles;
    }

    /**
     * Assemble un courriel a partir d'un modele (voir {@link ModeleCourriel}).
     *
     * <p>Une variable inconnue est journalisee par son NOM seulement : les
     * valeurs portent des mots de passe et des codes.
     */
    private SimpleMailMessage construire(String modele, String destinataire,
                                         Map<String, String> valeurs) {
        ModeleCourriel.Message rendu = modeles.rendre(modele, valeurs);
        if (!rendu.variablesInconnues().isEmpty()) {
            JOURNAL.warn("Modele de courriel '{}' : variables inconnues laissees telles "
                    + "quelles : {}", modele, rendu.variablesInconnues());
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(adresseExpediteur);
        message.setTo(destinataire);
        message.setSubject(rendu.objet());
        message.setText(rendu.corps());
        return message;
    }

    @Override
    public void envoyerMotDePasseProvisoire(Veterinaire veterinaire, String motDePasseProvisoire) {
        SimpleMailMessage message = construire("acces-veterinaire", veterinaire.getEmail(),
                Map.of("nom", veterinaire.getNom(),
                       "identifiant", veterinaire.getEmail(),
                       "motDePasse", motDePasseProvisoire));

        expediteur.send(message);
        // Le nom, pas le mot de passe.
        JOURNAL.info("Acces provisoire envoye au compte {}", veterinaire.getId());
    }

    @Override
    public void envoyerCodeReinitialisation(Utilisateur utilisateur, String code,
                                            int validiteMinutes) {
        // ASYNCHRONE, et c'est le seul envoi qui le soit.
        //
        // Cette route est PUBLIQUE et repond « 202, quoi qu'il arrive » pour ne
        // pas reveler si l'adresse a un compte. Avec un envoi synchrone, la
        // reponse prenait environ une seconde quand le compte existait (le
        // temps du SMTP) et quelques millisecondes quand il n'existait pas :
        // le contenu etait identique, la DUREE disait tout.
        //
        // Pour la meme raison, le modele (lecture d'un fichier) est rendu DANS
        // la tache et non avant : rien de ce qui depend de l'existence du
        // compte ne doit s'executer dans le fil de la requete.
        //
        // Les valeurs sont lues ICI, dans le fil appelant : l'entite appartient
        // a une transaction qui sera close quand la tache s'executera.
        String nom = utilisateur.getNom();
        Long compte = utilisateur.getId();
        String adresse = utilisateur.getEmail();
        // Le code est espace par groupes de trois : il sera recopie a la main
        // sur un telephone, souvent en plein soleil.
        Map<String, String> valeurs = Map.of(
                "nom", nom,
                "code", espacer(code),
                "validite", String.valueOf(validiteMinutes));
        ENVOI_CODE.submit(() -> {
            try {
                expediteur.send(construire("code-reinitialisation", adresse, valeurs));
                // Ni le code, ni l'adresse, ni le nom : l'identifiant du
                // compte suffit a suivre un incident (audit global G20).
                JOURNAL.info("Code de reinitialisation envoye au compte {}", compte);
            } catch (RuntimeException e) {
                // L'appelant ne voit plus l'echec : il repond 202 avant lui.
                // C'est voulu (voir ci-dessus), et c'est ce journal qui permet
                // de distinguer « adresse inconnue » de « SMTP en panne » ou de
                // « modele de courriel invalide ».
                JOURNAL.error("Envoi du code de reinitialisation impossible pour le compte {} : {}",
                        compte, e.getMessage());
            }
        });
    }

    @Override
    public void envoyerMotDePasseReinitialiseParAdmin(Utilisateur utilisateur,
                                                      String motDePasseProvisoire) {
        SimpleMailMessage message = construire("reinitialisation-admin", utilisateur.getEmail(),
                Map.of("nom", utilisateur.getNom(),
                       "identifiant", utilisateur.getEmail(),
                       "motDePasse", motDePasseProvisoire));

        expediteur.send(message);
        JOURNAL.info("Mot de passe reinitialise par l'administration pour le compte {}",
                utilisateur.getId());
    }

    @Override
    public void avertirChangementAdresse(Utilisateur utilisateur, String ancienneAdresse) {
        // A l'ANCIENNE adresse, volontairement : voir ServiceCourriel.
        SimpleMailMessage message = construire("changement-adresse", ancienneAdresse,
                Map.of("nom", utilisateur.getNom(),
                       "nouvelleAdresse", utilisateur.getEmail()));

        expediteur.send(message);
        JOURNAL.info("Changement d'adresse signale pour le compte {}", utilisateur.getId());
    }

    /**
     * « 482913 » devient « 482 913 ».
     *
     * <p>Un groupe de six chiffres se relit mal, et une erreur de recopie
     * consomme une des cinq tentatives autorisees.
     */
    private static String espacer(String code) {
        int milieu = code.length() / 2;
        return code.substring(0, milieu) + " " + code.substring(milieu);
    }
}
