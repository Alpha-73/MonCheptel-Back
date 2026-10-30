package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Seconde etape de « mot de passe oublie » : le code recu, et le nouveau mot
 * de passe.
 *
 * <h2>Pourquoi l'adresse est redemandee</h2>
 *
 * <p>Le code seul ne suffit pas a designer un compte : il fait six chiffres, et
 * deux comptes peuvent tirer le meme au meme moment. C'est le COUPLE
 * (adresse, code) qui identifie une demande. Rechercher sur le code seul
 * ouvrirait la porte a l'attaque suivante : demander un code pour son propre
 * compte, puis le presenter sans adresse en esperant tomber sur celui d'un
 * autre.
 *
 * <h2>Le motif du code accepte les espaces</h2>
 *
 * <p>Le courriel presente le code sous la forme « 482 913 », pour qu'il se
 * relise. L'utilisateur recopiera donc parfois l'espace. Le refuser rendrait
 * 400 sur une saisie juste ; le motif l'accepte et le service le retire avant
 * de comparer.
 *
 * <h2>La longueur minimale du mot de passe</h2>
 *
 * <p>Huit caracteres, comme a l'inscription et au changement : trois endroits
 * ou l'on choisit un mot de passe, une seule regle. Deux regles differentes
 * feraient accepter ici ce qui est refuse ailleurs, et l'utilisateur ne saurait
 * jamais ce qui est attendu.
 */
public record ReinitialisationRequete(

        @NotBlank(message = "L'adresse e-mail est obligatoire")
        @Email(message = "Adresse e-mail invalide")
        @Size(max = 160)
        String email,

        @NotBlank(message = "Le code reçu par courriel est obligatoire")
        @Pattern(regexp = "[0-9 ]{6,8}",
                 message = "Le code comporte six chiffres")
        String code,

        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(min = 8, max = 72,
              message = "Le mot de passe doit faire entre 8 et 72 caractères")
        String nouveauMotDePasse) {
}
