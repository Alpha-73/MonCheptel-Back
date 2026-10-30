package ml.moncheptel.api.service;

import java.security.SecureRandom;
import java.util.List;
import ml.moncheptel.api.dto.reponse.PageReponse;
import ml.moncheptel.api.dto.reponse.ProfilReponse;
import ml.moncheptel.api.dto.reponse.UtilisateurResumeReponse;
import ml.moncheptel.api.dto.reponse.VeterinaireAdminReponse;
import ml.moncheptel.api.dto.reponse.VeterinaireResumeReponse;
import ml.moncheptel.api.dto.requete.JetonAppareilRequete;
import ml.moncheptel.api.dto.requete.ProfilRequete;
import ml.moncheptel.api.dto.requete.VeterinaireRequete;
import ml.moncheptel.api.exception.ConflitException;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.mapper.UtilisateurMapper;
import ml.moncheptel.api.modele.Competence;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.JetonAppareil;
import ml.moncheptel.api.modele.Maladie;
import ml.moncheptel.api.modele.Utilisateur;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.modele.Zone;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.repository.CodeReinitialisationRepository;
import ml.moncheptel.api.repository.CompetenceRepository;
import ml.moncheptel.api.repository.EleveurRepository;
import ml.moncheptel.api.repository.JetonAppareilRepository;
import ml.moncheptel.api.repository.MaladieRepository;
import ml.moncheptel.api.repository.SessionRepository;
import ml.moncheptel.api.repository.UniteElevageRepository;
import ml.moncheptel.api.repository.UtilisateurRepository;
import ml.moncheptel.api.repository.VeterinaireRepository;
import ml.moncheptel.api.repository.ZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comptes utilisateurs : profil, annuaire, creation des veterinaires.
 *
 * <h2>Le mot de passe provisoire du veterinaire</h2>
 *
 * <p>L'administration ne choisit pas le mot de passe d'un veterinaire. Elle
 * cree le compte ; le serveur tire un mot de passe aleatoire, l'envoie par
 * courriel et pose {@code doitChangerMotDePasse}. Un mot de passe saisi par
 * un tiers est un mot de passe connu d'un tiers, et il resterait en clair dans
 * la memoire de celui qui l'a tape.
 *
 * <p>Le tirage utilise {@link SecureRandom}, pas {@code Math.random()} :
 * un generateur previsible rendrait chaque compte veterinaire ouvrable par
 * quiconque connait l'heure de sa creation.
 */
@Service
@Transactional(readOnly = true)
public class UtilisateurService {

    /** 12 caracteres tires dans un alphabet de 58 : environ 70 bits. */
    private static final int LONGUEUR_PROVISOIRE = 12;

    /**
     * Alphabet sans les caracteres qui se confondent a la lecture — 0/O, 1/l/I.
     * Un mot de passe provisoire se recopie a la main depuis un courriel lu
     * sur un telephone ; economiser trois caracteres ne vaut pas une
     * assistance par compte.
     */
    private static final String ALPHABET =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom ALEA = new SecureRandom();

    private static final Logger JOURNAL = LoggerFactory.getLogger(UtilisateurService.class);

    private final UtilisateurRepository utilisateurRepository;
    private final EleveurRepository eleveurRepository;
    private final VeterinaireRepository veterinaireRepository;
    private final CompetenceRepository competenceRepository;
    private final MaladieRepository maladieRepository;
    private final ZoneRepository zoneRepository;
    private final JetonAppareilRepository jetonRepository;
    private final SessionRepository sessionRepository;
    private final CodeReinitialisationRepository codeRepository;
    private final PasswordEncoder encodeur;
    private final ServiceCourriel courriel;
    private final UtilisateurMapper mapper;
    private final OrientationService orientation;
    // ajout audit global G7 : retrait d'une filiere encore active
    private final UniteElevageRepository uniteRepository;

