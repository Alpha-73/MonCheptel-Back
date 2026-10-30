package ml.moncheptel.api.repository.projection;

/**
 * Resultat d'un calcul de solde.
 *
 * <p>Une projection d'interface plutot qu'un {@code Object[]} : Spring Data
 * construit l'implementation, et l'appelant lit {@code getSolde()} au lieu de
 * {@code (Long) ligne[2]}. Un jour ou on ajoutera une colonne, rien ne casse.
 */
public interface SoldeProjection {

    Long getRevenus();

    Long getDepenses();

    /** Revenus moins depenses. Peut etre negatif : au jour 16 d'un cycle, il l'est. */
    Long getSolde();
}
