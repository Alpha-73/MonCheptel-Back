package ml.moncheptel.api.repository.projection;

import java.math.BigDecimal;

/**
 * Ligne brute du moteur de diagnostic : une maladie candidate, et les deux
 * seuls nombres que la base sait calculer.
 *
 * <p>Le score final n'est PAS ici. La requete rend la matiere premiere ; la
 * formule de classement vit dans le service, ou elle se relit, se discute avec
 * un veterinaire et se teste sans base de donnees.
 */
public interface ScoreMaladieProjection {

    Long getMaladieId();

    /** Somme des poids des symptomes de cette maladie qui ont ete coches. */
    BigDecimal getPoidsCumule();

    /** Nombre de symptomes coches que cette maladie explique. */
    long getCorrespondances();
}
