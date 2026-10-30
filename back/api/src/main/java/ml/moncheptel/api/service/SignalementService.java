package ml.moncheptel.api.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ml.moncheptel.api.dto.reponse.AccueilVeterinaireReponse;
import ml.moncheptel.api.dto.reponse.PageReponse;
import ml.moncheptel.api.dto.reponse.SignalementReponse;
import ml.moncheptel.api.dto.reponse.SymptomeReponse;
import ml.moncheptel.api.dto.reponse.VeterinaireResumeReponse;
import ml.moncheptel.api.dto.requete.PriseEnChargeRequete;
import ml.moncheptel.api.dto.requete.SignalementRequete;
import ml.moncheptel.api.exception.AccesRefuseException;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.mapper.MaladieMapper;
import ml.moncheptel.api.mapper.SignalementMapper;
import ml.moncheptel.api.mapper.UtilisateurMapper;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.SignalementMaladie;
import ml.moncheptel.api.modele.Symptome;
import ml.moncheptel.api.modele.UniteElevage;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;
import ml.moncheptel.api.modele.enumeration.TrancheEffectif;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;
import ml.moncheptel.api.modele.enumeration.TypeNotification;
import ml.moncheptel.api.modele.Maladie;
import ml.moncheptel.api.repository.MaladieRepository;
import ml.moncheptel.api.repository.SignalementMaladieRepository;
import ml.moncheptel.api.repository.SymptomeRepository;
import ml.moncheptel.api.repository.UniteElevageRepository;
import ml.moncheptel.api.repository.VeterinaireRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signalement d'une maladie, de la saisie de l'eleveur a l'orientation.
 *
 * <h2>Le parcours en un seul appel</h2>
 *
 * <p>La maquette montre trois ecrans — sujet, symptomes, ampleur — puis un
 * resultat. L'application accumule les reponses et poste une fois. Un
 * aller-retour par ecran ferait attendre l'eleveur trois fois sur un reseau
 * de campagne, pour rien : rien ne se calcule avant que les trois reponses
 * soient la.
 *
 * <h2>Le diagnostic est fige, pas recalcule</h2>
 *
 * <p>Le verdict est ecrit sur le signalement au moment ou il est cree. Si la
 * ponderation du referentiel evolue, un cas traite hier continue d'afficher ce
 * que le veterinaire a vu quand il a decide.
 */
@Service
@Transactional(readOnly = true)
public class SignalementService {

    private final SignalementMaladieRepository signalementRepository;
    private final UniteElevageRepository uniteRepository;
    private final SymptomeRepository symptomeRepository;
    private final VeterinaireRepository veterinaireRepository;
    private final DiagnosticService diagnosticService;
    private final NotificationService notificationService;
    private final SignalementMapper signalementMapper;
    private final MaladieMapper maladieMapper;
    private final OrientationService orientationService;
    private final MaladieRepository maladieRepository;
    private final UtilisateurMapper utilisateurMapper;

    public SignalementService(SignalementMaladieRepository signalementRepository,
                              UniteElevageRepository uniteRepository,
                              SymptomeRepository symptomeRepository,
                              VeterinaireRepository veterinaireRepository,
                              DiagnosticService diagnosticService,
                              NotificationService notificationService,
                              SignalementMapper signalementMapper,
                              MaladieMapper maladieMapper,
                              OrientationService orientationService,
                              MaladieRepository maladieRepository,
                              UtilisateurMapper utilisateurMapper) {
        this.signalementRepository = signalementRepository;
        this.uniteRepository = uniteRepository;
        this.symptomeRepository = symptomeRepository;
        this.veterinaireRepository = veterinaireRepository;
        this.diagnosticService = diagnosticService;
        this.notificationService = notificationService;
        this.signalementMapper = signalementMapper;
        this.maladieMapper = maladieMapper;
        this.orientationService = orientationService;
        this.maladieRepository = maladieRepository;
        this.utilisateurMapper = utilisateurMapper;
    }

    /**
     * Facultatif, par mutateur et non par constructeur : les tests unitaires
     * qui construisent ce service a la main n'en ont pas besoin.
     */
    private AvisAdministration avisAdministration;

