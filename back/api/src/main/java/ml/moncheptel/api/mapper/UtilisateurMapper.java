package ml.moncheptel.api.mapper;

import java.util.List;
import java.util.Set;
import ml.moncheptel.api.dto.reponse.ProfilReponse;
import ml.moncheptel.api.dto.reponse.UtilisateurResumeReponse;
import ml.moncheptel.api.dto.reponse.VeterinaireAdminReponse;
import ml.moncheptel.api.dto.reponse.VeterinaireResumeReponse;
import ml.moncheptel.api.modele.Competence;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.Utilisateur;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.modele.enumeration.Filiere;
import org.springframework.stereotype.Component;

/**
 * Profils utilisateurs.
 *
 * <p>Ni {@code motDePasse}, ni {@code tentativesEchouees}, ni
 * {@code verrouilleJusquA} ne sont recopies. Ce n'est pas un oubli a combler
 * plus tard : ces trois champs n'ont aucune raison de quitter le serveur, et
 * le troisieme indiquerait a un attaquant que son blocage tient encore.
 */
@Component
public class UtilisateurMapper {

    private final ZoneMapper zoneMapper;

    public UtilisateurMapper(ZoneMapper zoneMapper) {
        this.zoneMapper = zoneMapper;
    }

    public ProfilReponse versProfil(Utilisateur u) {
        if (u == null) {
            return null;
        }
        // filieresOrdonnees et non getFilieresPratiquees : Hibernate rend un
        // HashSet, dont l'ordre d'iteration change a CHAQUE demarrage de la JVM
        // (le hashCode d'une constante d'enumeration est celui de l'objet).
        // Cette liste devient des pastilles sur l'ecran « Mes informations » :
        // sans ordre stable, elles changeraient de place d'un redemarrage du
        // serveur a l'autre, sans qu'aucune erreur ne soit levee.
        // Meme correction que dans TableauDeBordService, pour la meme raison.
        Set<ml.moncheptel.api.modele.enumeration.Filiere> filieres =
                (u instanceof Eleveur e) ? e.filieresOrdonnees() : Set.of();
        Veterinaire v = (u instanceof Veterinaire vet) ? vet : null;

        return new ProfilReponse(
                u.getId(),
                u.getNom(),
                u.getTelephone(),
                u.getEmail(),
                u.getRole(),
                zoneMapper.versReponse(u.getZone()),
                u.getLangue(),
                u.getDateInscription(),
                filieres,
                v == null ? null : v.getTitre(),
                v == null ? null : v.getSpecialite(),
                v == null ? null : zoneMapper.versReponse(v.getZoneIntervention()),
                v == null ? null : v.isDisponible());
    }

    /**
     * Fiche d'annuaire.
     *
     * <p>Le telephone y figure : c'est la raison d'etre de l'annuaire, un
     * eleveur doit pouvoir appeler. Il n'est jamais renvoye pour un eleveur.
     */
    public VeterinaireResumeReponse versResume(Veterinaire v) {
        if (v == null) {
            return null;
        }
        return new VeterinaireResumeReponse(
                v.getId(),
                v.getNom(),
                v.getTitre(),
                v.getSpecialite(),
                v.getZoneIntervention() == null ? null : v.getZoneIntervention().getNom(),
                v.getTelephone(),
                v.isDisponible());
    }

    /**
     * Un veterinaire vu par l'administration. A appeler dans une transaction :
     * les competences sont chargees paresseusement.
     */
    public VeterinaireAdminReponse versAdministration(Veterinaire v) {
        if (v == null) {
            return null;
        }
        // Les maladies retirees du catalogue ne comptent plus : le formulaire
        // ne les montre pas et le catalogue ne compte pas ce veterinaire pour elles.
        List<Competence> competences = v.getCompetences().stream()
                .filter(c -> c.getMaladie().isActive())
                .sorted(java.util.Comparator.comparing(c -> c.getMaladie().getNom()))
                .toList();
        Set<Filiere> filieres = new java.util.TreeSet<>();
        competences.forEach(c -> filieres.addAll(c.getMaladie().getFilieres()));
        return new VeterinaireAdminReponse(
                v.getId(),
                v.getNom(),
                v.getTitre(),
                v.getSpecialite(),
                v.getTelephone(),
                v.getEmail(),
                zoneMapper.versReponse(v.getZone()),
                zoneMapper.versReponse(v.getZoneIntervention()),
                v.isActif(),
                v.isDisponible(),
                v.estVerrouille(),
                filieres,
                competences.stream().map(c -> c.getMaladie().getId()).toList(),
                competences.size());
    }

    /**
     * Une ligne du tableau « Utilisateurs » de l'administration.
     *
     * <h3>Pourquoi l'etat de verrouillage sort ici, alors que le Javadoc de
     * cette classe l'interdit</h3>
     *
     * <p>L'interdiction porte sur {@link #versProfil}, c'est-a-dire sur ce que
     * l'API rend a l'utilisateur lui-meme ou a un tiers. La raison y est
     * ecrite : dire a quelqu'un que son blocage tient encore lui apprend que
     * ses tentatives comptent, et l'invite a attendre puis a recommencer.
     *
     * <p>Ici le destinataire est l'ADMINISTRATEUR, authentifie comme tel
     * ({@code @PreAuthorize} sur {@code AdministrationControleur}), et le
     * besoin est inverse : un eleveur appelle en disant qu'il n'arrive pas a
     * entrer. Sans cette information, l'administrateur voit « Actif »,
     * conclut a une panne, et cherche pendant une demi-heure un probleme qui
     * se resoudra tout seul en dix minutes.
     *
     * <p>Et ce qui sort est un BOOLEEN, pas {@code verrouilleJusquA}. L'heure
     * exacte de deblocage n'apporte rien de plus a l'ecran et serait une
     * donnee de plus a proteger.
     *
     * <p><b>La contrainte que cela cree :</b> ce DTO ne doit jamais etre rendu
     * par une route non reservee a l'administration. Si une autre route en a
     * besoin, il faut un second DTO, pas un assouplissement de celui-ci.
     */
    public UtilisateurResumeReponse versResumeAdministration(Utilisateur u) {
        if (u == null) {
            return null;
        }
        return new UtilisateurResumeReponse(
                u.getId(),
                u.getNom(),
                u.getEmail(),
                u.getTelephone(),
                u.getRole(),
                u.getZone() == null ? null : u.getZone().getNom(),
                u instanceof Eleveur e ? e.filieresOrdonnees() : Set.of(),
                u.getDateInscription(),
                u.isActif(),
                u.estVerrouille(),
                u.isDoitChangerMotDePasse());
    }
}
