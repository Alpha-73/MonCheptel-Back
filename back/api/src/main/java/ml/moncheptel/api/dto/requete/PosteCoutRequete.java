package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import ml.moncheptel.api.modele.enumeration.ModeQuantite;
import ml.moncheptel.api.modele.enumeration.PhaseCout;

/**
 * Une ligne de cout du referentiel — ecran « Admin - Referentiel : fiche ».
 *
 * <p>Trois champs travaillent ensemble et se lisent mal separement :
 *
 * <ul>
 *   <li>{@code modeQuantite} dit sur QUOI la ligne se multiplie : sur les
 *       sujets achetes (PAR_SUJET), sur la capacite du batiment
 *       (PAR_CAPACITE), ou une seule fois (FORFAIT).</li>
 *   <li>{@code coefficient} dit COMBIEN par unite de base : 1,8 kg d'aliment
 *       par sujet, 0,1 m2 par sujet.</li>
 *   <li>{@code montant} dit ce que coute UNE unite : 500 FCFA le kilo.</li>
 * </ul>
 *
 * <p>Le produit des trois donne la ligne. C'est cette decomposition qui permet
 * a l'ecran d'afficher « 59,4 kg x 500 FCFA » plutot qu'un total opaque — et
 * donc a un eleveur de contester le prix du kilo.
 *
 * <p>{@code unite} n'est que du texte d'affichage (« kg », « m2 », « sujet »).
 * Aucun calcul ne la lit : une unite mal saisie enlaidit l'ecran, elle ne
 * fausse rien.
 */
public record PosteCoutRequete(
        @NotBlank @Size(max = 80) String libelle,

        @PositiveOrZero long montant,

        @Size(max = 20) String unite,

        @NotNull PhaseCout phase,
        @NotNull ModeQuantite modeQuantite,

        @NotNull @DecimalMin(value = "0.0", message = "Le coefficient ne peut pas être négatif")
        BigDecimal coefficient,

        int ordre) {
}