    public UtilisateurService(UtilisateurRepository utilisateurRepository,
                              EleveurRepository eleveurRepository,
                              VeterinaireRepository veterinaireRepository,
                              CompetenceRepository competenceRepository,
                              MaladieRepository maladieRepository,
                              ZoneRepository zoneRepository,
                              JetonAppareilRepository jetonRepository,
                              SessionRepository sessionRepository,
                              CodeReinitialisationRepository codeRepository,
                              PasswordEncoder encodeur,
                              ServiceCourriel courriel,
                              UtilisateurMapper mapper,
                              OrientationService orientation,
                              UniteElevageRepository uniteRepository) {
        this.utilisateurRepository = utilisateurRepository;
        this.eleveurRepository = eleveurRepository;
        this.veterinaireRepository = veterinaireRepository;
        this.competenceRepository = competenceRepository;
        this.maladieRepository = maladieRepository;
        this.zoneRepository = zoneRepository;
        this.jetonRepository = jetonRepository;
        this.sessionRepository = sessionRepository;
        this.codeRepository = codeRepository;
        this.encodeur = encodeur;
        this.courriel = courriel;
        this.mapper = mapper;
        this.orientation = orientation;
        this.uniteRepository = uniteRepository;
    }

    /**
     * Les routes d'administration des comptes ne touchent pas un compte
     * administrateur : sinon un administrateur pourrait changer l'adresse
     * d'un autre puis lui envoyer un mot de passe provisoire, et prendre son
     * compte ; ou se desactiver lui-meme et fermer la console a tous.
     */
    private Utilisateur compteGerable(Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Utilisateur", utilisateurId));
        if (utilisateur instanceof ml.moncheptel.api.modele.Administrateur) {
            throw new RegleMetierException(
                    "Un compte administrateur ne se gère pas depuis la console.");
        }
        return utilisateur;
    }

    // ── administration des comptes ──────────────────────────────────────────

    /**
     * Le tableau « Utilisateurs » de l'administration.
     *
     * <p>Le mappage se fait DANS cette methode, donc dans la transaction :
     * {@code open-in-view} est a faux, et la zone est paresseuse. Mapper dans le
     * controleur leverait {@code LazyInitializationException} sur la premiere
     * ligne dont la zone n'est pas deja chargee.
     *
     * @param recherche nom, e-mail ou telephone, partiel ; {@code null} pour tout
     * @param filiere   filtre de la maquette ; {@code null} pour toutes
     * @param zoneId    filtre de la maquette ; {@code null} pour toutes
     * @param actif     {@code null} pour les comptes actifs ET desactives
     */
    public PageReponse<UtilisateurResumeReponse> rechercherEleveurs(
            String recherche, Filiere filiere, Long zoneId, Boolean actif,
            Pageable pagination) {
        // Une chaine vide vaut « pas de filtre » : le champ de recherche de
        // l'ecran envoie "" des que l'utilisateur efface sa saisie, et
        // like '%%' ramenerait tout — ce qui est le but, mais en passant par
        // une comparaison inutile sur trois colonnes.
        String terme = recherche == null || recherche.isBlank() ? null : recherche.strip();

        Page<Eleveur> page = eleveurRepository.rechercher(terme, filiere, zoneId, actif, pagination);
        return PageReponse.de(page).avec(page.getContent().stream()
                .map(mapper::versResumeAdministration)
                .toList());
    }

