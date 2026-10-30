package ml.moncheptel.api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import ml.moncheptel.api.config.ProprietesSecurite;
import ml.moncheptel.api.dto.reponse.JetonReponse;
import ml.moncheptel.api.dto.requete.ChangementMotDePasseRequete;
import ml.moncheptel.api.dto.requete.ConnexionRequete;
import ml.moncheptel.api.dto.requete.DemandeReinitialisationRequete;
import ml.moncheptel.api.dto.requete.InscriptionRequete;
import ml.moncheptel.api.dto.requete.RafraichissementRequete;
import ml.moncheptel.api.dto.requete.ReinitialisationRequete;
import ml.moncheptel.api.modele.CodeReinitialisation;
import ml.moncheptel.api.repository.CodeReinitialisationRepository;
import ml.moncheptel.api.exception.ConflitException;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.Session;
import ml.moncheptel.api.modele.Utilisateur;
import ml.moncheptel.api.modele.Zone;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.repository.EleveurRepository;
import ml.moncheptel.api.repository.SessionRepository;
import ml.moncheptel.api.repository.UtilisateurRepository;
import ml.moncheptel.api.repository.ZoneRepository;
import ml.moncheptel.api.securite.FabriqueJeton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connexion, inscription, changement et reinitialisation du mot de passe.
 *
 * <h2>Le verrouillage apres cinq echecs</h2>
 *
 * <p>Cinq tentatives, puis quinze minutes de blocage. Sans ce plafond, un
 * code a quatre chiffres se force en quelques minutes ; c'est precisement
 * pourquoi le code a quatre chiffres initialement prevu a ete remplace par un
 * mot de passe.
 *
 * <p>Le comptage est porte par {@code Utilisateur}, pas par ce service :
 * c'est une propriete du compte, et un second point d'entree — une
 * reinitialisation, un futur connecteur — ne doit pas pouvoir la contourner.
 *
 * <h2>Le message d'erreur est le meme dans les deux cas</h2>
 *
 * <p>Adresse inconnue et mot de passe faux rendent « E-mail ou mot de passe
 * incorrect ». Distinguer les deux dirait a qui essaie des adresses au hasard
 * lesquelles correspondent a un compte existant — c'est-a-dire lui fournirait
 * la moitie du travail.
 *
 * <h2>La reinitialisation par courriel</h2>
 *
 * <p>Trois etapes : demande, verification du code, changement. Chacune est
 * ecrite pour ne rien apprendre a qui n'a pas acces a la boite de reception.
 * Voir {@link #demanderReinitialisation} et {@link #reinitialiser}.
 */
@Service
@Transactional(readOnly = true)
public class AuthentificationService {

    private static final Logger JOURNAL =
            LoggerFactory.getLogger(AuthentificationService.class);

    /**
     * Duree de validite d'un code de reinitialisation.
     *
     * <p>Quinze minutes : le temps d'ouvrir sa boite et de recopier six
     * chiffres, pas davantage. Une heure laisserait un code valable dans une
     * boite consultee sur un telephone partage.
     */
    private static final int VALIDITE_CODE_MINUTES = 15;

    /**
     * Nombre de codes qu'un compte peut demander par heure.
     *
     * <p>Sans plafond, cette route devient un outil d'envoi de courriel :
     * quelques milliers d'appels sur l'adresse de quelqu'un remplissent sa
     * boite, et notre serveur SMTP se fait classer comme indesirable.
     */
    private static final int DEMANDES_MAX_PAR_HEURE = 3;

    /** Un code a six chiffres : 100000 a 999999, jamais de zero en tete. */
    private static final int CODE_MINIMUM = 100_000;
    private static final int CODE_ETENDUE = 900_000;

    private static final SecureRandom ALEA = new SecureRandom();

    private final UtilisateurRepository utilisateurRepository;
    private final EleveurRepository eleveurRepository;
    private final ZoneRepository zoneRepository;
    private final SessionRepository sessionRepository;
    private final CodeReinitialisationRepository codeRepository;
    private final PasswordEncoder encodeur;
    private final FabriqueJeton fabriqueJeton;
    private final ServiceCourriel courriel;
    private final CompteurEchecs compteurEchecs;
    private final ProprietesSecurite proprietes;

    /**
     * Une empreinte BCrypt VALIDE, fabriquee au demarrage avec le meme
     * encodeur que les vrais comptes : meme algorithme, meme cout. Elle ne
     * correspond a aucun mot de passe connu ; elle sert uniquement a depenser
     * le temps d'une verification. Voir {@link #dissimuler}.
     */
    private final String empreinteFactice;

    public AuthentificationService(UtilisateurRepository utilisateurRepository,
                                   EleveurRepository eleveurRepository,
                                   ZoneRepository zoneRepository,
                                   SessionRepository sessionRepository,
                                   CodeReinitialisationRepository codeRepository,
                                   PasswordEncoder encodeur,
                                   FabriqueJeton fabriqueJeton,
                                   ServiceCourriel courriel,
                                   CompteurEchecs compteurEchecs,
                                   ProprietesSecurite proprietes) {
        this.utilisateurRepository = utilisateurRepository;
        this.eleveurRepository = eleveurRepository;
        this.zoneRepository = zoneRepository;
        this.sessionRepository = sessionRepository;
        this.codeRepository = codeRepository;
        this.encodeur = encodeur;
        this.fabriqueJeton = fabriqueJeton;
        this.courriel = courriel;
        this.compteurEchecs = compteurEchecs;
        this.proprietes = proprietes;
        this.empreinteFactice = encodeur.encode("sans-compte-" + ALEA.nextLong());
    }

    @Transactional
    public JetonReponse connecter(ConnexionRequete requete, String appareil, String adresseIp) {
        Optional<Utilisateur> trouve = utilisateurRepository
                .chercherParEmail(Utilisateur.normaliserEmail(requete.email()));

        // LE TEMPS DE REPONSE EST LE MEME, que le compte existe ou non.
        //
        // Le message d'erreur etait identique dans les deux cas, ce qui
        // fermait l'oracle par le CONTENU. Il restait ouvert par la DUREE :
        // une adresse inconnue rendait sa reponse en quelques millisecondes,
        // une adresse connue attendait la verification BCrypt (cout 12, de
        // l'ordre de 250 ms). Chronometrer cent adresses suffisait a trier
        // celles qui ont un compte — la liste que tout le reste du dispositif
        // s'emploie a rendre introuvable.
        //
        // Chaque sortie anticipee depense donc le meme travail qu'une
        // verification reelle. Voir dissimuler.
        if (trouve.isEmpty()) {
            dissimuler(requete.motDePasse());
            throw identifiantsInvalides();
        }
        Utilisateur utilisateur = trouve.get();

        // Un compte verrouille ou desactive rend LE MEME message qu'un mot de
        // passe faux. Les deux phrases precedentes — « Compte temporairement
        // bloque » et « Ce compte a ete desactive » — etaient atteignables des
        // le premier essai et CONFIRMAIENT l'existence du compte, ce que tout
        // le reste du dispositif s'emploie a cacher.
        //
        // Le prix a payer est reel : quelqu'un dont le compte est desactive ne
        // le comprend pas depuis l'ecran. C'est le role du courriel envoye par
        // l'administration et de l'ecran « Pas d'acces a votre e-mail ? ».
        if (utilisateur.estVerrouille() || !utilisateur.isActif()) {
            dissimuler(requete.motDePasse());
            throw identifiantsInvalides();
        }
        if (!encodeur.matches(requete.motDePasse(), utilisateur.getMotDePasse())) {
            // DANS UNE TRANSACTION A PART. L'increment etait fait ici meme sur
            // l'entite geree, juste avant le throw — et comme
            // RegleMetierException herite de RuntimeException, Spring annulait
            // la transaction ET l'increment avec elle. Le compteur restait a
            // zero, le verrouillage apres cinq essais n'existait pas, et cette
            // route publique acceptait un nombre illimite de tentatives.
            // Voir CompteurEchecs.
            compteurEchecs.enregistrerEchecConnexion(
                    utilisateur.getId(),
                    proprietes.tentativesAvantVerrouillage(),
                    proprietes.dureeVerrouillageMinutes());
            throw identifiantsInvalides();
        }

        utilisateur.signalerConnexionReussie();
        return ouvrirSession(utilisateur, appareil, adresseIp);
    }

    @Transactional
    public JetonReponse inscrire(InscriptionRequete requete, String appareil, String adresseIp) {
        String email = Utilisateur.normaliserEmail(requete.email());
        if (utilisateurRepository.existsByEmail(email)) {
            throw new ConflitException("Cette adresse e-mail est déjà associée à un compte");
        }
        Zone zone = zoneRepository.findById(requete.zoneId())
                .orElseThrow(() -> RessourceIntrouvableException.de("Zone", requete.zoneId()));

        Eleveur eleveur = new Eleveur(
                requete.nom().strip(),
                email,
                encodeur.encode(requete.motDePasse()),
                zone,
                EnumSet.copyOf(requete.filieres()));
        // Facultatif pour un eleveur : contact, pas identifiant.
        eleveur.setTelephone(requete.telephone());
        eleveurRepository.save(eleveur);

        return ouvrirSession(eleveur, appareil, adresseIp);
    }

    /**
     * Changement de mot de passe.
     *
     * <p>Toutes les sessions sont revoquees, y compris celle qui vient de
     * faire la demande. C'est le point du changement de mot de passe : si on
     * le change parce qu'un telephone a ete vole, laisser les autres sessions
     * ouvertes ne protege de rien. L'application redemande donc la connexion,
     * et c'est voulu.
     */
    /**
     * @param utilisateurId l'identifiant, PAS l'entite. L'entite que porte le
     *                      filtre d'authentification est detachee — elle a ete
     *                      lue dans une transaction deja fermee. La modifier
     *                      ne declencherait aucune ecriture : Hibernate ne
     *                      surveille que les entites attachees a une session
     *                      ouverte. On la recharge donc ici, dans la
     *                      transaction qui va ecrire.
     */
    @Transactional
    public void changerMotDePasse(Long utilisateurId, ChangementMotDePasseRequete requete) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Utilisateur", utilisateurId));
        if (!encodeur.matches(requete.ancienMotDePasse(), utilisateur.getMotDePasse())) {
            throw new RegleMetierException("Ancien mot de passe incorrect");
        }
        if (encodeur.matches(requete.nouveauMotDePasse(), utilisateur.getMotDePasse())) {
            throw new RegleMetierException(
                    "Le nouveau mot de passe doit être différent de l'ancien");
        }
        utilisateur.changerMotDePasse(encodeur.encode(requete.nouveauMotDePasse()));
        sessionRepository.revoquerToutesLesSessions(utilisateur.getId());
        // Les codes de reinitialisation en cours deviennent inutilisables.
        // Sans cette ligne, un code deja parti dans une boite compromise
        // restait valable un quart d'heure APRES que la victime a change son
        // mot de passe — c'est-a-dire pendant le geste meme qu'elle fait pour
        // reprendre la main.
        codeRepository.invaliderTous(utilisateur.getId());
    }

    // ── mot de passe oublie ─────────────────────────────────────────────────

    /**
     * Premiere etape : envoie un code a l'adresse donnee, si elle correspond a
     * un compte.
     *
     * <h3>Cette methode ne dit JAMAIS si le compte existe</h3>
     *
     * <p>Elle ne leve aucune exception et ne rend rien, quelle que soit
     * l'issue. Le controleur repond donc toujours 202 avec le meme message.
     *
     * <p>C'est la seule facon d'empecher l'ENUMERATION DES COMPTES. Si la route
     * rendait 404 sur une adresse inconnue, n'importe qui pourrait, avec une
     * liste d'adresses, etablir lesquelles sont inscrites sur MonCheptel. Pour
     * un eleveur, cela revele une activite commerciale ; pour un veterinaire,
     * une affiliation. Et cette liste serait ensuite revendue.
     *
     * <p><b>La contrepartie, assumee :</b> quelqu'un qui se trompe d'adresse
     * attend un courriel qui n'arrivera jamais, sans comprendre pourquoi. Le
     * message de l'ecran doit donc dire « si cette adresse correspond a un
     * compte, vous allez recevoir un code » — et non « un code vous a ete
     * envoye », qui serait un mensonge une fois sur deux.
     *
     * <h3>Les trois cas de sortie silencieuse</h3>
     *
     * <p>Adresse inconnue, compte desactive, plafond horaire atteint. Les trois
     * sont journalises au niveau DEBUG : sans cela, un eleveur qui ne recoit
     * rien laisserait une trace nulle part, et nous n'aurions aucun moyen de
     * distinguer « adresse inconnue » de « SMTP en panne ».
     *
     * <h3>Pourquoi l'echec d'envoi n'est pas propage</h3>
     *
     * <p>Une exception SMTP remontant jusqu'au client donnerait 500 sur une
     * adresse qui existe, et 202 sur une adresse inconnue. La panne du serveur
     * de courriel deviendrait alors l'oracle que tout ce dispositif cherche a
     * supprimer. L'echec est donc journalise en ERROR et tu cote client.
     */
    @Transactional
    public void demanderReinitialisation(DemandeReinitialisationRequete requete) {
        String email = Utilisateur.normaliserEmail(requete.email());
        Optional<Utilisateur> trouve = utilisateurRepository.chercherParEmail(email);

        if (trouve.isEmpty()) {
            JOURNAL.debug("Demande de reinitialisation sur une adresse inconnue : refusee en silence");
            return;
        }
        Utilisateur utilisateur = trouve.get();

        if (!utilisateur.isActif()) {
            JOURNAL.debug("Demande de reinitialisation sur le compte desactive {}",
                    utilisateur.getId());
            return;
        }

        long recentes = codeRepository.compterDemandesDepuis(
                utilisateur.getId(), Instant.now().minus(Duration.ofHours(1)));
        if (recentes >= DEMANDES_MAX_PAR_HEURE) {
            JOURNAL.debug("Plafond horaire de reinitialisation atteint pour le compte {} ({} demandes)",
                    utilisateur.getId(), recentes);
            return;
        }

        // Les codes precedents deviennent inutilisables : deux codes valables
        // en meme temps doublent la surface d'attaque, et l'utilisateur ne sait
        // de toute facon plus lequel est le bon.
        codeRepository.invaliderTous(utilisateur.getId());

        String code = codeASixChiffres();
        codeRepository.save(new CodeReinitialisation(
                utilisateur,
                empreinteDuCode(utilisateur.getId(), code),
                Instant.now().plus(Duration.ofMinutes(VALIDITE_CODE_MINUTES))));

        try {
            courriel.envoyerCodeReinitialisation(utilisateur, code, VALIDITE_CODE_MINUTES);
        } catch (RuntimeException e) {
            // Le code reste enregistre : l'utilisateur peut redemander, et le
            // plafond horaire lui en laisse deux autres.
            JOURNAL.error("Envoi du code de reinitialisation impossible pour le compte {} : {}",
                    utilisateur.getId(), e.getMessage());
        }
    }

    /**
     * Seconde etape : verifie le code et pose le nouveau mot de passe.
     *
     * <h3>Un seul message d'erreur pour trois causes</h3>
     *
     * <p>Adresse inconnue, aucun code en cours, code faux : la meme phrase. Un
     * message qui distinguerait « aucune demande en cours » de « code
     * incorrect » redonnerait l'oracle que
     * {@link #demanderReinitialisation} vient de fermer.
     *
     * <p>Le code expire, en revanche, a son propre message. Ce n'est pas une
     * fuite : pour le savoir il faut deja detenir le bon code. Et l'utilisateur
     * qui a mis vingt minutes a ouvrir sa boite doit comprendre qu'il lui faut
     * en redemander un, pas croire qu'il recopie mal.
     *
     * <h3>Ce que la reussite entraine</h3>
     *
     * <ul>
     *   <li>le code est consomme — il ne resservira pas ;</li>
     *   <li>toutes les sessions sont revoquees : si quelqu'un d'autre etait
     *       entre dans le compte, il en sort ;</li>
     *   <li>le compte est deverrouille. C'est le point important : un
     *       utilisateur bloque apres cinq tentatives a souvent oublie son mot
     *       de passe, et c'est justement pour cela qu'il le reinitialise.
     *       Garder le verrou lui refuserait l'entree avec le mot de passe qu'il
     *       vient de choisir ;</li>
     *   <li>{@code doitChangerMotDePasse} retombe a faux : il vient d'en
     *       choisir un.</li>
     * </ul>
     */
    @Transactional
    public void reinitialiser(ReinitialisationRequete requete) {
        String email = Utilisateur.normaliserEmail(requete.email());
        // Le courriel presente « 482 913 » : on retire tout ce qui n'est pas un
        // chiffre avant de comparer.
        String chiffres = requete.code().replaceAll("[^0-9]", "");

        Utilisateur utilisateur = utilisateurRepository.chercherParEmail(email)
                .orElseThrow(AuthentificationService::codeInvalide);

        CodeReinitialisation code = codeRepository.dernierActif(utilisateur.getId())
                .orElseThrow(AuthentificationService::codeInvalide);

        // L'EMPREINTE EST COMPAREE EN PREMIER. Les deux tests qui suivent
        // disent quelque chose du compte — « ce code a expire » prouve qu'un
        // code a ete emis, donc que le compte existe. Les placer avant la
        // comparaison en faisait un oracle d'existence utilisable SANS detenir
        // le code : demander un code sur l'adresse d'autrui, attendre quinze
        // minutes, presenter 000000, et lire la reponse.
        //
        // Place apres, le message d'expiration n'est atteint que par qui
        // possede le bon code — ce que le commentaire precedent affirmait a
        // tort.
        if (!memeEmpreinte(code.getEmpreinteCode(),
                           empreinteDuCode(utilisateur.getId(), chiffres))) {
            // Meme correction, meme raison, et c'etait plus grave ici : sans
            // elle, le plafond de cinq essais ne montait jamais, et un code a
            // six chiffres se forcait en un million d'appels sur une route
            // publique — une prise de compte sans acceder a la boite.
            compteurEchecs.enregistrerEchecCode(code.getId());
            JOURNAL.debug("Code de reinitialisation errone pour le compte {}", utilisateur.getId());
            throw codeInvalide();
        }

        // L'empreinte est juste : on peut maintenant expliquer precisement.
        if (code.getExpireLe().isBefore(Instant.now())) {
            throw new RegleMetierException("Ce code a expiré. Demandez-en un nouveau.");
        }
        if (!code.estUtilisable()) {
            throw new RegleMetierException(
                    "Ce code a été saisi trop de fois. Demandez-en un nouveau.");
        }

        code.consommer();
        utilisateur.changerMotDePasse(encodeur.encode(requete.nouveauMotDePasse()));
        utilisateur.deverrouiller();
        sessionRepository.revoquerToutesLesSessions(utilisateur.getId());
        JOURNAL.info("Mot de passe reinitialise par courriel pour le compte {}", utilisateur.getId());
    }

    /**
     * Supprime les codes de reinitialisation expires depuis plus de
     * {@code jours} jours. Appele par {@code TachesPlanifiees}.
     *
     * @return le nombre de lignes supprimees, pour le journal
     */
    @Transactional
    public int purgerCodesExpires(int jours) {
        return codeRepository.supprimerExpires(
                Instant.now().minus(Duration.ofDays(jours)));
    }

    /**
     * Supprime les sessions expirees depuis plus de {@code jours} jours
     * (audit global G15) : chaque rafraichissement en cree une, la table
     * grossissait d'une ligne par heure et par utilisateur actif. Une session
     * revoquee expire elle aussi : elle part avec le meme delai.
     */
    @Transactional
    public int purgerSessionsExpirees(int jours) {
        return sessionRepository.supprimerExpirees(
                Instant.now().minus(Duration.ofDays(jours)));
    }

    /**
     * Renouvelle une session : un jeton de rafraichissement valable contre un
     * nouveau couple de jetons.
     *
     * <h3>Le defaut que cette methode corrige</h3>
     *
     * <p>Le serveur remettait un jeton de rafraichissement a la connexion, et
     * aucune route ne l'acceptait. Toute session mourait au bout d'une heure :
     * l'eleveur devait ressaisir son mot de passe a chaque heure d'usage.
     *
     * <h3>Ce qui est verifie, dans cet ordre</h3>
     *
     * <ol>
     *   <li>la signature, l'expiration et le TYPE du jeton (un jeton d'acces
     *       presente ici est refuse) ;</li>
     *   <li>la session en base : elle existe, n'est ni revoquee ni expiree.
     *       C'est ici que {@code Session.revoquee} est enfin LUE ;</li>
     *   <li>le compte : actif (le verrou apres echecs ne compte pas ici) ;</li>
     *   <li>l'empreinte du mot de passe, comme pour le jeton d'acces : un
     *       changement de mot de passe coupe aussi le rafraichissement.</li>
     * </ol>
     *
     * <h3>Rotation</h3>
     *
     * <p>La session presentee est revoquee et une nouvelle est ouverte : un
     * jeton de rafraichissement ne sert qu'une fois. Un jeton recopie par un
     * tiers cesse de fonctionner des que le titulaire s'est rafraichi.
     * <b>Ce que cela ne fait pas :</b> rejouer un ancien jeton n'invalide pas
     * les autres sessions du compte. Le faire deconnecterait aussi le
     * titulaire legitime dont la reponse s'est perdue en route (reseau mobile),
     * ce qui est plus frequent qu'un vol.
     *
     * <p>Un seul message d'erreur pour toutes les causes : le client n'a rien a
     * en tirer d'autre que « reconnectez-vous ».
     */
    @Transactional
    public JetonReponse rafraichir(RafraichissementRequete requete,
                                   String appareil, String adresseIp) {
        String jeton = requete.jetonRafraichissement();
        FabriqueJeton.JetonLu lu = fabriqueJeton.lireJetonRafraichissement(jeton);
        if (lu == null) {
            throw sessionInvalide();
        }
        Session session = sessionRepository
                .findByEmpreinteJeton(fabriqueJeton.empreinte(jeton))
                .orElseThrow(AuthentificationService::sessionInvalide);
        if (!session.estValide()) {
            throw sessionInvalide();
        }
        Utilisateur utilisateur = session.getUtilisateur();
        // Le verrou apres echecs n'est PAS lu ici (audit global G2) : il ne
        // bloque que les nouvelles connexions par mot de passe. Le lire
        // permettait a n'importe qui, avec cinq mauvais mots de passe, de
        // couper la session d'un autre.
        if (!utilisateur.getId().equals(lu.id())
                || !utilisateur.isActif()
                || !fabriqueJeton.versionCorrespond(utilisateur, lu.versionMotDePasse())) {
            throw sessionInvalide();
        }
        session.revoquer();
        return ouvrirSession(utilisateur, appareil, adresseIp);
    }

    @Transactional
    public void deconnecter(String jeton) {
        sessionRepository.findByEmpreinteJeton(fabriqueJeton.empreinte(jeton))
                .ifPresent(Session::revoquer);
    }

    // ── outillage ───────────────────────────────────────────────────────────

    private JetonReponse ouvrirSession(Utilisateur utilisateur, String appareil, String adresseIp) {
        Duration duree = Duration.ofMinutes(proprietes.dureeJetonMinutes());
        String acces = fabriqueJeton.creerJetonAcces(utilisateur, duree);
        String rafraichissement = fabriqueJeton.creerJetonRafraichissement(utilisateur);

        sessionRepository.save(new Session(
                utilisateur,
                fabriqueJeton.empreinte(rafraichissement),
                Instant.now().plus(Duration.ofDays(proprietes.dureeRafraichissementJours())),
                appareil, adresseIp));

        return new JetonReponse(
                acces, rafraichissement, duree.toSeconds(),
                utilisateur.getId(), utilisateur.getNom(), utilisateur.getRole(),
                utilisateur.isDoitChangerMotDePasse());
    }

    /**
     * Depense le temps d'une verification de mot de passe, pour rien.
     *
     * <p>Le resultat est jete : seule compte la duree. L'empreinte factice a
     * le meme algorithme et le meme cout que celles des vrais comptes, sans
     * quoi on remplacerait un ecart de 250 ms par un autre.
     *
     * <h3>Ce que cela ne ferme pas</h3>
     *
     * <p>Un mauvais mot de passe sur un compte reel ecrit en plus le compteur
     * d'echecs, dans une transaction a part : quelques millisecondes de plus
     * que le chemin « adresse inconnue ». L'ecart est de deux ordres de
     * grandeur sous celui qu'on vient de supprimer, et se noie dans la gigue
     * d'un reseau mobile. Le fermer demanderait d'ecrire aussi en base pour
     * les adresses inconnues, ce qui ouvrirait une porte plus grave : remplir
     * la table de comptes qui n'existent pas.
     */
    private void dissimuler(String motDePasse) {
        encodeur.matches(motDePasse == null ? "" : motDePasse, empreinteFactice);
    }

    private static RegleMetierException identifiantsInvalides() {
        return new RegleMetierException("E-mail ou mot de passe incorrect");
    }

    private static RegleMetierException sessionInvalide() {
        return new RegleMetierException("Session expirée. Reconnectez-vous.");
    }

    /** Le seul message rendu quand la reinitialisation echoue. Voir le Javadoc. */
    private static RegleMetierException codeInvalide() {
        return new RegleMetierException(
                "Code invalide. Vérifiez l'adresse et le code reçu, "
                + "ou demandez un nouveau code.");
    }

    /**
     * Un entier de 100000 a 999999, tire d'un generateur cryptographique.
     *
     * <p>{@code SecureRandom} et non {@code Random} : le second est previsible
     * des qu'on connait quelques tirages, ce qui suffirait a deviner le code
     * suivant. {@code nextInt(borne)} est non biaise, contrairement a un
     * {@code % 900000} sur un entier quelconque.
     *
     * <p>Jamais de zero en tete : un code affiche « 048 213 » se recopie
     * « 48213 » une fois sur deux, et le zero perdu consomme une tentative.
     */
    private static String codeASixChiffres() {
        return String.valueOf(CODE_MINIMUM + ALEA.nextInt(CODE_ETENDUE));
    }

    /**
     * Empreinte de ce qui est stocke en base : jamais le code lui-meme.
     *
     * <h3>Pourquoi l'identifiant du compte entre dans l'empreinte</h3>
     *
     * <p>Sans lui, les six chiffres « 482913 » auraient la meme empreinte pour
     * tous les comptes de la plateforme. Une seule table precalculee — un
     * million de lignes, quelques secondes a produire — retrouverait alors tous
     * les codes en cours d'un coup. En salant par l'identifiant, cette table
     * doit etre refaite pour chaque compte.
     *
     * <h3>Ce que cette empreinte ne protege PAS</h3>
     *
     * <p>Un code a six chiffres se retrouve par force brute en un million de
     * SHA-256, soit une fraction de seconde sur une machine ordinaire. Celui
     * qui lit la table peut donc retrouver le code, salage ou non. Ce qui limite
     * reellement les degats, c'est la duree de validite et l'usage unique.
     *
     * <p>Le hachage protege contre autre chose : une sauvegarde egaree, un
     * export, un journal de requetes lentes. Le code n'y apparait pas en clair.
     * C'est utile, et ce n'est pas la meme garantie qu'un mot de passe BCrypt.
     * Ecrit ici pour que personne ne lise « hache » comme « inviolable ».
     */
    private String empreinteDuCode(Long utilisateurId, String code) {
        return fabriqueJeton.empreinte(utilisateurId + ":" + code);
    }

    /**
     * Comparaison a duree constante de deux empreintes.
     *
     * <p>{@code equals} sur une chaine s'arrete au premier caractere different :
     * le temps de reponse renseigne alors sur le nombre de caracteres justes.
     * L'attaque est difficile a mener au travers d'un reseau, mais
     * {@code MessageDigest.isEqual} ne coute rien et ferme la question.
     */
    private static boolean memeEmpreinte(String attendue, String presentee) {
        return MessageDigest.isEqual(
                attendue.getBytes(StandardCharsets.UTF_8),
                presentee.getBytes(StandardCharsets.UTF_8));
    }

    /** Sert au filtre de securite : retrouver l'utilisateur porte par un jeton. */
    public Utilisateur parId(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Utilisateur", id));
    }

    /** Sert au simulateur et aux ecrans d'annuaire. */
    public boolean pratique(Utilisateur u, Filiere filiere) {
        return u instanceof Eleveur e && e.pratique(filiere);
    }
}
