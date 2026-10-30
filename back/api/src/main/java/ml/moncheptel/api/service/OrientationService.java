package ml.moncheptel.api.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.modele.RefusSignalement;
import ml.moncheptel.api.modele.SignalementMaladie;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;
import ml.moncheptel.api.modele.enumeration.TypeNotification;
import ml.moncheptel.api.repository.RefusSignalementRepository;
import ml.moncheptel.api.repository.SignalementMaladieRepository;
import ml.moncheptel.api.repository.VeterinaireRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Qui suit un signalement : c'est l'ELEVEUR qui choisit son veterinaire.
 *
 * <h3>Aucun veterinaire n'est privilegie</h3>
 *
 * <p>Avant, le serveur choisissait un seul veterinaire (le moins charge, a
 * egalite le premier par ordre alphabetique). Desormais, l'eleveur voit
 * TOUS les veterinaires de SA zone qui traitent la maladie probable, dans un
 * ordre ALEATOIRE tire a chaque affichage, et touche celui a qui il confie le
 * cas. Aucune position n'est reservee a un praticien.
 *
 * <ul>
 *   <li>Toujours la zone de l'eleveur : un veterinaire d'une autre zone n'est
 *       jamais propose ni accepte.</li>
 *   <li>Maladie reconnue : les veterinaires de la zone competents pour elle.</li>
 *   <li>Aucune maladie reconnue : les veterinaires de la zone qui traitent au
 *       moins une maladie de la filiere de l'unite.</li>
 *   <li>Un veterinaire indisponible est affiche (grise) mais ne peut pas
 *       recevoir le cas.</li>
 * </ul>
 *
 * <p>Si personne ne convient, le cas reste NOUVEAU et l'administration peut
 * l'attribuer a la main (« cas graves sans vétérinaire »).
 */
