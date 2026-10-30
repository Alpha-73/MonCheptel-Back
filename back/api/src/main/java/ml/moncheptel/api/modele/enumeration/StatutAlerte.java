package ml.moncheptel.api.modele.enumeration;

/** Ecran « Admin - Alertes liste » — colonne STATUT : Active ou Terminee. */
public enum StatutAlerte {

    ACTIVE("Active"),
    TERMINEE("Terminée");

    private final String libelle;

    StatutAlerte(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
