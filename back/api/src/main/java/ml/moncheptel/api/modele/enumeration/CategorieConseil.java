package ml.moncheptel.api.modele.enumeration;

/**
 * Categorie d'un conseil veterinaire.
 *
 * <p>Les trois puces de « Veto - Conseil rediger » : Sante, Production,
 * Hygiene. Ni plus ni moins — toute autre valeur serait inventee.
 */
public enum CategorieConseil {

    SANTE("Santé"),
    PRODUCTION("Production"),
    HYGIENE("Hygiène");

    private final String libelle;

    CategorieConseil(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
