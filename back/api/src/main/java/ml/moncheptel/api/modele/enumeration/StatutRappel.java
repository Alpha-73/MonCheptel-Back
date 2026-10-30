package ml.moncheptel.api.modele.enumeration;

/**
 * Etat d'un rappel sanitaire.
 *
 * <p>Ecran « Dashboard - Volaille » : « Rappel vaccination — LOT-014,
 * echeance dans 3 jours ».
 */
public enum StatutRappel {

    /** Echeance future. */
    A_VENIR("À venir"),

    /** Echeance depassee sans acte enregistre. */
    ECHU("Échu"),

    /** Un evenement sanitaire correspondant a ete enregistre. */
    FAIT("Fait"),

    /** L'eleveur a ecarte ce rappel. */
    IGNORE("Ignoré");

    private final String libelle;

    StatutRappel(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
