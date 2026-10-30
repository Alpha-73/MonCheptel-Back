package ml.moncheptel.api.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import ml.moncheptel.api.dto.reponse.AlerteReponse;
import ml.moncheptel.api.dto.requete.AlerteRequete;
import ml.moncheptel.api.exception.ConflitException;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.mapper.NotificationMapper;
import ml.moncheptel.api.modele.Administrateur;
import ml.moncheptel.api.modele.Alerte;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.Maladie;
import ml.moncheptel.api.modele.Zone;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutAlerte;
import ml.moncheptel.api.modele.enumeration.TypeNotification;
import ml.moncheptel.api.repository.AlerteRepository;
import ml.moncheptel.api.repository.EleveurRepository;
import ml.moncheptel.api.repository.MaladieRepository;
import ml.moncheptel.api.repository.SignalementMaladieRepository;
import ml.moncheptel.api.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alertes epidemiques.
 *
 * <h2>Le declenchement reste humain</h2>
 *
 * <p>Ce service sait compter les signalements d'une maladie dans une zone. Il
 * ne declenche PAS l'alerte tout seul.
 *
 * <p>Une alerte envoie une notification a tous les eleveurs concernes d'une
 * region, et on ne la rattrape pas. Un declenchement automatique sur un seuil
 * ferait crier au loup a la premiere serie de saisies mal faites — et la
 * deuxieme alerte, la vraie, ne serait plus lue. Le systeme propose, un
 * administrateur decide.
 */
@Service
@Transactional(readOnly = true)
public class AlerteService {

    /** Le jour change a minuit heure de Bamako, pas a minuit UTC (c'est la meme heure). */
    private static final ZoneId FUSEAU = ZoneId.of("Africa/Bamako");

    /** Fenetre d'observation du comptage epidemiologique, en jours. */
    private static final int FENETRE_JOURS = 14;

    private final AlerteRepository alerteRepository;
    private final MaladieRepository maladieRepository;
    private final ZoneRepository zoneRepository;
    private final EleveurRepository eleveurRepository;
    private final SignalementMaladieRepository signalementRepository;
    private final NotificationService notificationService;
    private final NotificationMapper mapper;

    public AlerteService(AlerteRepository alerteRepository,
                         MaladieRepository maladieRepository,
                         ZoneRepository zoneRepository,
                         EleveurRepository eleveurRepository,
                         SignalementMaladieRepository signalementRepository,
                         NotificationService notificationService,
                         NotificationMapper mapper) {
        this.alerteRepository = alerteRepository;
        this.maladieRepository = maladieRepository;
        this.zoneRepository = zoneRepository;
        this.eleveurRepository = eleveurRepository;
        this.signalementRepository = signalementRepository;
        this.notificationService = notificationService;
        this.mapper = mapper;
    }

    /**
     * Declenche une alerte et notifie les eleveurs cibles.
     *
     * <p>{@code portee} — le nombre d'eleveurs touches — est calcule ici et
     * stocke sur l'alerte. Le recalculer a l'affichage donnerait un nombre qui
     * bouge : un eleveur qui s'inscrit apres coup changerait la portee d'une
     * alerte passee, alors que lui n'a rien recu.
     */
    @Transactional
    public AlerteReponse declencher(Administrateur administrateur, AlerteRequete requete) {
        Maladie maladie = maladieRepository.findById(requete.maladieId())
                .orElseThrow(() -> RessourceIntrouvableException.de("Maladie", requete.maladieId()));
        Zone zone = zoneRepository.findById(requete.zoneId())
                .orElseThrow(() -> RessourceIntrouvableException.de("Zone", requete.zoneId()));

        if (!maladie.isActive()) {
            throw new RegleMetierException("Cette maladie a été retirée du catalogue.");
        }
        verifierFiliere(maladie, requete.filiere());
        // Le doublon tient compte de la filiere : une alerte PPR « ovins »
        // n'empeche pas une alerte PPR « caprins » sur la meme zone. Une
        // alerte « toutes filieres » couvre les autres, et inversement.
        if (alerteRepository.existeDejaActive(maladie.getId(), zone.getId(), requete.filiere())) {
            throw new ConflitException(
                    "Une alerte " + maladie.getNom() + " est déjà active sur " + zone.getNom()
                    + (requete.filiere() == null ? "" : " pour cette filière"));
        }

        List<Eleveur> cibles = eleveurRepository.chercherCible(
                zone.getId(), maladie.getId(), requete.filiere());

        Alerte alerte = alerteRepository.save(new Alerte(
                maladie, zone, requete.filiere(), requete.message(),
                cibles.size(), administrateur));
        alerte.setDateFin(requete.dateFin());

        notificationService.creerPourChacun(cibles, TypeNotification.ALERTE,
                "Alerte " + maladie.getNom() + " — " + zone.getNom(),
                requete.message(), "ALERTE", alerte.getId());

        return mapper.versReponse(alerte);
    }

