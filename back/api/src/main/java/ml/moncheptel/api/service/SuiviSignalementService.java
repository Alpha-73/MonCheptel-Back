package ml.moncheptel.api.service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import ml.moncheptel.api.dto.reponse.CandidatVeterinaireReponse;
import ml.moncheptel.api.dto.reponse.PageReponse;
import ml.moncheptel.api.dto.reponse.RefusReponse;
import ml.moncheptel.api.dto.reponse.SignalementAdminReponse;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.modele.SignalementMaladie;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;
import ml.moncheptel.api.repository.RefusSignalementRepository;
import ml.moncheptel.api.repository.SignalementMaladieRepository;
import ml.moncheptel.api.repository.VeterinaireRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ecran « Signalements » de l'administration : voir tous les cas, et confier
 * a la main un cas a un veterinaire precis.
 *
 * <p>L'orientation automatique ({@link OrientationService}) reste la regle.
 * L'attribution manuelle sert quand elle ne suffit pas : aucun veterinaire ne
 * couvre la zone, ou l'administration sait qu'un confrere est mieux place.
 */
@Service
@Transactional(readOnly = true)
public class SuiviSignalementService {

    /** Les vues de l'ecran, dans l'ordre des onglets. */
    public enum Vue { A_ATTRIBUER, ORIENTES, EN_SUIVI, TRAITES, TOUS }

    private static final Set<StatutSignalement> OUVERTS =
            EnumSet.of(StatutSignalement.ORIENTE, StatutSignalement.EN_COURS);

    private final SignalementMaladieRepository signalementRepository;
    private final VeterinaireRepository veterinaireRepository;
    private final OrientationService orientationService;

    public SuiviSignalementService(SignalementMaladieRepository signalementRepository,
                                   VeterinaireRepository veterinaireRepository,
                                   OrientationService orientationService) {
        this.signalementRepository = signalementRepository;
        this.veterinaireRepository = veterinaireRepository;
        this.orientationService = orientationService;
    }

    /** Historique des refus (V14). Facultatif : les tests s'en passent. */
    private RefusSignalementRepository refusRepository;

    @Autowired(required = false)
    void setRefusRepository(RefusSignalementRepository refusRepository) {
        this.refusRepository = refusRepository;
    }

    /** Les refus de plusieurs cas, en une requete. */
    private Map<Long, List<RefusReponse>> refusDe(List<Long> ids) {
        Map<Long, List<RefusReponse>> parCas = new HashMap<>();
        if (refusRepository == null || ids.isEmpty()) {
            return parCas;
        }
        refusRepository.findBySignalementIdInOrderByDateRefusDesc(ids).forEach(r ->
                parCas.computeIfAbsent(r.getSignalementId(), k -> new java.util.ArrayList<>())
                        .add(new RefusReponse(r.getVeterinaireNom(), r.getMotif(), r.getDateRefus())));
        return parCas;
    }

    public PageReponse<SignalementAdminReponse> lister(Vue vue, Pageable pageable) {
        boolean tous = vue == Vue.TOUS;
        Set<StatutSignalement> statuts = switch (vue) {
            case A_ATTRIBUER -> EnumSet.of(StatutSignalement.NOUVEAU);
            // Orientes, pas encore ouverts par le veterinaire : ce que compte
            // le tableau de bord (« cas pas encore ouverts »).
            case ORIENTES -> EnumSet.of(StatutSignalement.ORIENTE);
            case EN_SUIVI -> OUVERTS;
            case TRAITES -> EnumSet.of(StatutSignalement.TRAITE);
            case TOUS -> EnumSet.allOf(StatutSignalement.class);
        };
        var page = signalementRepository
                .chercherPourAdministration(tous, statuts, vue == Vue.A_ATTRIBUER, pageable);
        Map<Long, List<RefusReponse>> refus = refusDe(
                page.getContent().stream().map(SignalementMaladie::getId).toList());
        return PageReponse.de(page.map(s -> versReponse(s, refus.getOrDefault(s.getId(), List.of()))));
    }

