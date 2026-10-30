package ml.moncheptel.api.config;

import ml.moncheptel.api.modele.Administrateur;
import ml.moncheptel.api.modele.Utilisateur;
import ml.moncheptel.api.repository.AdministrateurRepository;
import ml.moncheptel.api.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cree le PREMIER administrateur au demarrage (audit global G1).
 *
 * <h2>Pourquoi</h2>
 *
 * <p>Le seul compte administrateur venait du jeu de demonstration V4, avec un
 * mot de passe publie dans le depot. Une base de production sans V4 n'avait
 * donc aucun administrateur, et aucune route ne permettait d'en creer un.
 *
 * <h2>La regle</h2>
 *
 * <ul>
 *   <li>s'il existe deja un administrateur, quel qu'il soit : rien ne change.
 *       En particulier le compte de la base de demonstration n'est pas
 *       touche ;</li>
 *   <li>sinon, si {@code MONCHEPTEL_ADMIN_EMAIL} et
 *       {@code MONCHEPTEL_ADMIN_MOT_DE_PASSE_INITIAL} sont definies, le compte
 *       est cree avec ce mot de passe PROVISOIRE : {@code doitChangerMotDePasse}
 *       est vrai, et le serveur refuse toute autre route tant qu'il n'a pas ete
 *       change (FiltreJwt) ;</li>
 *   <li>sinon, un avertissement dit quoi faire, et l'API demarre quand meme.</li>
 * </ul>
 *
 * <p>Le mot de passe n'est jamais journalise. Une fois le compte cree, les
 * variables peuvent (et doivent) etre retirees de l'environnement.
 */
@Component
public class InitialisationAdministrateur implements ApplicationRunner {

    private static final Logger JOURNAL =
            LoggerFactory.getLogger(InitialisationAdministrateur.class);

    /** Longueur minimale du mot de passe initial. */
    static final int LONGUEUR_MINIMALE = 12;

    private final AdministrateurRepository administrateurRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder encodeur;
    private final String email;
    private final String motDePasse;
    private final String nom;

    public InitialisationAdministrateur(
            AdministrateurRepository administrateurRepository,
            UtilisateurRepository utilisateurRepository,
            PasswordEncoder encodeur,
            @Value("${moncheptel.administrateur-initial.email:}") String email,
            @Value("${moncheptel.administrateur-initial.mot-de-passe:}") String motDePasse,
            @Value("${moncheptel.administrateur-initial.nom:Administrateur}") String nom) {
        this.administrateurRepository = administrateurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.encodeur = encodeur;
        this.email = email;
        this.motDePasse = motDePasse;
        this.nom = nom;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        initialiser();
    }

    /**
     * @return le compte cree, ou {@code null} si rien n'a ete cree
     */
    @Transactional
    public Administrateur initialiser() {
        if (administrateurRepository.count() > 0) {
            if (renseigne(email) || renseigne(motDePasse)) {
                JOURNAL.info("Un administrateur existe deja : MONCHEPTEL_ADMIN_EMAIL et"
                        + " MONCHEPTEL_ADMIN_MOT_DE_PASSE_INITIAL sont ignorees. Retirez-les"
                        + " de l'environnement.");
            }
            return null;
        }
        if (!renseigne(email) || !renseigne(motDePasse)) {
            JOURNAL.warn("Aucun compte administrateur dans la base. Definissez"
                    + " MONCHEPTEL_ADMIN_EMAIL et MONCHEPTEL_ADMIN_MOT_DE_PASSE_INITIAL"
                    + " (au moins {} caracteres), puis relancez l'API : le compte sera cree"
                    + " avec ce mot de passe provisoire, a changer a la premiere connexion.",
                    LONGUEUR_MINIMALE);
            return null;
        }
        String adresse = Utilisateur.normaliserEmail(email);
        if (!adresse.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalStateException(
                    "MONCHEPTEL_ADMIN_EMAIL n'est pas une adresse e-mail valide.");
        }
        if (motDePasse.length() < LONGUEUR_MINIMALE || motDePasse.length() > 72) {
            throw new IllegalStateException(
                    "MONCHEPTEL_ADMIN_MOT_DE_PASSE_INITIAL doit faire entre "
                    + LONGUEUR_MINIMALE + " et 72 caracteres.");
        }
        if (utilisateurRepository.existsByEmail(adresse)) {
            throw new IllegalStateException(
                    "MONCHEPTEL_ADMIN_EMAIL est deja l'adresse d'un compte eleveur ou"
                    + " veterinaire : choisissez une autre adresse pour l'administrateur.");
        }
        Administrateur administrateur = new Administrateur(
                renseigne(nom) ? nom.strip() : "Administrateur",
                adresse, encodeur.encode(motDePasse), null);
        administrateur.exigerChangementMotDePasse();
        administrateurRepository.save(administrateur);
        JOURNAL.warn("Premier administrateur cree (compte {}). Son mot de passe est provisoire :"
                + " connectez-vous a la console pour le changer, puis retirez"
                + " MONCHEPTEL_ADMIN_MOT_DE_PASSE_INITIAL de l'environnement.",
                administrateur.getId());
        return administrateur;
    }

    private static boolean renseigne(String valeur) {
        return valeur != null && !valeur.isBlank();
    }
}
