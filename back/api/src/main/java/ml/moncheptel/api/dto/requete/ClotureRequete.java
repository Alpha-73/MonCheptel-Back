package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ml.moncheptel.api.modele.enumeration.TypeMouvement;

/**
 * Corps FACULTATIF de {@code POST /api/cheptel/{uniteId}/cloture} (audit
 * global G5) : ce que sont devenues les tetes encore presentes.
 *
 * <p>Une unite qui a encore des tetes ne se termine pas sans ce corps : les
 * tetes sortiraient des comptes sans mouvement. Avec lui, une sortie de TOUTES
 * les tetes restantes est enregistree (et, pour une vente, son revenu), puis
 * l'unite est terminee — un seul geste, des comptes justes.
 *
 * @param type    VENTE, MORT ou TRANSFERT
 * @param montant obligatoire et positif pour une vente, absent sinon
 * @param motif   texte libre facultatif
 */
public record ClotureRequete(
        @NotNull(message = "Dites ce que sont devenues les têtes restantes")
        TypeMouvement type,

        @Positive(message = "Le montant d'une vente doit être supérieur à zéro")
        Long montant,

        @Size(max = 255) String motif) {
}
