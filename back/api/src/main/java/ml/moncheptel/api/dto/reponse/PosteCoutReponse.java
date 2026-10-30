package ml.moncheptel.api.dto.reponse;

import java.math.BigDecimal;
import ml.moncheptel.api.modele.enumeration.ModeQuantite;
import ml.moncheptel.api.modele.enumeration.PhaseCout;

/**
 * Un poste de cout tel qu'il est DEFINI dans le referentiel.
 *
 * <p>A distinguer de {@link LignePosteReponse}, qui est le meme poste une fois
 * APPLIQUE a une simulation : ici il n'y a pas de quantite, parce qu'on ne
 * sait pas encore combien de sujets.
 */
public record PosteCoutReponse(
        Long id,
        String libelle,
        long montant,
        String unite,
        PhaseCout phase,
        ModeQuantite modeQuantite,
        BigDecimal coefficient,
        int ordre) {
}