    /**
     * Remet le mot de passe d'un compte a zero, a la demande de son titulaire.
     *
     * <h3>A quoi cela sert, puisque la reinitialisation par courriel existe</h3>
     *
     * <p>A traiter les cas que le libre-service ne couvre pas :
     * <ul>
     *   <li>l'adresse e-mail du compte n'est plus accessible — boite perdue,
     *       adresse saisie avec une faute a l'inscription ;</li>
     *   <li>l'eleveur ne sait pas se servir de sa boite, situation courante et
     *       qu'il serait malhonnete d'ignorer ;</li>
     *   <li>le compte porte une adresse en {@code .invalid} posee par la
     *       migration V5, faute d'adresse connue.</li>
     * </ul>
     *
     * <p>Sans cette route, ces comptes sont definitivement perdus. C'est la
     * raison d'etre du dispositif, pas un doublon de confort.
     *
     * <h3>Ce que la methode fait, dans cet ordre</h3>
     *
     * <ol>
     *   <li>tire un mot de passe provisoire ;</li>
     *   <li>l'enregistre sous forme d'empreinte et exige son changement a la
     *       prochaine connexion ;</li>
     *   <li>deverrouille le compte — il est tres souvent bloque, c'est
     *       justement pourquoi on appelle ;</li>
     *   <li>revoque toutes les sessions : si quelqu'un d'autre etait entre, il
     *       sort maintenant ;</li>
     *   <li>invalide les codes de reinitialisation en cours, qui ne doivent
     *       plus rien ouvrir ;</li>
     *   <li>envoie le courriel, APRES le reste.</li>
     * </ol>
     *
     * <h3>Pourquoi le mot de passe n'est PAS rendu a l'administrateur</h3>
     *
     * <p>C'est la question qui se pose, parce qu'elle rendrait le support
     * telephonique immediat : l'administrateur lirait le mot de passe a
     * l'eleveur. Et c'est precisement ce qu'il ne faut pas faire.
     *
     * <p>Un mot de passe qui s'affiche sur l'ecran de l'administration finit
     * dans une capture d'ecran, dans un journal de requetes, dans la memoire du
     * navigateur, et dans les mains de quiconque passe derriere. Il circule
     * alors sans trace. Le courriel, lui, va dans la boite du titulaire et de
     * personne d'autre.
     *
     * <p><b>La limite, assumee :</b> un compte dont l'adresse est inaccessible
     * ne peut donc pas etre debloque par cette route seule. Il faut d'abord
     * corriger l'adresse — c'est l'ecran « Modifier un utilisateur » —, puis
     * reinitialiser. Deux etapes, et une trace de qui a change quoi.
     */
    @Transactional
    public void reinitialiserMotDePasse(Long utilisateurId) {
        Utilisateur utilisateur = compteGerable(utilisateurId);
        exigerCourrielConfigure();

        String provisoire = motDePasseProvisoire();
        utilisateur.changerMotDePasse(encodeur.encode(provisoire));
        utilisateur.exigerChangementMotDePasse();
        utilisateur.deverrouiller();

        sessionRepository.revoquerToutesLesSessions(utilisateur.getId());
        codeRepository.invaliderTous(utilisateur.getId());

        // APRES la validation, hors de la requete (audit global G14) : un
        // courriel parti alors que la transaction echoue donnerait un mot de
        // passe qui ne fonctionne pas, et une panne SMTP n'annule plus rien.
        ApresValidation.envoyer("mot de passe reinitialise, compte " + utilisateur.getId(),
                () -> courriel.envoyerMotDePasseReinitialiseParAdmin(utilisateur, provisoire));
        JOURNAL.info("Mot de passe reinitialise par l'administration : compte {}",
                utilisateur.getId());
    }

    /**
     * Active ou desactive un compte.
     *
     * <h3>Desactiver plutot que supprimer</h3>
     *
     * <p>C'est le geste par defaut : supprimer un eleveur emporte ses lots,
     * son journal et sa comptabilite, sur lesquels reposent les statistiques.
     * La suppression definitive existe, a part et apres confirmation, dans
     * {@link SuppressionCompteService}.
     *
     * <p>Un compte desactive ne peut plus se connecter (voir
     * {@code AuthentificationService.connecter}) mais tout son historique reste.
     *
     * <h3>La desactivation ferme les sessions ouvertes</h3>
     *
     * <p>Sans cela, un compte desactive garde la main pendant une heure — la
     * duree de son jeton d'acces. {@code FiltreJwt} verifie l'etat du compte a
     * chaque requete, mais revoquer les sessions de rafraichissement evite qu'il
     * en obtienne un nouveau.
     *
     * <p>La reactivation, elle, ne rouvre rien : l'utilisateur se reconnecte.
     */
    @Transactional
    public void changerActivation(Long utilisateurId, boolean actif) {
        Utilisateur utilisateur = compteGerable(utilisateurId);

        utilisateur.setActif(actif);
        if (!actif) {
            sessionRepository.revoquerToutesLesSessions(utilisateur.getId());
            codeRepository.invaliderTous(utilisateur.getId());
            // Audit des notifications (11 octobre) : un compte desactive ne
            // recoit plus rien sur son telephone. Une reactivation n'en
            // rallume aucun : l'application reenregistre l'appareil a la
            // prochaine connexion.
            jetonRepository.findByUtilisateurIdAndActifTrue(utilisateur.getId())
                    .forEach(JetonAppareil::desactiver);
        }
        if (utilisateur instanceof Veterinaire) {
            // Un veterinaire qui part rend ses cas ouverts : chaque eleveur
            // concerne est prevenu pour en choisir un autre.
            if (!actif) {
                orientation.reprendreCasDe(utilisateur.getId(), true);
            }
        }
        JOURNAL.info("Compte {} {}", utilisateur.getId(), actif ? "reactive" : "desactive");
    }

