package ml.moncheptel.api.dto.reponse;

/** Une ligne de l'annuaire veterinaire. */
public record VeterinaireResumeReponse(
        Long id,
        String nom,
        String titre,
        String specialite,
        String zoneIntervention,
        String telephone,
        boolean disponible) {
}
