package ml.moncheptel.api.dto.reponse;

import java.math.BigDecimal;
import ml.moncheptel.api.modele.enumeration.CategorieOperation;

/** Une part de « Repartition des depenses » : aliment, soins, main-d'oeuvre. */
public record LigneRepartitionReponse(
        CategorieOperation categorie,
        long montant,
        /** Part dans le total, en pourcentage a une decimale. Calculee au serveur. */
        BigDecimal pourcentage) {
}