    /**
     * Reformule le message d'une alerte en cours.
     *
     * <p>Seul le texte change ; la portee, la zone et la maladie restent.
     * {@link ml.moncheptel.api.dto.requete.MessageAlerteRequete} dit pourquoi.
     *
     * <h3>Les notifications deja parties ne sont pas reecrites</h3>
     *
     * <p>La maquette annonce que « modifier le message met a jour la
     * notification affichee dans l'application des eleveurs concernes ». C'est
     * vrai pour l'alerte elle-meme, qui est relue a chaque ouverture de
     * l'ecran. Ce ne l'est PAS pour les notifications deja envoyees par
     * Firebase : une notification poussee est partie, on ne la rattrape pas.
     *
     * <p>Ecrit ici pour que personne ne promette l'inverse a la soutenance.
     *
     * <h3>Une alerte terminee ne se modifie plus</h3>
     *
     * <p>Elle est devenue une archive. La reformuler reviendrait a reecrire ce
     * que les eleveurs ont reellement recu.
     */
    @Transactional
    public AlerteReponse modifierMessage(Long alerteId, String message, LocalDate dateFin) {
        Alerte alerte = alerteRepository.findById(alerteId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Alerte", alerteId));
        if (alerte.getStatut() != StatutAlerte.ACTIVE) {
            throw new RegleMetierException(
                    "Cette alerte est terminée. Son message est ce que les éleveurs "
                    + "ont reçu : il ne se réécrit pas.");
        }
        alerte.setMessage(message.strip());
        alerte.setDateFin(dateFin);
        return mapper.versReponse(alerte);
    }

    @Transactional
    public AlerteReponse cloturer(Long alerteId) {
        Alerte alerte = alerteRepository.findById(alerteId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Alerte", alerteId));
        if (alerte.getStatut() == StatutAlerte.TERMINEE) {
            throw new RegleMetierException("Cette alerte est déjà clôturée.");
        }
        clore(alerte);
        return mapper.versReponse(alerte);
    }

    /**
     * Clot les alertes dont la date de fin est passee. Appelee par la tache
     * planifiee ; rend le nombre d'alertes closes.
     */
    @Transactional
    public int cloturerEchues() {
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        List<Alerte> echues = alerteRepository.chercherEchues(aujourdhui);
        echues.forEach(this::clore);
        return echues.size();
    }

    private void clore(Alerte alerte) {
        alerte.cloturer();
        // Les éleveurs qui ont reçu l'alerte apprennent qu'elle est levée :
        // sinon le bandeau disparaît sans explication.
        List<Eleveur> cibles = eleveurRepository.chercherCible(
                alerte.getZone().getId(), alerte.getMaladie().getId(), alerte.getFiliere());
        notificationService.creerPourChacun(cibles, TypeNotification.ALERTE,
                "Fin d'alerte " + alerte.getMaladie().getNom() + " — " + alerte.getZone().getNom(),
                "L'alerte " + alerte.getMaladie().getNom() + " sur " + alerte.getZone().getNom()
                + " est levée. Restez attentif aux symptômes et signalez tout nouveau cas.",
                "ALERTE", alerte.getId());
    }

    /** Alertes a afficher sur le tableau de bord d'un eleveur. */
    public List<AlerteReponse> pourEleveur(Eleveur eleveur) {
        if (eleveur.getZone() == null || eleveur.getFilieresPratiquees().isEmpty()) {
            return List.of();
        }
        // Une alerte echue que la tache n'a pas encore close ne s'affiche plus.
        LocalDate aujourdhui = LocalDate.now(FUSEAU);
        return mapper.versAlertes(alerteRepository.chercherPourEleveur(
                        eleveur.getZone().getId(), EnumSet.copyOf(eleveur.getFilieresPratiquees()))
                .stream().filter(a -> !a.estEchue(aujourdhui)).toList());
    }

    public List<AlerteReponse> toutes() {
        return mapper.versAlertes(alerteRepository.chercherToutesTriees());
    }

    /** Une alerte, pour l'ecran « Modifier une alerte ». */
    public AlerteReponse une(Long alerteId) {
        return mapper.versReponse(alerteRepository.findById(alerteId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Alerte", alerteId)));
    }

    /**
     * Combien de signalements de cette maladie dans cette zone, sur les
     * {@value #FENETRE_JOURS} derniers jours.
     *
     * <p>C'est le nombre que l'ecran de declenchement affiche a
     * l'administrateur AVANT qu'il ne confirme. Il ne declenche rien.
     */
    public long compterSignalementsRecents(Long maladieId, Long zoneId) {
        return signalementRepository.compterDansZone(
                maladieId, zoneId, Instant.now().minus(FENETRE_JOURS, ChronoUnit.DAYS));
    }

    /**
     * Eleveurs que l'alerte toucherait. Filiere nulle = toutes les filieres
     * de la maladie (jamais toute la zone).
     */
    public long compterEleveursCibles(Long maladieId, Long zoneId, Filiere filiere) {
        Maladie maladie = maladieRepository.findById(maladieId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Maladie", maladieId));
        verifierFiliere(maladie, filiere);
        return eleveurRepository.compterCible(zoneId, maladieId, filiere);
    }

    /** Une filiere demandee doit etre une filiere de la maladie (422 sinon). */
    private static void verifierFiliere(Maladie maladie, Filiere filiere) {
        if (filiere != null && !maladie.concerne(filiere)) {
            throw new RegleMetierException(
                    maladie.getNom() + " ne touche pas cette filière : choisissez une de ses filières.");
        }
    }
}
