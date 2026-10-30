package ml.moncheptel.api.dto.reponse;

import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.TypeMouvement;

/**
 * Une sortie du cheptel, telle que le carnet la liste pour l'annuler.
 *
 * <p>{@code id} est l'identifiant du mouvement de sortie : c'est le
 * {@code sortieId} de {@code DELETE /api/cheptel/{uniteId}/sorties/{sortieId}},
 * et le meme que {@code mouvements[].id}. {@code montant} n'existe que pour
 * une vente.
 */
public record SortieReponse(
        Long id,
        LocalDate date,
        TypeMouvement nature,
        int nombre,
        Long montant,
        String motif) {
}
