package ml.moncheptel.api.service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import ml.moncheptel.api.dto.reponse.FicheUtilisateurReponse;
import ml.moncheptel.api.dto.reponse.FicheUtilisateurReponse.Activite;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.mapper.UtilisateurMapper;
import ml.moncheptel.api.modele.MouvementCheptel;
import ml.moncheptel.api.modele.SignalementMaladie;
import ml.moncheptel.api.modele.UniteElevage;
import ml.moncheptel.api.modele.Utilisateur;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import ml.moncheptel.api.repository.MouvementCheptelRepository;
import ml.moncheptel.api.repository.SignalementMaladieRepository;
import ml.moncheptel.api.repository.UniteElevageRepository;
import ml.moncheptel.api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ecran « Fiche utilisateur » de l'administration.
 *
 * <p>Service a part plutot qu'une methode de plus dans
 * {@link UtilisateurService} : la fiche lit le cheptel et les signalements,
 * qui ne sont pas l'affaire de la gestion des comptes.
 */
@Service
@Transactional(readOnly = true)
public class FicheAdministrationService {

    /** Le tableau « Activité récente » de la maquette en montre quatre ; dix laissent de la marge. */
    private static final int LIGNES_ACTIVITE = 10;

    private static final ZoneId BAMAKO = ZoneId.of("Africa/Bamako");

    private final UtilisateurRepository utilisateurRepository;
    private final UniteElevageRepository uniteRepository;
    private final SignalementMaladieRepository signalementRepository;
    private final MouvementCheptelRepository mouvementRepository;
    private final UtilisateurMapper mapper;

    public FicheAdministrationService(UtilisateurRepository utilisateurRepository,
                                      UniteElevageRepository uniteRepository,
                                      SignalementMaladieRepository signalementRepository,
                                      MouvementCheptelRepository mouvementRepository,
                                      UtilisateurMapper mapper) {
        this.utilisateurRepository = utilisateurRepository;
        this.uniteRepository = uniteRepository;
        this.signalementRepository = signalementRepository;
        this.mouvementRepository = mouvementRepository;
        this.mapper = mapper;
    }

    public FicheUtilisateurReponse fiche(Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.chercherAvecZone(utilisateurId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Utilisateur", utilisateurId));

        List<UniteElevage> actives = uniteRepository.chercherParProprietaireEtFiliere(
                utilisateurId, null, StatutUnite.ACTIF);
        long tetes = actives.stream().mapToLong(UniteElevage::getEffectifRestant).sum();

        List<SignalementMaladie> signalements =
                signalementRepository.findByEleveurIdOrderByDateSignalementDesc(utilisateurId);
        // « En cours » : ce qu'un veterinaire doit encore traiter. Un cas
        // NOUVEAU (gravite faible ou moyenne, conseil donne a l'eleveur) n'est
        // suivi par personne et ne sera jamais clos : il n'en fait pas partie.
        long enCours = signalements.stream()
                .filter(s -> s.getStatut() == StatutSignalement.ORIENTE
                        || s.getStatut() == StatutSignalement.EN_COURS)
                .count();

        List<Activite> activite = new ArrayList<>();
        for (SignalementMaladie s : signalements) {
            String maladie = s.getMaladieProbable() == null
                    ? "une maladie" : "la maladie « " + s.getMaladieProbable().getNom() + " »";
            String unite = s.getUnite() == null ? "" : " sur " + s.getUnite().getCode();
            activite.add(new Activite(s.getDateSignalement(), "A signalé " + maladie + unite));
        }
        for (MouvementCheptel m : mouvementRepository.chercherActiviteRecente(utilisateurId)) {
            activite.add(new Activite(
                    m.getDateMouvement().atStartOfDay(BAMAKO).toInstant(),
                    m.getType().getLibelle() + " : " + m.getNombreTetes() + " tête"
                    + (m.getNombreTetes() > 1 ? "s" : "") + " — " + m.getUnite().getCode()));
        }
        activite.sort(Comparator.comparing(Activite::date).reversed());

        return new FicheUtilisateurReponse(
                mapper.versProfil(utilisateur),
                utilisateur.isActif(),
                utilisateur.estVerrouille(),
                actives.size(),
                tetes,
                signalements.size(),
                enCours,
                activite.stream().limit(LIGNES_ACTIVITE).toList());
    }
}
