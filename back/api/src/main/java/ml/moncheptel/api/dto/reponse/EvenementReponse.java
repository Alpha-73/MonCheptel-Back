package ml.moncheptel.api.dto.reponse;

import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.TypeEvenement;

public record EvenementReponse(
        Long id,
        TypeEvenement type,
        LocalDate dateEvenement,
        String produitObservation,
        String auteur,
        String veterinaire) {
}
