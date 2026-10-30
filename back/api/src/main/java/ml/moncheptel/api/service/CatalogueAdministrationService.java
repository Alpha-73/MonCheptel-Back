package ml.moncheptel.api.service;

import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ml.moncheptel.api.dto.reponse.EtapeProtocoleReponse;
import ml.moncheptel.api.dto.requete.EtapeProtocoleRequete;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.modele.CalendrierSanitaire;
import ml.moncheptel.api.modele.TypeElevage;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.modele.enumeration.TypeEvenement;
import ml.moncheptel.api.repository.CalendrierSanitaireRepository;
import ml.moncheptel.api.repository.TypeElevageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ce que la console d'administration change dans le catalogue, au-dela de
 * {@link ReferentielService} : le protocole sanitaire d'un type d'elevage et
 * le retrait d'une maladie avec les competences declarees dessus.
 */
@Service
@Transactional(readOnly = true)
public class CatalogueAdministrationService {

    private final TypeElevageRepository typeElevageRepository;
    private final CalendrierSanitaireRepository calendrierRepository;
    private final ReferentielService referentielService;
    private final EntityManager em;

    public CatalogueAdministrationService(TypeElevageRepository typeElevageRepository,
                                          CalendrierSanitaireRepository calendrierRepository,
                                          ReferentielService referentielService,
                                          EntityManager em) {
        this.typeElevageRepository = typeElevageRepository;
        this.calendrierRepository = calendrierRepository;
        this.referentielService = referentielService;
        this.em = em;
    }

    // ── protocole sanitaire ─────────────────────────────────────────────────

    public List<EtapeProtocoleReponse> protocole(Long typeElevageId) {
        exigerType(typeElevageId);
        return calendrierRepository.findByTypeElevageIdOrderByJourDuCycleAsc(typeElevageId)
                .stream().map(CatalogueAdministrationService::versReponse).toList();
    }

    /**
     * Remplace le protocole d'un type d'elevage.
     *
     * <p>Les etapes sont rapprochees par identifiant : une etape gardee est
     * mise a jour, une etape nouvelle est creee, une etape absente est
     * supprimee.
     *
     * <p>Le protocole sert a la CREATION d'un elevage : il y engendre les
     * rappels. Les rappels deja engendres ne sont pas recrees. Ceux d'une
     * etape supprimee sont detaches (calendrier nul) avant la suppression :
     * ils deviennent des rappels libres et gardent leur libelle, leur acte et
     * leur echeance — l'eleveur ne perd pas un rappel qu'il attendait.
     */
    @Transactional
    public List<EtapeProtocoleReponse> remplacerProtocole(Long typeElevageId, List<EtapeProtocoleRequete> etapes) {
        TypeElevage type = exigerType(typeElevageId);
        for (EtapeProtocoleRequete e : etapes) {
            if (e.typeEvenement() == TypeEvenement.MALADIE) {
                throw new RegleMetierException("Un protocole programme des vaccinations, des traitements "
                        + "ou des consultations, pas une maladie.");
            }
            if (type.getModeEvaluation() == ModeEvaluation.PAR_PERIODE && type.getDureeCycleJours() != null
                    && e.jourDuCycle() > type.getDureeCycleJours()) {
                throw new RegleMetierException("« " + e.libelle().strip() + " » tombe au jour " + e.jourDuCycle()
                        + ", après la fin du cycle (" + type.getDureeCycleJours() + " jours).");
            }
        }

        Map<Long, CalendrierSanitaire> existantes = new HashMap<>();
        for (CalendrierSanitaire c : calendrierRepository.findByTypeElevageIdOrderByJourDuCycleAsc(typeElevageId)) {
            existantes.put(c.getId(), c);
        }
        Set<Long> gardees = new HashSet<>();
        int ordre = 0;
        for (EtapeProtocoleRequete e : etapes) {
            CalendrierSanitaire ligne;
            if (e.id() != null) {
                ligne = existantes.get(e.id());
                if (ligne == null || !gardees.add(e.id())) {
                    throw new RegleMetierException("Étape de protocole inconnue pour ce type d'élevage : " + e.id());
                }
                ligne.setLibelle(e.libelle().strip());
                ligne.setTypeEvenement(e.typeEvenement());
                ligne.setJourDuCycle(e.jourDuCycle());
            } else {
                ligne = new CalendrierSanitaire(type, e.libelle().strip(), e.typeEvenement(), e.jourDuCycle(), ordre);
            }
            ligne.setObligatoire(e.obligatoire());
            ligne.setOrdre(ordre++);
            calendrierRepository.save(ligne);
        }

        List<Long> supprimees = existantes.keySet().stream().filter(id -> !gardees.contains(id)).toList();
        if (!supprimees.isEmpty()) {
            em.createQuery("update RappelSanitaire r set r.calendrier = null where r.calendrier.id in :ids")
                    .setParameter("ids", supprimees).executeUpdate();
            calendrierRepository.deleteAllById(supprimees);
        }
        calendrierRepository.flush();
        return protocole(typeElevageId);
    }

    // ── maladies ────────────────────────────────────────────────────────────

    /**
     * Retire une maladie du catalogue ({@link ReferentielService#retirerMaladie})
     * et supprime les competences declarees dessus : l'orientation ne s'appuie
     * plus sur elle, et la fiche d'un veterinaire ne doit pas afficher une
     * competence sur une maladie qui n'existe plus a l'ecran. Une remise au
     * catalogue ne les recree pas : on les redeclare.
     */
    @Transactional
    public int retirerMaladie(Long maladieId) {
        referentielService.retirerMaladie(maladieId);
        return em.createQuery("delete from Competence c where c.maladie.id = :id")
                .setParameter("id", maladieId).executeUpdate();
    }

    private TypeElevage exigerType(Long id) {
        return typeElevageRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Type d'élevage", id));
    }

    private static EtapeProtocoleReponse versReponse(CalendrierSanitaire c) {
        return new EtapeProtocoleReponse(c.getId(), c.getLibelle(), c.getTypeEvenement(),
                c.getJourDuCycle(), c.isObligatoire());
    }
}
