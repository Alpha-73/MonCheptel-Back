package ml.moncheptel.api.service;

import java.util.List;
import ml.moncheptel.api.dto.reponse.ConseilDetailReponse;
import ml.moncheptel.api.dto.reponse.ConseilResumeReponse;
import ml.moncheptel.api.dto.reponse.PageReponse;
import ml.moncheptel.api.dto.requete.ConseilRequete;
import ml.moncheptel.api.dto.requete.ModerationRequete;
import ml.moncheptel.api.exception.AccesRefuseException;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.mapper.ConseilMapper;
import ml.moncheptel.api.modele.Administrateur;
import ml.moncheptel.api.modele.Conseil;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.modele.enumeration.CategorieConseil;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutConseil;
import ml.moncheptel.api.modele.enumeration.TypeNotification;
import ml.moncheptel.api.repository.ConseilRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redaction, moderation et lecture des conseils.
 *
 * <h2>Pourquoi la moderation a ete conservee</h2>
 *
 * <p>Un conseil publie est lu par des eleveurs qui vont l'appliquer a leur
 * troupeau. Un texte errone sur un dosage de vermifuge ne produit pas un
 * commentaire desagreable : il produit des betes mortes. La relecture par
 * l'administration est le seul filtre entre les deux, et c'est peu.
 *
 * <h2>Les transitions d'etat sont sur l'entite</h2>
 *
 * <p>{@code soumettre}, {@code publier}, {@code retirer} vivent sur
 * {@link Conseil} et refusent les enchainements impossibles — publier un
 * brouillon sans moderation, par exemple. Ce service ne fait que les appeler,
 * verifier les droits et notifier.
 */
@Service
@Transactional(readOnly = true)
public class ConseilService {

    private final ConseilRepository conseilRepository;
    private final ConseilMapper mapper;
    private final NotificationService notificationService;

    public ConseilService(ConseilRepository conseilRepository,
                          ConseilMapper mapper,
                          NotificationService notificationService) {
        this.conseilRepository = conseilRepository;
        this.mapper = mapper;
        this.notificationService = notificationService;
    }

    // ── cote eleveur ────────────────────────────────────────────────────────

    public PageReponse<ConseilResumeReponse> lire(Filiere filiere, CategorieConseil categorie,
                                                  Pageable pageable) {
        var page = conseilRepository.chercherPublies(filiere, categorie, pageable);
        return PageReponse.de(page).avec(mapper.versResumes(page.getContent()));
    }

