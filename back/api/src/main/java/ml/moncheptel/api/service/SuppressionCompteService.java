package ml.moncheptel.api.service;

import jakarta.persistence.EntityManager;
import java.util.List;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.Utilisateur;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suppression definitive d'un compte eleveur ou veterinaire par
 * l'administration.
 *
 * <h3>Ce qui part avec le compte</h3>
 *
 * <ul>
 *   <li><b>Eleveur</b> : tout ce qui lui appartient. Ses unites (lots et
 *       animaux), leurs mouvements, leurs evenements et rappels sanitaires,
 *       ses operations financieres et ses signalements. Rien de cela n'a de
 *       sens sans lui, et le garder ferait mentir les statistiques.</li>
 *   <li><b>Veterinaire</b> : ses competences et ses conseils non publies.
 *       Ses conseils publies restent, au nom de l'administration. Ses cas
 *       ouverts repartent vers un confrere ({@link OrientationService}). Ses
 *       actes au carnet sanitaire restent, sans auteur.</li>
 * </ul>
 *
 * <h3>Pourquoi du SQL natif, dans cet ordre</h3>
 *
 * <p>Les cles etrangeres de la base ne sont pas toutes en cascade, et elles
 * ne doivent pas le devenir : supprimer une unite par erreur ne doit pas
 * effacer un historique en silence. L'ordre ci-dessous retire les enfants
 * avant les parents, si bien que chaque instruction respecte toutes les
 * contraintes. Il marche a l'identique sur MySQL et sur la base H2 des tests.
 *
 * <p>Un administrateur ne se supprime pas ici : la console n'en gere pas.
 */
@Service
@Transactional
public class SuppressionCompteService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(SuppressionCompteService.class);

    private final UtilisateurRepository utilisateurRepository;
    private final EntityManager em;
    private final OrientationService orientation;

    public SuppressionCompteService(UtilisateurRepository utilisateurRepository, EntityManager em,
                                    OrientationService orientation) {
        this.utilisateurRepository = utilisateurRepository;
        this.em = em;
        this.orientation = orientation;
    }

    public void supprimer(Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Utilisateur", utilisateurId));

        boolean eleveur = utilisateur instanceof Eleveur;
        boolean veterinaire = utilisateur instanceof Veterinaire;
        if (!eleveur && !veterinaire) {
            throw new RegleMetierException("Seuls les comptes éleveur et vétérinaire peuvent être supprimés.");
        }

        // Ses cas ouverts repartent vers un confrere AVANT qu'il disparaisse.
        if (veterinaire) {
            orientation.reprendreCasDe(utilisateurId, true);
        }

        // Ce que Hibernate garde en memoire ne doit pas etre reecrit apres
        // les suppressions.
        em.flush();
        em.clear();

        if (eleveur) {
            supprimerDonneesEleveur(utilisateurId);
        } else {
            detacherVeterinaire(utilisateurId);
        }
        supprimerCompte(utilisateurId, eleveur ? "eleveur" : "veterinaire");
        em.clear();

        JOURNAL.info("Compte {} supprime ({})", utilisateurId, eleveur ? "eleveur" : "veterinaire");
    }

    private void supprimerDonneesEleveur(Long id) {
        String unites = "(select u.id from unite_elevage u where u.proprietaire_id = :id)";
        String signalements = "(select s.id from signalement_maladie s where s.eleveur_id = :id"
                + " or s.unite_id in " + unites + ")";
        executer("delete from signalement_symptome where signalement_id in " + signalements, id);
        executer("delete from signalement_maladie where eleveur_id = :id or unite_id in " + unites, id);
        executer("delete from operation_financiere where eleveur_id = :id or unite_id in " + unites, id);
        executer("delete from rappel_sanitaire where unite_id in " + unites, id);
        executer("delete from evenement_sanitaire where unite_id in " + unites, id);
        executer("delete from mouvement_cheptel where unite_id in " + unites, id);
        executer("delete from animal where unite_id in " + unites, id);
        executer("delete from lot where unite_id in " + unites, id);
        executer("delete from unite_elevage where proprietaire_id = :id", id);
        // Un acte qu'il aurait saisi ailleurs reste, sans auteur.
        executer("update evenement_sanitaire set auteur_id = null where auteur_id = :id", id);
        executer("update mouvement_cheptel set auteur_id = null where auteur_id = :id", id);
        executer("delete from eleveur_filiere where eleveur_id = :id", id);
    }

    private void detacherVeterinaire(Long id) {
        // Filet de securite : un cas ouvert que personne n'a pu reprendre
        // revient en attente ; le tableau de bord le compte.
        executer("update signalement_maladie set statut = 'NOUVEAU', date_orientation = null,"
                + " date_prise_en_charge = null"
                + " where veterinaire_id = :id and statut in ('ORIENTE', 'EN_COURS')", id);
        executer("update signalement_maladie set veterinaire_id = null where veterinaire_id = :id", id);
        executer("update evenement_sanitaire set veterinaire_id = null where veterinaire_id = :id", id);
        executer("update evenement_sanitaire set auteur_id = null where auteur_id = :id", id);
        executer("update mouvement_cheptel set auteur_id = null where auteur_id = :id", id);
        // Ses conseils publies, valides par l'administration, restent en
        // ligne et passent au nom de l'administration. Les autres partent.
        executer("update conseil set auteur_id = null where auteur_id = :id and statut = 'PUBLIE'", id);
        executer("delete from conseil where auteur_id = :id", id);
        executer("delete from competence where veterinaire_id = :id", id);
    }

    private void supprimerCompte(Long id, String role) {
        for (String table : List.of("notification", "session", "jeton_appareil", "code_reinitialisation")) {
            String colonne = table.equals("notification") ? "destinataire_id" : "utilisateur_id";
            executer("delete from " + table + " where " + colonne + " = :id", id);
        }
        executer("delete from " + role + " where utilisateur_id = :id", id);
        executer("delete from utilisateur where id = :id", id);
    }

    private void executer(String sql, Long id) {
        em.createNativeQuery(sql).setParameter("id", id).executeUpdate();
    }
}