    /**
     * L'utilisateur corrige ses propres informations.
     *
     * <h3>L'ordre des controles</h3>
     *
     * <p>On verifie TOUT avant d'ecrire quoi que ce soit. Poser le nom puis
     * echouer sur l'adresse laisserait un profil a moitie modifie, dans une
     * transaction qui annule — mais l'ecran, lui, aurait deja affiche une
     * erreur sur un champ alors qu'un autre etait accepte.
     *
     * <h3>Le changement d'adresse</h3>
     *
     * <p>L'adresse est l'identifiant de connexion. Trois choses en decoulent :
     *
     * <ul>
     *   <li>elle est normalisee avant comparaison — sinon « Balla@… » et
     *       « balla@… » paraitraient differentes et passeraient le test
     *       d'unicite, pour echouer ensuite sur la contrainte de la base, avec
     *       une erreur 500 incomprehensible ;</li>
     *   <li>l'unicite est verifiee ici pour rendre 409 avec une phrase utile,
     *       plutot que de laisser remonter la violation de contrainte ;</li>
     *   <li><b>un courriel part a l'ancienne adresse.</b> Voir
     *       {@code ProfilRequete} pour le raisonnement : c'est la seule parade
     *       contre la prise de compte depuis une session volee qui ne demande
     *       pas de champ absent de la maquette.</li>
     * </ul>
     *
     * <h3>Pourquoi les sessions ne sont PAS revoquees</h3>
     *
     * <p>Changer d'adresse ne change pas le mot de passe, et le jeton porte
     * l'identifiant numerique du compte, pas l'adresse. Deconnecter quelqu'un
     * parce qu'il a corrige une faute de frappe dans son nom serait une
     * punition sans raison.
     */
    @Transactional
    public ProfilReponse modifierProfil(Utilisateur utilisateur, ProfilRequete requete) {
        // On RECHARGE par identifiant. L'entite que le controleur passe vient
        // de @AuthenticationPrincipal, donc de FiltreJwt, qui la lit hors de
        // toute transaction applicative : elle arrive DETACHEE.
        //
        // La version precedente la mutait directement. Hibernate ne suit pas
        // une entite detachee : les setters ne produisaient AUCUN ordre SQL.
        // La route rendait 200 avec les nouvelles valeurs — le mappeur lisait
        // l'objet en memoire —, le courriel d'avertissement partait, le journal
        // ecrivait « Adresse de connexion changee », et la base restait
        // inchangee. L'utilisateur se reconnectait avec son ancienne adresse.
        //
        // Rien n'echouait. C'est le defaut le plus difficile a voir de cette
        // famille : la reponse est juste, seule la base ment.
        return appliquerProfil(utilisateur.getId(), requete, true);
    }

    /**
     * La meme correction, faite par l'administration sur le compte d'un autre.
     *
     * <h3>Pourquoi cette route existe en plus de la reinitialisation</h3>
     *
     * <p>C'est elle qui rend un compte a quelqu'un dont l'adresse est perdue :
     * l'administrateur corrige l'adresse ici, puis reinitialise le mot de
     * passe, qui part alors a la bonne boite. Les deux etapes sont voulues —
     * voir {@link #reinitialiserMotDePasse}.
     *
     * <p>C'est aussi elle qui repare les comptes marques
     * {@code @adresse-a-corriger.invalid} par la migration V5.
     *
     * <h3>L'ancienne adresse est prevenue ici aussi</h3>
     *
     * <p>Et c'est encore plus important que dans le cas precedent : un
     * administrateur qui changerait une adresse par erreur — ou par
     * malveillance — couperait quelqu'un de son compte sans qu'il le sache.
     * Le courriel part a l'ancienne boite, qui reste la sienne.
     */
    @Transactional
    public ProfilReponse modifierParAdministration(Long utilisateurId, ProfilRequete requete) {
        compteGerable(utilisateurId);
        return appliquerProfil(utilisateurId, requete, false);
    }

    /**
     * Le coeur partage des deux routes precedentes.
     *
     * <p>Une seule copie des regles : unicite de l'adresse, filiere
     * obligatoire pour un eleveur, avertissement a l'ancienne boite. Deux
     * copies finiraient par diverger, et c'est toujours celle que personne ne
     * relit qui garderait le trou.
     */
    private ProfilReponse appliquerProfil(Long utilisateurId, ProfilRequete requete,
                                          boolean parLeTitulaire) {
        // findById et non l'entite recue : seule une entite GEREE par la
        // session est suivie par Hibernate. C'est la ligne qui fait que cette
        // methode ecrit reellement.
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> RessourceIntrouvableException.de(
                        "Utilisateur", utilisateurId));
        String nouvelleAdresse = Utilisateur.normaliserEmail(requete.email());
        String ancienneAdresse = utilisateur.getEmail();
        boolean adresseChange = !nouvelleAdresse.equals(ancienneAdresse);

