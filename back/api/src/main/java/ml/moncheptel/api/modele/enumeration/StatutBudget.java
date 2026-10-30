package ml.moncheptel.api.modele.enumeration;

/**
 * Verdict du simulateur pour une option d'investissement.
 *
 * <p>Ecran « Investir - Options » : « Il reste 4 200 FCFA » quand le budget
 * couvre, « Il manque 30 000 FCFA » quand il ne couvre pas.
 */
public enum StatutBudget {

    SUFFISANT("Suffisant"),
    INSUFFISANT("Insuffisant");

    private final String libelle;

    StatutBudget(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
