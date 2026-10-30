package ml.moncheptel.api.dto.reponse;

import java.math.BigDecimal;
import ml.moncheptel.api.modele.enumeration.PhaseCout;

/**
 * Un poste de cout APPLIQUE a une simulation — ecran « Investir - Detail ».
 *
 * <p>La quantite et le prix unitaire sortent, pas seulement leur produit : un
 * eleveur qui lit « Aliment : 29 700 FCFA » ne peut ni verifier ni contester.
 * Avec « 59,4 kg x 500 FCFA », il peut dire que l'aliment est a 450 chez lui —
 * et c'est exactement le retour qui rend le referentiel juste.
 */
public record LignePosteReponse(
        String libelle,
        PhaseCout phase,
        /** Quantite calculee : 59,4 pour 33 sujets a 1,8 kg. */
        BigDecimal quantite,
        /** Unite d'affichage de la quantite : kg, m2, sujet. */
        String unite,
        /** Prix d'UNE unite. */
        long montantUnitaire,
        /** quantite x montantUnitaire, arrondi au franc. */
        long montant) {
}