    /** Historique des refus (V14). Facultatif pour les memes raisons. */
    private ml.moncheptel.api.repository.RefusSignalementRepository refusRepository;

    @Autowired(required = false)
    void setRefusRepository(ml.moncheptel.api.repository.RefusSignalementRepository refusRepository) {
        this.refusRepository = refusRepository;
    }

    /** Le detail d'un cas porte l'historique de ses refus. */
    private SignalementReponse avecRefus(SignalementReponse r) {
        if (r == null || refusRepository == null) {
            return r;
        }
        return r.avecRefus(signalementMapper.versRefus(
                refusRepository.findBySignalementIdOrderByDateRefusDesc(r.id())));
    }

    @Autowired(required = false)
    void setAvisAdministration(AvisAdministration avisAdministration) {
        this.avisAdministration = avisAdministration;
    }

    @Transactional
    public SignalementReponse signaler(Eleveur eleveur, SignalementRequete requete) {
        UniteElevage unite = uniteRepository.findById(requete.uniteId())
                .orElseThrow(() -> RessourceIntrouvableException.de("Unité", requete.uniteId()));
        if (!unite.getProprietaire().getId().equals(eleveur.getId())) {
            throw new AccesRefuseException();
        }
        if (unite.getStatut().estSortie()) {
            throw new RegleMetierException(
                    "L'unité " + unite.getCode() + " est sortie du cheptel : "
                    + "elle ne peut plus faire l'objet d'un signalement");
        }
        exigerSymptomesConnus(requete.symptomeIds());

        // Un animal suivi individuellement (bovin, ovin, caprin) est UN sujet.
        // La tranche n'a de sens que pour un lot : on ignore celle recue et
        // on retient « moins de 10 ». Sans cela, un eleveur qui cocherait
        // « plus de 50 » pour une seule vache ferait monter la gravite a
        // ELEVE (graviteInduite) et declencherait une orientation a tort.
        TrancheEffectif tranche = unite.getFiliere().suiviParLot()
                ? requete.trancheEffectif()
                : TrancheEffectif.MOINS_DE_10;

        // Un lot de 20 tetes ne peut pas avoir « plus de 50 » sujets touches.
        // Accepter cette tranche gonflerait la gravite (graviteInduite) sur
        // une donnee impossible : on la refuse, avec l'effectif reel.
        if (tranche.getBorneBasse() > unite.getEffectifRestant()) {
            throw new RegleMetierException(
                    "Le lot " + unite.getCode() + " ne compte que "
                    + unite.getEffectifRestant() + " têtes : la tranche « "
                    + tranche.getLibelle() + " » est impossible.");
        }

        SignalementMaladie signalement = new SignalementMaladie(
                eleveur, unite, requete.symptomeIds(),
                tranche, requete.precision());

        DiagnosticService.Verdict verdict =
                diagnosticService.verdict(requete.symptomeIds(), unite.getFiliere());
        signalement.enregistrerDiagnostic(
                verdict.probable(), verdict.probabilite(),
                verdict.alternative(), verdict.probabiliteAlternative());

        signalementRepository.save(signalement);

        // Plus d'orientation automatique : l'eleveur choisit lui-meme son
        // veterinaire parmi ceux de sa zone (veterinairesProposes).
        //
        // Audit du 10 octobre : si le cas DOIT etre oriente et que personne
        // n'est proposable (aucun veterinaire competent dans la zone, ou
        // eleveur sans zone), il restait NOUVEAU sans que personne ne soit
        // prevenu. L'administration l'est desormais, pour l'attribuer.
        if (avisAdministration != null && signalement.doitEtreOriente()
                && orientationService.candidats(signalement).isEmpty()) {
            String maladie = signalement.getMaladieProbable() == null
                    ? "maladie non identifiée" : signalement.getMaladieProbable().getNom();
            String zone = eleveur.getZone() == null ? "sans zone" : eleveur.getZone().getNom();
            avisAdministration.prevenir(TypeNotification.ORIENTATION,
                    "Cas grave sans vétérinaire",
                    unite.getCode() + " (" + eleveur.getNom() + ", " + zone + ") — " + maladie
                    + " : aucun vétérinaire compétent n'est proposable. Attribuez-le depuis la console.",
                    "SIGNALEMENT", signalement.getId());
        }
        return signalementMapper.versReponseEleveur(signalement, symptomes(signalement));
    }

