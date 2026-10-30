package ml.moncheptel.api.repository.projection;

import ml.moncheptel.api.modele.enumeration.CategorieOperation;

/** Une ligne de « Repartition des depenses » de l'ecran Rapports. */
public interface RepartitionProjection {

    CategorieOperation getCategorie();

    Long getTotal();
}
