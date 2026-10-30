package ml.moncheptel.api.dto.requete;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Protocole sanitaire complet d'un type d'elevage, remplace en bloc. Liste vide = aucun rappel. */
public record ProtocoleRequete(
        @NotNull @Size(max = 40, message = "Quarante étapes au plus") List<@Valid @NotNull EtapeProtocoleRequete> etapes) {
}
