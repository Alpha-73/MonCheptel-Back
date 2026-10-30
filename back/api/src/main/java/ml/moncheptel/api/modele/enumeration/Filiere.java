package ml.moncheptel.api.modele.enumeration;

/**
 * Les quatre filieres de la plateforme.
 *
 * <p>Source : les onglets de « Dashboard », « MesAnimaux » et « Conseils ».
 * Chaque filiere porte son mode de suivi : c'est ce qui evite un {@code if}
 * sur le nom de la filiere dispersé dans le code.
 */
public enum Filiere {

    VOLAILLE("Volaille", ModeSuivi.LOT),
    BOVINS("Bovins", ModeSuivi.INDIVIDUEL),
    OVINS("Ovins", ModeSuivi.INDIVIDUEL),
    CAPRINS("Caprins", ModeSuivi.INDIVIDUEL);

    private final String libelle;
    private final ModeSuivi modeSuivi;

    Filiere(String libelle, ModeSuivi modeSuivi) {
        this.libelle = libelle;
        this.modeSuivi = modeSuivi;
    }

    public String getLibelle() {
        return libelle;
    }

    public ModeSuivi getModeSuivi() {
        return modeSuivi;
    }

    /** Vrai si cette filiere se suit par bandes plutot que bete par bete. */
    public boolean suiviParLot() {
        return modeSuivi == ModeSuivi.LOT;
    }
}
