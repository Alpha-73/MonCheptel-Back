package ml.moncheptel.api.dto.reponse;

import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.TypeMouvement;

public record MouvementReponse(
        Long id,
        TypeMouvement type,
        int nombreTetes,
        LocalDate dateMouvement,
        String motif) {
}
