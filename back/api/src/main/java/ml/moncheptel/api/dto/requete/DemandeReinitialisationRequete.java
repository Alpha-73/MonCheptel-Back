package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Premiere etape de « mot de passe oublie » : l'adresse, et rien d'autre.
 *
 * <h2>Pourquoi aucun autre champ</h2>
 *
 * <p>On pourrait demander le nom en plus, pour « verifier ». Ce serait une
 * fausse securite : le nom d'un eleveur n'est pas un secret, et l'exiger
 * n'empeche personne de demander un code pour l'adresse d'autrui. Cela
 * ajouterait seulement une raison d'echouer a quelqu'un qui a vraiment oublie
 * son mot de passe.
 *
 * <p>Ce qui protege reellement, c'est que le code part a l'adresse du compte,
 * pas a celle du demandeur. Celui qui ne lit pas cette boite n'obtient rien.
 *
 * <h2>{@code @Email} ici, pas de verification d'existence</h2>
 *
 * <p>La validation du format rend 400 sur une saisie qui n'est pas une adresse
 * — « 70000000 », par exemple, que les habitudes feront taper. En revanche la
 * route ne dit JAMAIS si l'adresse correspond a un compte : voir
 * {@code AuthentificationService.demanderReinitialisation}.
 */
public record DemandeReinitialisationRequete(

        @NotBlank(message = "L'adresse e-mail est obligatoire")
        @Email(message = "Adresse e-mail invalide")
        @Size(max = 160)
        String email) {
}
