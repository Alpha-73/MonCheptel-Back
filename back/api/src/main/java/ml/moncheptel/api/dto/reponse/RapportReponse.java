package ml.moncheptel.api.dto.reponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Ecran « Rapports », sur 3 ou 12 mois.
 *
 * <p>{@code debut} et {@code fin} sont renvoyes avec les chiffres. La maquette
 * a montre pourquoi : un ecran titre « periode d'un an » affichait en fait
 * trois mois, et rien a l'ecran ne permettait de s'en apercevoir.
 */
public record RapportReponse(
        LocalDate debut,
        LocalDate fin,
        SoldeReponse total,
        List<LigneRepartitionReponse> repartitionDepenses,
        List<PointMensuelReponse> evolution) {
}
