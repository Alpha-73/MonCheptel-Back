package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Une ligne de ponderation maladie/symptome.
 *
 * <p>Le poids est borne a [0,1] : c'est une force d'association, pas un
 * nombre libre. Sans borne haute, une seule ligne mal saisie ferait gagner sa
 * maladie a tous les coups, et le diagnostic deviendrait une constante.
 */
public record PoidsSymptomeRequete(
        @NotNull Long symptomeId,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal poids) {
}
