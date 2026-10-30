package ml.moncheptel.api.repository.projection;

/**
 * Un mois de la courbe d'evolution des rapports.
 *
 * <p>L'annee et le mois sortent en NOMBRES, pas en chaine formatee. Une
 * chaine demanderait {@code date_format}, qui n'existe que sur MySQL : les
 * tests, qui tournent sur H2, echoueraient au demarrage. Les fonctions
 * {@code year()} et {@code month()} sont portables, et la mise en forme
 * « 2026-09 » se fait en Java.
 */
public interface EvolutionProjection {

    int getAnnee();

    int getMois();

    Long getRevenus();

    Long getDepenses();
}