@Service
@Transactional
public class OrientationService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(OrientationService.class);

    /** Ordre d'affichage tire au hasard a chaque lecture. */
    private static final Random HASARD = new SecureRandom();

    private final SignalementMaladieRepository signalementRepository;
    private final VeterinaireRepository veterinaireRepository;
    private final NotificationService notificationService;

    public OrientationService(SignalementMaladieRepository signalementRepository,
                              VeterinaireRepository veterinaireRepository,
                              NotificationService notificationService) {
        this.signalementRepository = signalementRepository;
        this.veterinaireRepository = veterinaireRepository;
        this.notificationService = notificationService;
    }

    // ── collaborateurs facultatifs (V14) ────────────────────────────────────
    // Par mutateur, comme AvisAdministration ailleurs : les tests unitaires
    // qui construisent ce service a la main n'en ont pas besoin.

    private RefusSignalementRepository refusRepository;
    private AvisAdministration avisAdministration;

    /** Delai de reponse d'un cas urgent (gravite elevee ou maladie qui oriente). */
    private Duration delaiUrgent = Duration.ofHours(2);
    /** Delai de reponse des autres cas. */
    private Duration delaiNormal = Duration.ofHours(12);

    @Autowired(required = false)
    void setRefusRepository(RefusSignalementRepository refusRepository) {
        this.refusRepository = refusRepository;
    }

    @Autowired(required = false)
    void setAvisAdministration(AvisAdministration avisAdministration) {
        this.avisAdministration = avisAdministration;
    }

    @Autowired
    void setDelais(@Value("${moncheptel.orientation.delai-urgent:PT2H}") Duration urgent,
                   @Value("${moncheptel.orientation.delai-normal:PT12H}") Duration normal) {
        this.delaiUrgent = urgent;
        this.delaiNormal = normal;
    }

    /** Delai de reponse attendu pour ce cas. */
    Duration delaiDe(SignalementMaladie s) {
        return s.doitEtreOriente() ? delaiUrgent : delaiNormal;
    }

    /**
     * Les veterinaires que l'eleveur peut choisir pour ce signalement, dans un
     * ordre aleatoire different a chaque appel.
     */
    @Transactional(readOnly = true)
    public List<Veterinaire> candidats(SignalementMaladie signalement) {
        Long zoneId = signalement.getEleveur().getZone() == null
                ? null : signalement.getEleveur().getZone().getId();
        if (zoneId == null) {
            return List.of();
        }
        List<Veterinaire> trouves;
        if (signalement.getMaladieProbable() != null) {
            trouves = veterinaireRepository.chercherCompetents(
                    signalement.getMaladieProbable().getId(), zoneId);
        } else if (signalement.getUnite() != null) {
            trouves = veterinaireRepository.chercherActifsDansZoneParFiliere(
                    zoneId, signalement.getUnite().getFiliere());
        } else {
            trouves = List.of();
        }
        // V14 : un veterinaire qui a refuse CE cas ne le revoit pas.
        List<Long> ontRefuse = refusRepository == null || signalement.getId() == null
                ? List.of() : refusRepository.veterinairesAyantRefuse(signalement.getId());
        List<Veterinaire> melanges = new ArrayList<>(trouves.stream()
                .filter(Veterinaire::isActif)
                .filter(v -> !ontRefuse.contains(v.getId()))
                .filter(v -> v.getZoneIntervention() != null
                        && zoneId.equals(v.getZoneIntervention().getId()))
                .toList());
        Collections.shuffle(melanges, HASARD);
        return melanges;
    }

    /**
     * L'eleveur confie son cas au veterinaire choisi dans la liste.
     *
     * <p>Le choix est revalide ici : meme zone que l'eleveur, competent,
     * actif et disponible. Un cas deja pris en main ou cloture ne change plus
     * de veterinaire. Un cas oriente mais pas encore ouvert peut changer de
     * veterinaire : l'ancien est prevenu.
     */
    public void choisirParEleveur(SignalementMaladie signalement, Long veterinaireId) {
        if (signalement.getStatut() == StatutSignalement.EN_COURS
                || signalement.getStatut() == StatutSignalement.TRAITE) {
            throw new RegleMetierException(
                    "Ce signalement est déjà suivi par un vétérinaire : il ne peut plus changer de vétérinaire.");
        }
        Veterinaire choisi = candidats(signalement).stream()
                .filter(v -> v.getId().equals(veterinaireId))
                .findFirst()
                .orElseThrow(() -> new RegleMetierException(
                        "Ce vétérinaire ne peut pas recevoir ce signalement : "
                        + "il doit exercer dans votre zone et traiter cette maladie."));
        if (!choisi.isDisponible()) {
            throw new RegleMetierException(
                    "Ce vétérinaire est indisponible pour le moment : choisissez-en un autre.");
        }
        Veterinaire ancien = signalement.getVeterinaire();
        if (ancien != null && ancien.getId().equals(choisi.getId())) {
            return;
        }
        if (ancien != null) {
            signalement.detacherDuVeterinaire();
            notificationService.creer(ancien, TypeNotification.ORIENTATION,
                    "Cas confié à un confrère",
                    signalement.getEleveur().getNom() + " a confié son signalement sur "
                    + signalement.getUnite().getCode() + " à un autre vétérinaire.",
                    "SIGNALEMENT", signalement.getId());
        }
        confier(signalement, choisi);
    }

    /**
     * Attribution manuelle par l'administration : le cas passe a ce
     * veterinaire, quel que soit son statut ouvert. L'ancien veterinaire,
     * s'il y en avait un, est prevenu que le cas lui est retire.
     */
    public void attribuer(SignalementMaladie signalement, Veterinaire veterinaire) {
        Veterinaire ancien = signalement.getVeterinaire();
        if (ancien != null) {
            signalement.detacherDuVeterinaire();
            notificationService.creer(ancien, TypeNotification.ORIENTATION,
                    "Cas réattribué",
                    "L'administration a confié le signalement de " + signalement.getEleveur().getNom()
                    + " sur " + signalement.getUnite().getCode() + " à un confrère.",
                    "SIGNALEMENT", signalement.getId());
        }
        confier(signalement, veterinaire);
        // Audit des notifications (11 octobre) : l'eleveur n'etait pas prevenu
        // qu'un veterinaire avait ete choisi POUR lui. Quand c'est lui qui
        // choisit (choisirParEleveur), il le sait deja : pas de notification.
        notificationService.creer(signalement.getEleveur(), TypeNotification.ORIENTATION,
                "Un vétérinaire a reçu votre signalement",
                "L'administration a confié votre signalement sur " + signalement.getUnite().getCode()
                + " à " + SignalementService.nomPraticien(veterinaire)
                + ". Vous serez prévenu dès qu'il l'aura accepté.",
                "SIGNALEMENT", signalement.getId());
    }

    private void confier(SignalementMaladie signalement, Veterinaire veterinaire) {
        signalement.orienterVers(veterinaire);
        String maladie = signalement.getMaladieProbable() == null
                ? "maladie non identifiée" : signalement.getMaladieProbable().getNom();
        notificationService.creer(veterinaire, TypeNotification.ORIENTATION,
                "Nouveau signalement — " + maladie,
                signalement.getEleveur().getNom() + " a signalé des symptômes sur "
                + signalement.getUnite().getCode()
                + " (" + signalement.getTrancheEffectif().getLibelle() + " sujets touchés)",
                "SIGNALEMENT", signalement.getId());
    }

    /** Statuts d'un cas qui occupe encore son veterinaire. */
    private static final List<StatutSignalement> OUVERTS =
            List.of(StatutSignalement.ORIENTE, StatutSignalement.EN_COURS);

    /**
     * Le veterinaire rend ses cas.
     *
     * <ul>
     *   <li>{@code aussiEnCours} vrai : il part (desactive, supprime) et rend
     *       TOUS ses cas ouverts, pris en main ou non ;</li>
     *   <li>faux : il a change de zone, perdu une competence ou s'est declare
     *       indisponible. Il ne rend que les cas pas encore ouverts auxquels
     *       il ne CONVIENT plus.</li>
     * </ul>
     *
     * <p>Un cas rendu redevient NOUVEAU et l'eleveur est prevenu pour choisir
     * un autre veterinaire.
     */
    public void reprendreCasDe(Long veterinaireId, boolean aussiEnCours) {
        List<StatutSignalement> statuts = aussiEnCours
                ? OUVERTS
                : List.of(StatutSignalement.ORIENTE);
        List<SignalementMaladie> cas =
                signalementRepository.findByVeterinaireIdAndStatutIn(veterinaireId, statuts);
        int repris = 0;
        for (SignalementMaladie s : cas) {
            Veterinaire actuel = s.getVeterinaire();
            if (!aussiEnCours && actuel != null && convientEncore(s, actuel)) {
                continue;
            }
            boolean etaitAccepte = s.getStatut() == StatutSignalement.EN_COURS;
            s.detacherDuVeterinaire();
            // Audit du 29 octobre (point 8) : un cas urgent DEJA accepte qui
            // perd son veterinaire est signale a l'administration tout de
            // suite, sans attendre la relance.
            if (etaitAccepte && s.doitEtreOriente()) {
                prevenirAdministration("Cas grave sans vétérinaire",
                        s.getUnite().getCode() + " (" + s.getEleveur().getNom() + ") : le vétérinaire qui suivait"
                        + " ce cas n'est plus en service. Vérifiez qu'un confrère le reprend.", s);
            }
            // L'eleveur choisit lui-meme un autre veterinaire de sa zone.
            notificationService.creer(s.getEleveur(), TypeNotification.ORIENTATION,
                    "Choisissez un autre vétérinaire",
                    "Le vétérinaire de votre signalement sur " + s.getUnite().getCode()
                    + " n'est plus disponible. Ouvrez le signalement pour en choisir un autre.",
                    "SIGNALEMENT", s.getId());
            repris++;
        }
        if (repris > 0) {
            JOURNAL.info("{} cas repris au veterinaire {}", repris, veterinaireId);
        }
    }

    /**
     * Le veterinaire REFUSE un cas qui lui est confie (V14).
     *
     * <p>Seulement tant qu'il ne l'a pas accepte : un cas accepte se cloture.
     * Le refus est garde (ce veterinaire n'est plus propose pour ce cas, et
     * l'administration le voit), le cas redevient NOUVEAU et l'eleveur est
     * prevenu pour en choisir un autre. Si plus personne ne peut le prendre
     * et qu'il est urgent, l'administration est prevenue pour l'attribuer.
     */
    public void refuser(SignalementMaladie signalement, Veterinaire veterinaire, String motif) {
        if (signalement.getStatut() != StatutSignalement.ORIENTE) {
            throw new RegleMetierException(
                    signalement.getStatut() == StatutSignalement.TRAITE
                    ? "Ce cas est déjà clôturé."
                    : signalement.getStatut() == StatutSignalement.EN_COURS
                    ? "Vous avez accepté ce cas : clôturez-le avec votre compte rendu."
                    : "Ce cas ne vous est plus confié.");
        }
        String nom = SignalementService.nomPraticien(veterinaire);
        if (refusRepository != null) {
            refusRepository.save(new RefusSignalement(
                    signalement.getId(), veterinaire.getId(), nom, motif));
        }
        signalement.detacherDuVeterinaire();
        String raison = motif == null || motif.isBlank() ? "" : " Motif : « " + motif.strip() + " ».";
        notificationService.creer(signalement.getEleveur(), TypeNotification.ORIENTATION,
                nom + " ne peut pas prendre votre cas",
                "Votre signalement sur " + signalement.getUnite().getCode() + " n'a pas été accepté."
                + raison + " Ouvrez-le pour choisir un autre vétérinaire.",
                "SIGNALEMENT", signalement.getId());
        if (signalement.doitEtreOriente() && candidats(signalement).isEmpty()) {
            prevenirAdministration("Cas grave sans vétérinaire",
                    signalement.getUnite().getCode() + " (" + signalement.getEleveur().getNom() + ") : "
                    + nom + " a refusé le cas et aucun autre vétérinaire de la zone ne peut le prendre."
                    + " Attribuez-le depuis la console.", signalement);
        }
    }

    /**
     * L'eleveur a change de zone (audit du 29 octobre, point 10) : un cas
     * confie mais pas encore accepte par un veterinaire de l'ancienne zone
     * lui est retire. Le veterinaire et l'eleveur sont prevenus.
     */
    public void reprendreCasHorsZone(Long eleveurId) {
        for (SignalementMaladie s : signalementRepository
                .findByEleveurIdOrderByDateSignalementDesc(eleveurId)) {
            Veterinaire v = s.getVeterinaire();
            if (s.getStatut() != StatutSignalement.ORIENTE || v == null || convientEncore(s, v)) {
                continue;
            }
            s.detacherDuVeterinaire();
            notificationService.creer(v, TypeNotification.ORIENTATION,
                    "Cas retiré",
                    s.getEleveur().getNom() + " a changé de zone : son signalement sur "
                    + s.getUnite().getCode() + " ne vous est plus confié.",
                    "SIGNALEMENT", s.getId());
            notificationService.creer(s.getEleveur(), TypeNotification.ORIENTATION,
                    "Choisissez un vétérinaire de votre zone",
                    "Votre signalement sur " + s.getUnite().getCode()
                    + " attend un vétérinaire de votre nouvelle zone.",
                    "SIGNALEMENT", s.getId());
        }
    }

    /**
     * Relances des cas sans reponse (V14), appelee par une tache planifiee.
     *
     * <ul>
     *   <li>Cas confie, pas de reponse apres le delai (2 h urgent, 12 h
     *       sinon) : rappel au veterinaire.</li>
     *   <li>Apres le double du delai : l'eleveur est invite a attendre ou a
     *       changer de veterinaire ; l'administration est prevenue si le cas
     *       est urgent.</li>
     *   <li>Cas urgent reste sans veterinaire apres le delai urgent :
     *       l'eleveur est invite a en choisir un, l'administration prevenue.</li>
     * </ul>
     *
     * <p>Chaque relance n'est envoyee qu'une fois par periode d'attente : les
     * dates sont remises a zero a chaque nouvelle orientation ou retour en
     * attente.
     *
     * @return le nombre de relances envoyees
     */
    public int relancer(Instant maintenant) {
        int envoyees = 0;
        for (SignalementMaladie s : signalementRepository.chercherARelancer()) {
            if (s.getUnite() == null || s.getUnite().getStatut().estSortie()) {
                continue;
            }
            Duration delai = delaiDe(s);
            Duration attente = Duration.between(s.debutAttente(), maintenant);
            if (s.getStatut() == StatutSignalement.ORIENTE && s.getVeterinaire() != null) {
                String nom = SignalementService.nomPraticien(s.getVeterinaire());
                if (attente.compareTo(delai.multipliedBy(2)) >= 0) {
                    notificationService.creer(s.getEleveur(), TypeNotification.ORIENTATION,
                            nom + " n'a pas encore répondu",
                            "Votre signalement sur " + s.getUnite().getCode() + " attend toujours sa réponse."
                            + " Vous pouvez l'appeler, attendre, ou choisir un autre vétérinaire.",
                            "SIGNALEMENT", s.getId());
                    if (s.doitEtreOriente()) {
                        prevenirAdministration("Cas grave sans réponse",
                                s.getUnite().getCode() + " (" + s.getEleveur().getNom() + ") : " + nom
                                + " n'a pas répondu depuis " + heures(attente) + ".", s);
                    }
                    s.marquerRelanceEleveur(maintenant);
                    envoyees++;
                } else if (attente.compareTo(delai) >= 0 && s.getDateRelanceVeterinaire() == null) {
                    notificationService.creer(s.getVeterinaire(), TypeNotification.ORIENTATION,
                            "Un éleveur attend votre réponse",
                            s.getEleveur().getNom() + " vous a confié son signalement sur "
                            + s.getUnite().getCode() + " il y a " + heures(attente)
                            + ". Acceptez-le ou refusez-le pour qu'il puisse en choisir un autre.",
                            "SIGNALEMENT", s.getId());
                    s.marquerRelanceVeterinaire(maintenant);
                    envoyees++;
                }
            } else if (s.getStatut() == StatutSignalement.NOUVEAU && s.doitEtreOriente()
                    && attente.compareTo(delaiUrgent) >= 0) {
                if (!candidats(s).isEmpty()) {
                    notificationService.creer(s.getEleveur(), TypeNotification.ORIENTATION,
                            "Choisissez un vétérinaire",
                            "Votre signalement sur " + s.getUnite().getCode()
                            + " est grave et n'est suivi par personne. Ouvrez-le pour le confier à un vétérinaire.",
                            "SIGNALEMENT", s.getId());
                }
                prevenirAdministration("Cas grave sans vétérinaire",
                        s.getUnite().getCode() + " (" + s.getEleveur().getNom() + ") attend un vétérinaire depuis "
                        + heures(attente) + ". Attribuez-le depuis la console.", s);
                s.marquerRelanceEleveur(maintenant);
                envoyees++;
            }
        }
        return envoyees;
    }

    private void prevenirAdministration(String titre, String corps, SignalementMaladie s) {
        if (avisAdministration != null) {
            avisAdministration.prevenir(TypeNotification.ORIENTATION, titre, corps,
                    "SIGNALEMENT", s.getId());
        }
    }

    /** « 3 h », « 1 j 4 h ». */
    static String heures(Duration d) {
        long h = Math.max(1, d.toHours());
        return h < 24 ? h + " h" : (h / 24) + " j" + (h % 24 == 0 ? "" : " " + (h % 24) + " h");
    }

    /**
     * Le veterinaire peut-il garder ce cas pas encore ouvert : actif,
     * disponible, de la zone de l'eleveur, et competent pour la maladie (ou,
     * sans maladie reconnue, pour la filiere de l'unite).
     */
    static boolean convientEncore(SignalementMaladie s, Veterinaire v) {
        if (!v.isActif() || !v.isDisponible()) {
            return false;
        }
        var zoneEleveur = s.getEleveur() == null ? null : s.getEleveur().getZone();
        var intervention = v.getZoneIntervention();
        if (zoneEleveur == null || intervention == null
                || !intervention.getId().equals(zoneEleveur.getId())) {
            return false;
        }
        if (s.getMaladieProbable() != null) {
            return v.traite(s.getMaladieProbable());
        }
        return s.getUnite() == null || v.traiteFiliere(s.getUnite().getFiliere());
    }

    @Transactional(readOnly = true)
    public long compterSansVeterinaire() {
        return signalementRepository.compterSansVeterinaire();
    }
}
