package ml.moncheptel.api.dto.reponse;

import java.time.Instant;
import java.util.List;

/**
 * Ecran « Fiche utilisateur » de l'administration.
 *
 * <p>Le profil, plus ce que la fiche affiche a cote : l'etat du compte, les
 * chiffres du cheptel et des signalements, et l'activite recente (sorties et
 * entrees du cheptel, signalements), la plus recente en premier.
 */
public record FicheUtilisateurReponse(
        ProfilReponse profil,
        boolean actif,
        boolean verrouille,
        long unites,
        long tetes,
        long signalements,
        long signalementsEnCours,
        List<Activite> activite) {

    public record Activite(Instant date, String libelle) {
    }
}
