package ml.moncheptel.api.mapper;

import java.util.List;
import ml.moncheptel.api.dto.reponse.OperationReponse;
import ml.moncheptel.api.dto.reponse.SoldeReponse;
import ml.moncheptel.api.modele.OperationFinanciere;
import ml.moncheptel.api.repository.projection.SoldeProjection;
import org.springframework.stereotype.Component;

@Component
public class OperationMapper {

    public OperationReponse versReponse(OperationFinanciere o) {
        if (o == null) {
            return null;
        }
        return new OperationReponse(
                o.getId(),
                o.getType(),
                o.getCategorie(),
                o.getMontant(),
                o.montantSigne(),
                o.getDateOperation(),
                o.getFiliere(),
                o.getUnite() == null ? null : o.getUnite().getId(),
                o.getUnite() == null ? null : o.getUnite().getCode(),
                o.getNote(),
                o.getMouvement() != null);
    }

    public List<OperationReponse> versReponses(List<OperationFinanciere> operations) {
        return operations.stream().map(this::versReponse).toList();
    }

    /**
     * Traduit l'agregat de la base.
     *
     * <p>La projection rend {@code null} quand la periode est vide sur l'un
     * des deux sens ; le {@code coalesce} des requetes couvre deja ce cas,
     * mais une projection nulle reste possible si aucune ligne ne remonte du
     * tout. Zero est la bonne reponse, pas une erreur.
     */
    public SoldeReponse versSolde(SoldeProjection p) {
        if (p == null) {
            return SoldeReponse.vide();
        }
        return new SoldeReponse(zeroSiNul(p.getRevenus()),
                                zeroSiNul(p.getDepenses()),
                                zeroSiNul(p.getSolde()));
    }

    /**
     * La projection rend des {@code Long}, pas des {@code long}. Un
     * deballage direct sur {@code null} leverait une NullPointerException a
     * l'affichage du tableau de bord d'un eleveur qui n'a rien saisi ce
     * mois-ci — le cas le plus frequent le 1er du mois.
     */
    private static long zeroSiNul(Long valeur) {
        return valeur == null ? 0L : valeur;
    }
}