    /**
     * Les veterinaires de la zone de l'eleveur qui traitent la maladie
     * probable, dans un ordre aleatoire different a chaque appel.
     */
    public List<VeterinaireResumeReponse> veterinairesProposes(Eleveur eleveur, Long signalementId) {
        SignalementMaladie s = chargerPourEleveur(eleveur, signalementId);
        return orientationService.candidats(s).stream()
                .map(utilisateurMapper::versResume)
                .toList();
    }

    /** L'eleveur confie son signalement au veterinaire choisi. */
    @Transactional
    public SignalementReponse choisirVeterinaire(Eleveur eleveur, Long signalementId,
                                                 Long veterinaireId) {
        SignalementMaladie s = chargerPourEleveur(eleveur, signalementId);
        orientationService.choisirParEleveur(s, veterinaireId);
        return avecRefus(signalementMapper.versReponseEleveur(s, symptomes(s)));
    }

    private SignalementMaladie chargerPourEleveur(Eleveur eleveur, Long id) {
        SignalementMaladie s = signalementRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Signalement", id));
        if (!s.getEleveur().getId().equals(eleveur.getId())) {
            throw RessourceIntrouvableException.de("Signalement", id);
        }
        return s;
    }

    // ── cote eleveur : suivi ────────────────────────────────────────────────

    /** « Mes signalements » : du plus recent au plus ancien (audit F3). */
    public List<SignalementReponse> mesSignalements(Eleveur eleveur) {
        List<SignalementMaladie> liste =
                signalementRepository.findByEleveurIdOrderByDateSignalementDesc(eleveur.getId());
        Map<Long, Symptome> parId = symptomesDe(liste);
        // V14 : la liste de l'eleveur porte les refus (une requete pour tous),
        // pour lui dire pourquoi un cas attend un autre veterinaire.
        Map<Long, List<ml.moncheptel.api.modele.RefusSignalement>> refus = new java.util.HashMap<>();
        if (refusRepository != null && !liste.isEmpty()) {
            refusRepository.findBySignalementIdInOrderByDateRefusDesc(
                    liste.stream().map(SignalementMaladie::getId).toList())
                    .forEach(r -> refus.computeIfAbsent(r.getSignalementId(),
                            k -> new java.util.ArrayList<>()).add(r));
        }
        return liste.stream()
                .map(s -> {
                    SignalementReponse r = signalementMapper.versReponseEleveur(s, symptomes(s, parId));
                    List<ml.moncheptel.api.modele.RefusSignalement> siens = refus.get(s.getId());
                    return r == null || siens == null ? r : r.avecRefus(signalementMapper.versRefus(siens));
                })
                .toList();
    }

    /**
     * Un signalement : pour l'eleveur qui l'a fait (vue eleveur), ou pour le
     * veterinaire a qui il est confie (vue praticien). 404 pour tout autre,
     * y compris un ancien veterinaire : on ne revele pas l'existence d'un cas.
     */
    public SignalementReponse detail(ml.moncheptel.api.modele.Utilisateur lecteur, Long id) {
        SignalementMaladie s = signalementRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Signalement", id));
        if (lecteur instanceof Eleveur && s.getEleveur().getId().equals(lecteur.getId())) {
            return avecRefus(signalementMapper.versReponseEleveur(s, symptomes(s)));
        }
        if (lecteur instanceof Veterinaire && s.getVeterinaire() != null
                && s.getVeterinaire().getId().equals(lecteur.getId())) {
            return avecRefus(signalementMapper.versReponseVeterinaire(s, symptomes(s)));
        }
        throw RessourceIntrouvableException.de("Signalement", id);
    }

    // ── cote veterinaire ────────────────────────────────────────────────────

