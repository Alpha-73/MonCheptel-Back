package ml.moncheptel.api.dto.reponse;

import java.time.LocalDate;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.Langue;
import ml.moncheptel.api.modele.enumeration.Role;

/**
 * Ecran « Mon profil ».
 *
 * <p>Ni empreinte de mot de passe, ni compteur de tentatives, ni date de
 * verrouillage : ces trois champs existent sur l'entite et ne sortent jamais.
 */
public record ProfilReponse(
        Long id,
        String nom,
        String telephone,
        String email,
        Role role,
        ZoneReponse zone,
        Langue langue,
        LocalDate dateInscription,
        /** Renseignees pour un eleveur, vides sinon. */
        Set<Filiere> filieresPratiquees,
        /** Renseignes pour un veterinaire, nuls sinon. */
        String titre,
        String specialite,
        ZoneReponse zoneIntervention,
        Boolean disponible) {
}
