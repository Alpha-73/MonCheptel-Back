package ml.moncheptel.api.dto.reponse;

import java.time.Instant;
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutAlerte;

public record AlerteReponse(
        Long id,
        String maladie,
        String zone,
        Filiere filiere,
        String message,
        int portee,
        Instant dateDeclenchement,
        StatutAlerte statut,
        LocalDate dateFin,
        Instant dateCloture) {
}