    public PageReponse<SignalementReponse> fileDuVeterinaire(Veterinaire veterinaire,
                                                             StatutSignalement statut,
                                                             Pageable pageable) {
        var page = signalementRepository.chercherPourVeterinaire(
                veterinaire.getId(), statut, pageable);
        Map<Long, Symptome> parId = symptomesDe(page.getContent());
        return PageReponse.de(page).avec(page.getContent().stream()
                .map(s -> signalementMapper.versReponseVeterinaire(s, symptomes(s, parId)))
                .toList());
    }

    /**
     * Ecran « Veto - Accueil ». « Traités ce mois » se compte sur la date de
     * traitement, dans le mois civil a Bamako.
     */
    public AccueilVeterinaireReponse accueil(Veterinaire veterinaire, long conseilsPublies,
                                             long conseilsEnModeration) {
        // Trois comptages en base (relecture, point 15) : l'accueil chargeait
        // TOUTE la file du veterinaire, sans pagination, pour compter.
        ZoneId bamako = ZoneId.of("Africa/Bamako");
        Instant debutMois = LocalDate.now(bamako).withDayOfMonth(1)
                .atStartOfDay(bamako).toInstant();
        List<StatutSignalement> ouverts =
                List.of(StatutSignalement.ORIENTE, StatutSignalement.EN_COURS);
        long enAttente = signalementRepository
                .countByVeterinaireIdAndStatutIn(veterinaire.getId(), ouverts);
        long graves = signalementRepository.countByVeterinaireIdAndStatutInAndGravite(
                veterinaire.getId(), ouverts, NiveauGravite.ELEVE);
        long traitesCeMois = signalementRepository
                .countByVeterinaireIdAndStatutAndDateTraitementGreaterThanEqual(
                        veterinaire.getId(), StatutSignalement.TRAITE, debutMois);
        return new AccueilVeterinaireReponse(enAttente, graves, traitesCeMois,
                conseilsPublies, conseilsEnModeration, veterinaire.isDisponible());
    }

    @Transactional
    public SignalementReponse prendreEnCharge(Veterinaire veterinaire, Long signalementId) {
        SignalementMaladie signalement = chargerPourVeterinaire(veterinaire, signalementId);
        try {
            signalement.prendreEnCharge();
        } catch (IllegalStateException e) {
            throw new RegleMetierException(e.getMessage());
        }
        // Le veterinaire ACCEPTE le cas (V14) : l'eleveur l'apprend (audit M6).
        notificationService.creer(signalement.getEleveur(), TypeNotification.ORIENTATION,
                nomPraticien(veterinaire) + " a accepté votre cas",
                nomPraticien(veterinaire) + " a accepté votre signalement sur "
                + signalement.getUnite().getCode() + ". Il vous contactera prochainement.",
                "SIGNALEMENT", signalement.getId());
        return signalementMapper.versReponseVeterinaire(signalement, symptomes(signalement));
    }

    /**
     * Le veterinaire REFUSE le cas qui lui est confie (V14) : indisponible,
     * trop loin, hors de ses competences... L'eleveur est prevenu et choisit
     * un autre veterinaire ; celui-ci ne lui est plus propose pour ce cas.
     */
    @Transactional
    public SignalementReponse refuser(Veterinaire veterinaire, Long signalementId, String motif) {
        SignalementMaladie signalement = chargerPourVeterinaire(veterinaire, signalementId);
        orientationService.refuser(signalement, veterinaire, motif);
        return signalementMapper.versReponseVeterinaire(signalement, symptomes(signalement));
    }