    /** Les veterinaires en service, les mieux places d'abord. */
    public List<CandidatVeterinaireReponse> candidats(Long signalementId) {
        SignalementMaladie s = charger(signalementId);
        Long zoneId = s.getEleveur().getZone() == null ? null : s.getEleveur().getZone().getId();
        Long maladieId = s.getMaladieProbable() == null ? null : s.getMaladieProbable().getId();
        Long actuelId = s.getVeterinaire() == null ? null : s.getVeterinaire().getId();
        // V14 : l'administration peut passer outre un refus, mais elle le voit.
        List<Long> ontRefuse = refusRepository == null
                ? List.of() : refusRepository.veterinairesAyantRefuse(signalementId);
        // Un seul « group by » pour les cas ouverts, pas deux count par veterinaire.
        Map<Long, Long> ouverts = new HashMap<>();
        for (Object[] ligne : signalementRepository.compterOuvertsParVeterinaire(OUVERTS)) {
            ouverts.put((Long) ligne[0], ((Number) ligne[1]).longValue());
        }

        return veterinaireRepository.findAll().stream()
                .filter(Veterinaire::isActif)
                .map(v -> new CandidatVeterinaireReponse(
                        v.getId(),
                        v.getNom(),
                        v.getZoneIntervention() == null ? null : v.getZoneIntervention().getNom(),
                        maladieId != null && v.getCompetences().stream()
                                .anyMatch(c -> c.getMaladie().getId().equals(maladieId)),
                        zoneId != null && v.getZoneIntervention() != null
                                && v.getZoneIntervention().getId().equals(zoneId),
                        v.isDisponible(),
                        ouverts.getOrDefault(v.getId(), 0L),
                        Objects.equals(v.getId(), actuelId),
                        ontRefuse.contains(v.getId())))
                // Les indisponibles descendent en bas de liste, quels que soient
                // leur zone et leurs competences : ils ne prendront pas le cas.
                .sorted(Comparator
                        .comparing((CandidatVeterinaireReponse c) -> !c.disponible())
                        .thenComparing(CandidatVeterinaireReponse::aRefuse)
                        .thenComparing(c -> !(c.competent() && c.memeZone()))
                        .thenComparing(c -> !c.memeZone())
                        .thenComparing(c -> !c.competent())
                        .thenComparingLong(CandidatVeterinaireReponse::casOuverts)
                        .thenComparing(CandidatVeterinaireReponse::nom))
                .toList();
    }

    @Transactional
    public SignalementAdminReponse attribuer(Long signalementId, Long veterinaireId) {
        SignalementMaladie s = charger(signalementId);
        Veterinaire veterinaire = veterinaireRepository.findById(veterinaireId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Vétérinaire", veterinaireId));
        if (s.getStatut() == StatutSignalement.TRAITE) {
            throw new RegleMetierException("Ce signalement est déjà traité : il ne se réattribue plus.");
        }
        if (!veterinaire.isActif()) {
            throw new RegleMetierException("Ce vétérinaire est désactivé : réactivez son compte d'abord.");
        }
        if (s.getVeterinaire() != null && s.getVeterinaire().getId().equals(veterinaireId)) {
            throw new RegleMetierException("Ce cas est déjà suivi par " + veterinaire.getNom() + ".");
        }
        orientationService.attribuer(s, veterinaire);
        return versReponse(s, refusDe(List.of(s.getId())).getOrDefault(s.getId(), List.of()));
    }

    private SignalementMaladie charger(Long id) {
        return signalementRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Signalement", id));
    }

    private static SignalementAdminReponse versReponse(SignalementMaladie s, List<RefusReponse> refus) {
        boolean aSuivre = s.getGravite() == NiveauGravite.ELEVE
                || (s.getMaladieProbable() != null && s.getMaladieProbable().isOrienteVersVeterinaire());
        return new SignalementAdminReponse(
                s.getId(),
                s.getEleveur().getId(),
                s.getEleveur().getNom(),
                s.getEleveur().getZone() == null ? null : s.getEleveur().getZone().getNom(),
                s.getUnite().getCode(),
                s.getMaladieProbable() == null ? null : s.getMaladieProbable().getNom(),
                s.getMaladieConfirmee() == null ? null : s.getMaladieConfirmee().getNom(),
                s.getGravite(),
                s.getStatut(),
                aSuivre && s.getStatut() == StatutSignalement.NOUVEAU,
                s.getVeterinaire() == null ? null : s.getVeterinaire().getId(),
                s.getVeterinaire() == null ? null : s.getVeterinaire().getNom(),
                s.getDateSignalement(),
                s.getDateOrientation(),
                s.getDatePriseEnCharge(),
                s.getDateTraitement(),
                refus);
    }
}