    /**
     * Lecture d'un conseil publie.
     *
     * <p>Passe par {@code chercherPublie}, qui filtre sur le statut dans la
     * requete elle-meme. Charger par identifiant puis tester le statut en
     * Java donnerait le meme resultat, mais laisserait la porte ouverte au
     * jour ou quelqu'un oublierait le test : ici, il n'y a rien a oublier.
     */
    @Transactional
    public ConseilDetailReponse lireUn(Long conseilId) {
        Conseil conseil = conseilRepository.chercherPublie(conseilId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Conseil", conseilId));
        conseilRepository.incrementerLectures(conseilId);
        return mapper.versDetail(conseil, false);
    }

    // ── cote veterinaire ────────────────────────────────────────────────────

    @Transactional
    public ConseilDetailReponse rediger(Veterinaire auteur, ConseilRequete requete) {
        Conseil conseil = new Conseil(requete.titre().strip(), requete.contenu(),
                requete.filiere(), requete.categorie(), auteur);
        return mapper.versDetail(conseilRepository.save(conseil), true);
    }

    @Transactional
    public ConseilDetailReponse modifier(Veterinaire auteur, Long conseilId, ConseilRequete requete) {
        Conseil conseil = chargerPourAuteur(auteur, conseilId);
        // En cours de relecture, le texte ne bouge pas : l'administration
        // publierait sinon un texte different de celui qu'elle a lu.
        if (conseil.getStatut() == StatutConseil.EN_MODERATION) {
            throw new RegleMetierException(
                    "Ce conseil est en cours de relecture : attendez la décision avant de le modifier.");
        }
        // Un conseil publie corrige quitte le fil des eleveurs et repasse en
        // moderation : la relecture porte sur le texte, pas sur l'auteur.
        conseil.reviser();
        conseil.setTitre(requete.titre().strip());
        conseil.setContenu(requete.contenu());
        conseil.setFiliere(requete.filiere());
        conseil.setCategorie(requete.categorie());
        return mapper.versDetail(conseil, true);
    }

    @Transactional
    public ConseilDetailReponse soumettre(Veterinaire auteur, Long conseilId) {
        Conseil conseil = chargerPourAuteur(auteur, conseilId);
        try {
            conseil.soumettre();
        } catch (IllegalStateException e) {
            throw new RegleMetierException(e.getMessage());
        }
        // Audit des notifications (11 octobre) : un conseil soumis n'etait
        // visible que par la pastille du menu de la console. La cloche de
        // l'administration le signale desormais, avec un lien vers la file.
        if (avisAdministration != null) {
            avisAdministration.prevenir(TypeNotification.MODERATION,
                    "Conseil à modérer",
                    "« " + conseil.getTitre() + " », proposé par "
                    + SignalementService.nomPraticien(auteur) + ", attend votre relecture.",
                    "CONSEIL", conseil.getId());
        }
        return mapper.versDetail(conseil, true);
    }

    /** Facultatif : les tests unitaires construisent le service sans lui. */
    private AvisAdministration avisAdministration;

    @Autowired(required = false)
    void setAvisAdministration(AvisAdministration avisAdministration) {
        this.avisAdministration = avisAdministration;
    }

    /** Suppression definitive par son auteur (« Supprimer ce conseil »). */
    @Transactional
    public void supprimer(Veterinaire auteur, Long conseilId) {
        Conseil conseil = chargerPourAuteur(auteur, conseilId);
        // Audit du 29 octobre (point 9) : supprime pendant la relecture, le
        // conseil laissait dans la cloche de l'administration un « Conseil à
        // modérer » qui menait a un conseil disparu. L'auteur garde le droit
        // de le retirer ; ses notifications partent avec lui.
        notificationService.oublierReference("CONSEIL", conseil.getId());
        conseilRepository.delete(conseil);
    }

    public List<ConseilDetailReponse> mesConseils(Veterinaire auteur) {
        return conseilRepository.findByAuteurIdOrderByDateCreationDesc(auteur.getId())
                .stream().map(c -> mapper.versDetail(c, true)).toList();
    }

    // ── cote administration ─────────────────────────────────────────────────

    public List<ConseilDetailReponse> pourAdministration(StatutConseil statut) {
        return conseilRepository.chercherPourAdministration(statut)
                .stream().map(c -> mapper.versDetail(c, true)).toList();
    }

    /** Ecran « Ajouter un conseil » : publication directe, sans moderation. */
    @Transactional
    public ConseilDetailReponse publierDirectement(Administrateur administrateur,
                                                   ConseilRequete requete) {
        Conseil conseil = Conseil.publieParAdministration(requete.titre().strip(),
                requete.contenu(), requete.filiere(), requete.categorie(), administrateur);
        return mapper.versDetail(conseilRepository.save(conseil), true);
    }

    public long compterPourAuteur(Veterinaire auteur, StatutConseil statut) {
        return conseilRepository.countByAuteurIdAndStatut(auteur.getId(), statut);
    }

    public List<ConseilDetailReponse> fileDeModeration() {
        return conseilRepository.chercherEnModeration()
                .stream().map(c -> mapper.versDetail(c, true)).toList();
    }

    /**
     * Publie ou refuse.
     *
     * <p>Le motif est exige au refus — la verification croisee entre deux
     * champs est ici, une annotation de validation ne peut pas lire l'autre.
     * L'auteur est notifie dans les deux cas : un conseil qui reste en
     * moderation sans reponse est un vetrinaire qui n'en ecrira pas un
     * deuxieme.
     */
    @Transactional
    public ConseilDetailReponse moderer(Administrateur moderateur, Long conseilId,
                                        ModerationRequete requete) {
        Conseil conseil = conseilRepository.findById(conseilId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Conseil", conseilId));
        try {
            if (Boolean.TRUE.equals(requete.publier())) {
                conseil.publier(moderateur);
                notifierAuteur(conseil, "Conseil publié",
                        "« " + conseil.getTitre() + " » est en ligne.");
            } else {
                conseil.retirer(moderateur, requete.motif());
                notifierAuteur(conseil, "Conseil à revoir",
                        "« " + conseil.getTitre() + " » : " + requete.motif());
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new RegleMetierException(e.getMessage());
        }
        return mapper.versDetail(conseil, true);
    }

    public long compterEnModeration() {
        return conseilRepository.countByStatut(StatutConseil.EN_MODERATION);
    }

    private void notifierAuteur(Conseil conseil, String titre, String message) {
        if (conseil.getAuteur() != null) {
            notificationService.creer(conseil.getAuteur(), TypeNotification.MODERATION,
                    titre, message, "CONSEIL", conseil.getId());
        }
    }

    private Conseil chargerPourAuteur(Veterinaire auteur, Long conseilId) {
        Conseil conseil = conseilRepository.findById(conseilId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Conseil", conseilId));
        if (conseil.getAuteur() == null || !conseil.getAuteur().getId().equals(auteur.getId())) {
            throw new AccesRefuseException();
        }
        return conseil;
    }
}
