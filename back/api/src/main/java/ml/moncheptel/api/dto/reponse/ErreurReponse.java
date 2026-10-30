package ml.moncheptel.api.dto.reponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Forme unique des erreurs de l'API.
 *
 * <p>{@code message} est destine a l'utilisateur et doit pouvoir s'afficher
 * tel quel : « L'unite LOT-014 ne compte que 180 tete(s) », pas
 * « IllegalArgumentException ». C'est le gestionnaire d'exceptions qui garantit
 * ce contrat, et c'est pour cela que les regles du modele levent des messages
 * rediges plutot que des codes.
 *
 * <p>{@code champs} porte les erreurs de validation, un message par champ
 * fautif : c'est ce que le formulaire affiche sous chaque ligne.
 */
public record ErreurReponse(
        Instant horodatage,
        int statut,
        String erreur,
        String message,
        String chemin,
        Map<String, String> champs,
        List<String> details) {

    public static ErreurReponse simple(int statut, String erreur, String message, String chemin) {
        return new ErreurReponse(Instant.now(), statut, erreur, message, chemin, null, null);
    }

    public static ErreurReponse validation(String chemin, Map<String, String> champs) {
        return new ErreurReponse(Instant.now(), 400, "Validation",
                "Certains champs sont invalides", chemin, champs, null);
    }
}
