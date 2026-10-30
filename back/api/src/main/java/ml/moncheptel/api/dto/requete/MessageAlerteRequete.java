package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Le seul champ modifiable d'une alerte deja declenchee : son message.
 *
 * <h2>Pourquoi ni la maladie, ni la zone, ni la filiere</h2>
 *
 * <p>La portee — « 318 eleveurs touches » — est FIGEE au declenchement,
 * volontairement : un eleveur inscrit apres coup changerait la portee d'une
 * alerte passee alors qu'il n'a rien recu.
 *
 * <p>Changer la zone ou la maladie rendrait ce nombre faux sans le recalculer,
 * et les notifications deja parties resteraient chez des gens que l'alerte ne
 * concerne plus. Une alerte qui vise la mauvaise zone se cloture et se
 * redeclenche ; elle ne se detourne pas.
 *
 * <p>C'est exactement ce que montre la maquette : maladie, filiere et zone y
 * sont affichees, seul le message est dans un champ de saisie.
 */
public record MessageAlerteRequete(

        @NotBlank(message = "Le message est obligatoire")
        @Size(max = 500)
        String message,

        /** Dernier jour de l'alerte ; nulle = jusqu'a cloture manuelle. */
        @FutureOrPresent(message = "La date de fin ne peut pas être passée")
        LocalDate dateFin) {
}