    @Transactional
    public SignalementReponse cloturer(Veterinaire veterinaire, Long signalementId,
                                       PriseEnChargeRequete requete) {
        SignalementMaladie signalement = chargerPourVeterinaire(veterinaire, signalementId);
        if (signalement.getStatut() == StatutSignalement.TRAITE) {
            throw new RegleMetierException("Ce signalement est déjà clôturé.");
        }
        Maladie confirmee = null;
        if (requete.maladieConfirmeeId() != null) {
            confirmee = maladieRepository.findById(requete.maladieConfirmeeId())
                    .orElseThrow(() -> RessourceIntrouvableException.de("Maladie", requete.maladieConfirmeeId()));
            // Une maladie retiree du catalogue, ou d'une autre filiere que
            // l'unite, fausserait les statistiques (audit M7).
            if (!confirmee.isActive()) {
                throw new RegleMetierException(
                        "« " + confirmee.getNom() + " » a été retirée du catalogue : choisissez une autre maladie.");
            }
            if (!confirmee.concerne(signalement.getUnite().getFiliere())) {
                throw new RegleMetierException(
                        "« " + confirmee.getNom() + " » ne touche pas la filière "
                        + signalement.getUnite().getFiliere().getLibelle()
                        + " de " + signalement.getUnite().getCode() + ".");
            }
        }
        signalement.marquerTraite(requete.compteRendu(), confirmee);

        Maladie retenue = confirmee;
        notificationService.creer(signalement.getEleveur(), TypeNotification.ORIENTATION,
                "Réponse du vétérinaire sur " + signalement.getUnite().getCode(),
                nomPraticien(veterinaire) + " a répondu à votre signalement"
                + (retenue == null ? "" : " : " + retenue.getNom())
                + ". Ouvrez-le pour lire son compte rendu.",
                "SIGNALEMENT", signalement.getId());

        return signalementMapper.versReponseVeterinaire(signalement, symptomes(signalement));
    }

    // ── outillage ───────────────────────────────────────────────────────────

    /**
     * « Dr Aminata Sow ». Le titre saisi par l'administration est trop
     * variable (« Docteur vétérinaire »…) pour etre accole au nom.
     */
    static String nomPraticien(Veterinaire v) {
        String nom = v.getNom() == null ? "" : v.getNom().strip();
        return nom.regionMatches(true, 0, "Dr", 0, 2) ? nom : "Dr " + nom;
    }

    private SignalementMaladie chargerPourVeterinaire(Veterinaire veterinaire, Long id) {
        SignalementMaladie signalement = signalementRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Signalement", id));
        if (signalement.getVeterinaire() == null
                || !signalement.getVeterinaire().getId().equals(veterinaire.getId())) {
            throw new AccesRefuseException(
                    "Ce signalement ne vous a pas été confié");
        }
        return signalement;
    }

    /**
     * Verifie que chaque identifiant recu designe un symptome reel.
     *
     * <p>Un identifiant inexistant serait silencieusement ignore par la
     * requete de notation : le diagnostic serait alors calcule sur moins de
     * symptomes que l'eleveur n'en a coche, et le facteur de couverture —
     * qui divise par le nombre de symptomes coches — donnerait un resultat
     * faux sans que rien ne le signale.
     */
    private void exigerSymptomesConnus(Set<Long> ids) {
        if (symptomeRepository.countByIdIn(ids) != ids.size()) {
            throw new RegleMetierException("Un des symptômes sélectionnés est inconnu");
        }
    }

    private List<SymptomeReponse> symptomes(SignalementMaladie signalement) {
        List<Symptome> trouves = symptomeRepository.findAllById(signalement.getSymptomes());
        return maladieMapper.versReponses(trouves);
    }

    /**
     * Les symptomes de TOUTE une page en une requete (audit global G17) :
     * « Mes signalements » et la file du veterinaire en faisaient une par
     * signalement.
     */
    private Map<Long, Symptome> symptomesDe(List<SignalementMaladie> signalements) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        signalements.forEach(s -> ids.addAll(s.getSymptomes()));
        Map<Long, Symptome> parId = new java.util.LinkedHashMap<>();
        if (!ids.isEmpty()) {
            symptomeRepository.findAllById(ids).forEach(sy -> parId.put(sy.getId(), sy));
        }
        return parId;
    }

    private List<SymptomeReponse> symptomes(SignalementMaladie signalement,
                                            Map<Long, Symptome> parId) {
        return maladieMapper.versReponses(parId.values().stream()
                .filter(sy -> signalement.getSymptomes().contains(sy.getId()))
                .toList());
    }
}