        if (adresseChange && utilisateurRepository.existsByEmail(nouvelleAdresse)) {
            throw new ConflitException("Cette adresse e-mail est déjà associée à un compte");
        }
        // L'adresse est l'IDENTIFIANT de connexion. Sans cette verification,
        // un jeton d'acces vole (60 minutes) suffisait a prendre le compte :
        // nouvelle adresse, puis « mot de passe oublie » (audit du 10 octobre).
        if (adresseChange && parLeTitulaire
                && (requete.motDePasseActuel() == null
                    || !encodeur.matches(requete.motDePasseActuel(), utilisateur.getMotDePasse()))) {
            throw new RegleMetierException(
                    "Pour changer d'adresse e-mail, confirmez votre mot de passe actuel.");
        }
        Zone zone = zoneRepository.findById(requete.zoneId())
                .orElseThrow(() -> RessourceIntrouvableException.de("Zone", requete.zoneId()));

        // Un eleveur sans filiere ne recevrait plus aucune alerte ni aucun
        // conseil cible, et son tableau de bord n'aurait plus d'onglet. La
        // regle est la meme qu'a l'inscription.
        if (utilisateur instanceof Eleveur eleveur) {
            if (requete.filieres() == null || requete.filieres().isEmpty()) {
                throw new RegleMetierException("Indiquez au moins une filière");
            }
            exigerFilieresRetirablesSansElevageActif(eleveur, requete.filieres());
            eleveur.setFilieresPratiquees(requete.filieres());
        }

        Long ancienneZone = utilisateur.getZone() == null ? null : utilisateur.getZone().getId();
        utilisateur.setNom(requete.nom().strip());
        utilisateur.setEmail(nouvelleAdresse);
        utilisateur.setTelephone(requete.telephone());
        utilisateur.setZone(zone);
        utilisateur.setLangue(requete.langue());
        // Audit du 29 octobre (point 10) : un eleveur qui change de zone ne
        // garde pas, sur un cas pas encore accepte, un veterinaire de
        // l'ancienne zone (la regle « meme zone » vaut a tout moment).
        if (utilisateur instanceof Eleveur && !zone.getId().equals(ancienneZone)) {
            orientation.reprendreCasHorsZone(utilisateur.getId());
        }

