package ml.moncheptel.api.mapper;

import java.util.List;
import ml.moncheptel.api.dto.reponse.PosteCoutReponse;
import ml.moncheptel.api.dto.reponse.TypeElevageReponse;
import ml.moncheptel.api.modele.PosteCout;
import ml.moncheptel.api.modele.TypeElevage;
import org.springframework.stereotype.Component;

/**
 * Referentiel des types d'elevage.
 *
 * <p>Ce referentiel n'est pas de la documentation : chaque nombre qui sort
 * d'ici devient un montant en FCFA sur l'ecran d'investissement d'un eleveur.
 * C'est la raison pour laquelle l'ecran de detail affiche la quantite et le
 * prix unitaire separement — et donc pourquoi les postes sortent decomposes.
 */
@Component
public class ReferentielMapper {

    public TypeElevageReponse versReponse(TypeElevage t) {
        if (t == null) {
            return null;
        }
        return new TypeElevageReponse(
                t.getId(),
                t.getFiliere(),
                t.getLibelle(),
                t.getModeEvaluation(),
                t.getDureeCycleJours(),
                t.getCapaciteReference(),
                t.getEspaceParSujetM2(),
                t.getTemperatureMin(),
                t.getTemperatureMax(),
                t.getAlimentParSujetKg(),
                t.getEauParSujetJourL(),
                t.getVaccinsObligatoires(),
                t.getTauxMortalite(),
                t.getDifficulte(),
                t.isActif(),
                versPostes(t.getPostesCout()));
    }

    public List<TypeElevageReponse> versReponses(List<TypeElevage> types) {
        return types.stream().map(this::versReponse).toList();
    }

    public PosteCoutReponse versReponse(PosteCout p) {
        if (p == null) {
            return null;
        }
        return new PosteCoutReponse(
                p.getId(),
                p.getLibelle(),
                p.getMontant(),
                p.getUnite(),
                p.getPhase(),
                p.getModeQuantite(),
                p.getCoefficient(),
                p.getOrdre());
    }

    public List<PosteCoutReponse> versPostes(List<PosteCout> postes) {
        return postes == null ? List.of() : postes.stream().map(this::versReponse).toList();
    }
}
