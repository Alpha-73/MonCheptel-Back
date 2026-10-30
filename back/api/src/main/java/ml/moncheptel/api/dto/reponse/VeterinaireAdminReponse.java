package ml.moncheptel.api.dto.reponse;

import java.util.List;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;

/**
 * Un veterinaire vu par l'administration : ecrans « Vétérinaires », « Créer » et
 * « Modifier un vétérinaire ».
 *
 * <p>Plus riche que {@link VeterinaireResumeReponse}, qui est la carte de
 * l'annuaire montree aux eleveurs : l'administration a besoin de l'adresse
 * e-mail, de l'etat du compte et des competences cochees. Les filieres sont
 * DEDUITES des maladies traitees ; elles ne sont stockees nulle part.
 */
public record VeterinaireAdminReponse(
        Long id,
        String nom,
        String titre,
        String specialite,
        String telephone,
        String email,
        ZoneReponse zone,
        ZoneReponse zoneIntervention,
        boolean actif,
        boolean disponible,
        boolean verrouille,
        Set<Filiere> filieres,
        List<Long> competences,
        int nombreCompetences) {
}