        if (adresseChange) {
            // Apres l'ecriture, et sans faire echouer l'operation : un serveur
            // de courriel en panne ne doit pas empecher quelqu'un de corriger
            // son profil. L'echec est journalise.
            ApresValidation.envoyer("changement d'adresse, compte " + utilisateur.getId(),
                    () -> courriel.avertirChangementAdresse(utilisateur, ancienneAdresse));
            JOURNAL.info("Adresse de connexion changee pour le compte {}", utilisateur.getId());
        }
        return mapper.versProfil(utilisateur);
    }

    /**
     * Refuse de retirer une filiere qui porte encore un elevage actif (audit
     * global G7) : ses lots disparaitraient de l'accueil et ne se
     * modifieraient plus, tout en restant vendables et comptes.
     */
    private void exigerFilieresRetirablesSansElevageActif(
            Eleveur eleveur, java.util.Set<ml.moncheptel.api.modele.enumeration.Filiere> gardees) {
        for (var filiere : eleveur.filieresOrdonnees()) {
            if (gardees.contains(filiere)) {
                continue;
            }
            var actives = uniteRepository.chercherParProprietaireEtFiliere(
                    eleveur.getId(), filiere,
                    ml.moncheptel.api.modele.enumeration.StatutUnite.ACTIF);
            if (!actives.isEmpty()) {
                String codes = actives.stream().limit(3)
                        .map(ml.moncheptel.api.modele.UniteElevage::getCode)
                        .collect(java.util.stream.Collectors.joining(", "));
                throw new RegleMetierException(
                        "Vous ne pouvez pas retirer la filière " + filiere.getLibelle()
                        + " : " + (actives.size() > 1 ? "des élevages y sont" : "un élevage y est")
                        + " encore actif" + (actives.size() > 1 ? "s" : "") + " (" + codes
                        + (actives.size() > 3 ? "…" : "") + "). Clôturez-les d'abord.");
            }
        }
    }

    /**
     * La fiche complete d'un compte, pour l'administration.
     *
     * <p>Rend le meme {@code ProfilReponse} que « Mes informations ». Aucun
     * champ de plus n'est utile ici : l'etat d'activation et de verrouillage
     * figure deja dans la ligne de la liste, et le mot de passe n'a aucune
     * raison de quitter le serveur, pour personne.
     */
    public ProfilReponse fichePourAdministration(Long utilisateurId) {
        return mapper.versProfil(utilisateurRepository.chercherAvecZone(utilisateurId)
                .orElseThrow(() -> RessourceIntrouvableException.de(
                        "Utilisateur", utilisateurId)));
    }

    /**
     * Corrige la fiche d'un veterinaire : ecran « Modifier un veterinaire ».
     *
     * <p>Les champs sont ceux que l'administration pose a la creation du
     * compte — titre, specialite, zone d'intervention — plus les coordonnees.
     * Le veterinaire ne peut pas les changer lui-meme : un titre que personne
     * ne verifie viderait l'annuaire de son sens.
     *
     * <p>Le telephone est obligatoire ici, contrairement a l'eleveur :
     * l'annuaire l'affiche pour que l'eleveur appelle. Un veterinaire sans
     * numero est injoignable, et la mise en relation est le coeur de la
     * plateforme.
     */
    @Transactional
    public VeterinaireAdminReponse modifierVeterinaire(Long veterinaireId,
                                                       VeterinaireRequete requete) {
        Veterinaire veterinaire = veterinaireRepository.findById(veterinaireId)
                .orElseThrow(() -> RessourceIntrouvableException.de(
                        "Veterinaire", veterinaireId));
        String adresse = Utilisateur.normaliserEmail(requete.email());
        String ancienne = veterinaire.getEmail();

        if (!adresse.equals(ancienne) && utilisateurRepository.existsByEmail(adresse)) {
            throw new ConflitException("Cette adresse e-mail est déjà associée à un compte");
        }
        Zone zone = zoneRepository.findById(requete.zoneId())
                .orElseThrow(() -> RessourceIntrouvableException.de("Zone", requete.zoneId()));
        Zone intervention = zoneRepository.findById(requete.zoneInterventionId())
                .orElseThrow(() -> RessourceIntrouvableException.de(
                        "Zone", requete.zoneInterventionId()));

        veterinaire.setNom(requete.nom().strip());
        veterinaire.setEmail(adresse);
        veterinaire.setTelephone(requete.telephone());
        veterinaire.setZone(zone);
        Long ancienneZone = veterinaire.getZoneIntervention() == null
                ? null : veterinaire.getZoneIntervention().getId();
        veterinaire.setZoneIntervention(intervention);
        veterinaire.setTitre(requete.titre());
        veterinaire.setSpecialite(requete.specialite());
        if (!intervention.getId().equals(ancienneZone)) {
            // Nouvelle zone : les cas pas encore ouverts de l'ancienne zone
            // sont rendus, et leurs eleveurs choisissent un autre veterinaire.
            orientation.reprendreCasDe(veterinaire.getId(), false);
        }

        if (!adresse.equals(ancienne)) {
            ApresValidation.envoyer("changement d'adresse, compte " + veterinaire.getId(),
                    () -> courriel.avertirChangementAdresse(veterinaire, ancienne));
        }
        return mapper.versAdministration(veterinaire);
    }

    /**
     * Le tableau « Vetérinaires » de l'administration.
     *
     * <p>Different de {@link #annuaire} sur deux points, et c'est pour cela
     * qu'il a sa propre methode : l'annuaire ne rend que les veterinaires
     * ACTIFS d'une zone donnee, parce qu'un eleveur n'a rien a faire d'un
     * compte desactive. L'administration, elle, doit voir precisement ceux-la.
     */
    public List<VeterinaireAdminReponse> veterinairesPourAdministration(Long zoneId) {
        return veterinaireRepository.findAll().stream()
                .filter(v -> zoneId == null
                        || (v.getZoneIntervention() != null
                            && zoneId.equals(v.getZoneIntervention().getId())))
                .sorted(java.util.Comparator.comparing(Veterinaire::getNom))
                .map(mapper::versAdministration)
                .toList();
    }

    /** Ecran « Modifier un vétérinaire » : la fiche avec ses competences cochees. */
    public VeterinaireAdminReponse veterinairePourAdministration(Long veterinaireId) {
        return mapper.versAdministration(chargerVeterinaire(veterinaireId));
    }

    /**
     * Ecran « Mes compétences » du veterinaire : les maladies qu'il traite.
     * La mise en forme est faite ici, dans la transaction, parce que les
     * filieres d'une maladie se chargent paresseusement.
     */
    public <T> List<T> competencesDe(Veterinaire veterinaire,
                                     java.util.function.Function<Maladie, T> miseEnForme) {
        return competenceRepository.findByVeterinaireIdOrderByMaladieNomAsc(veterinaire.getId())
                .stream().map(Competence::getMaladie).map(miseEnForme).toList();
    }

    /**
     * Les deux memes gestes, faits par l'administration sur un autre compte.
     *
     * <p>Elles existent parce que l'ecran « Modifier un vétérinaire » porte le
     * bloc « Maladies traitées » avec ses cases a cocher, et que son texte
     * annonce : « Toute modification est immédiatement prise en compte par le
     * moteur de mise en relation ».
     *
     * <p>Elles delegent aux memes methodes que l'espace du veterinaire : une
     * seule regle de doublon, une seule facon de retirer.
     */
    @Transactional
    public void declarerCompetencePourVeterinaire(Long veterinaireId, Long maladieId) {
        declarerCompetence(chargerVeterinaire(veterinaireId), maladieId);
    }

    @Transactional
    public void retirerCompetencePourVeterinaire(Long veterinaireId, Long maladieId) {
        retirerCompetence(chargerVeterinaire(veterinaireId), maladieId);
    }

    /** 422 explicite plutot qu'un mot de passe provisoire qui ne partira jamais (G14). */
    private void exigerCourrielConfigure() {
        if (!courriel.estConfigure()) {
            throw new RegleMetierException(
                    "L'envoi de courriels n'est pas configuré sur le serveur : le mot de passe "
                    + "provisoire ne pourrait pas être envoyé. Renseignez MONCHEPTEL_SMTP_USER "
                    + "et MONCHEPTEL_SMTP_PASSWORD, puis réessayez.");
        }
    }

    private Veterinaire chargerVeterinaire(Long veterinaireId) {
        return veterinaireRepository.findById(veterinaireId)
                .orElseThrow(() -> RessourceIntrouvableException.de(
                        "Veterinaire", veterinaireId));
    }

    public ProfilReponse profil(Utilisateur utilisateur) {
        return mapper.versProfil(utilisateur);
    }

    public List<VeterinaireResumeReponse> annuaire(Long zoneId) {
        return veterinaireRepository.chercherAnnuaire(zoneId)
                .stream().map(mapper::versResume).toList();
    }

    @Transactional
    public VeterinaireAdminReponse creerVeterinaire(VeterinaireRequete requete) {
        exigerCourrielConfigure();
        String email = Utilisateur.normaliserEmail(requete.email());
        if (utilisateurRepository.existsByEmail(email)) {
            throw new ConflitException("Cette adresse e-mail est déjà associée à un compte");
        }
        Zone zone = zone(requete.zoneId());
        Zone intervention = zone(requete.zoneInterventionId());

        String provisoire = motDePasseProvisoire();
        Veterinaire veterinaire = new Veterinaire(
                requete.nom().strip(), email,
                encodeur.encode(provisoire), zone, intervention);
        // Obligatoire pour un veterinaire : l'annuaire l'affiche aux eleveurs.
        veterinaire.setTelephone(requete.telephone());
        veterinaire.setTitre(requete.titre());
        veterinaire.setSpecialite(requete.specialite());
        veterinaire.exigerChangementMotDePasse();
        veterinaireRepository.save(veterinaire);

        // Envoye APRES la validation de la transaction, hors de la requete
        // (audit global G14) : un courriel parti alors que le commit echoue
        // donnerait un mot de passe pour un compte qui n'existe pas.
        ApresValidation.envoyer("acces provisoire, nouveau veterinaire",
                () -> courriel.envoyerMotDePasseProvisoire(veterinaire, provisoire));

        return mapper.versAdministration(veterinaire);
    }

    /**
     * Declare une competence.
     *
     * <p>Le doublon est refuse : une competence enregistree deux fois ferait
     * apparaitre le meme veterinaire deux fois dans la liste des candidats a
     * l'orientation, et doublerait ses chances d'etre choisi sans raison.
     */
    @Transactional
    public void declarerCompetence(Veterinaire veterinaire, Long maladieId) {
        if (competenceRepository.existsByVeterinaireIdAndMaladieId(
                veterinaire.getId(), maladieId)) {
            throw new ConflitException("Cette compétence est déjà déclarée");
        }
        Maladie maladie = maladieRepository.findById(maladieId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Maladie", maladieId));
        if (!maladie.isActive()) {
            throw new RegleMetierException("Cette maladie a été retirée du catalogue.");
        }
        competenceRepository.save(new Competence(veterinaire, maladie));
    }

    /**
     * Le veterinaire se declare disponible ou non (audit veterinaire M1).
     *
     * <p>Indisponible : les eleveurs ne peuvent plus le choisir, et les cas
     * qu'il n'a pas encore ouverts (ORIENTE) redeviennent NOUVEAUX : chaque
     * eleveur est prevenu pour choisir un autre veterinaire. Ceux qu'il a
     * pris en charge lui restent.
     */
    @Transactional
    public boolean changerDisponibilite(Veterinaire connecte, boolean disponible) {
        Veterinaire veterinaire = veterinaireRepository.findById(connecte.getId())
                .orElseThrow(() -> RessourceIntrouvableException.de("Vétérinaire", connecte.getId()));
        boolean avant = veterinaire.isDisponible();
        veterinaire.setDisponible(disponible);
        if (avant && !disponible) {
            orientation.reprendreCasDe(veterinaire.getId(), false);
        }
        return veterinaire.isDisponible();
    }

    @Transactional
    public void retirerCompetence(Veterinaire veterinaire, Long maladieId) {
        competenceRepository.deleteByVeterinaireIdAndMaladieId(veterinaire.getId(), maladieId);
        // Les cas qu'il n'a pas encore ouverts repartent s'il ne convient plus.
        orientation.reprendreCasDe(veterinaire.getId(), false);
    }

    /**
     * Enregistre le jeton Firebase de l'appareil.
     *
     * <p>Un meme jeton peut changer de proprietaire : un telephone prete,
     * reinstalle, ou une deconnexion suivie d'une autre connexion. On le
     * rattache alors au nouvel utilisateur plutot que d'en creer un second —
     * sinon l'ancien continuerait de recevoir les notifications du nouveau.
     */
    @Transactional
    public void enregistrerAppareil(Utilisateur utilisateur, JetonAppareilRequete requete) {
        jetonRepository.findByJeton(requete.jeton())
                .ifPresentOrElse(
                        existant -> existant.rattacherA(utilisateur),
                        () -> jetonRepository.save(new JetonAppareil(
                                utilisateur, requete.jeton(), requete.plateforme())));
    }

    /**
     * L'appareil ne recoit plus de notifications. Appele a la deconnexion.
     *
     * <h3>Le controle d'appartenance manquait</h3>
     *
     * <p>La methode prenait le jeton seul et desactivait la ligne, quelle
     * qu'elle soit. N'importe quel utilisateur authentifie qui connaissait le
     * jeton Firebase d'un autre coupait ses notifications — alertes sanitaires
     * comprises. Et la route ne transmettait meme pas l'appelant, donc aucune
     * verification n'etait possible plus bas.
     *
     * <p>On compare donc le proprietaire. {@code ifPresent} sans le
     * {@code else} : un jeton inconnu ou appartenant a un autre ne fait rien
     * et ne dit rien. Rendre 403 sur le jeton d'autrui confirmerait son
     * existence, et rendre 404 sur un jeton inconnu permettrait de tester
     * lesquels sont enregistres.
     */
    @Transactional
    public void oublierAppareil(Utilisateur proprietaire, String jeton) {
        jetonRepository.findByJeton(jeton)
                .filter(j -> j.getUtilisateur().getId().equals(proprietaire.getId()))
                .ifPresent(JetonAppareil::desactiver);
    }

    private Zone zone(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Zone", id));
    }

    private static String motDePasseProvisoire() {
        StringBuilder sb = new StringBuilder(LONGUEUR_PROVISOIRE);
        for (int i = 0; i < LONGUEUR_PROVISOIRE; i++) {
            sb.append(ALPHABET.charAt(ALEA.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
