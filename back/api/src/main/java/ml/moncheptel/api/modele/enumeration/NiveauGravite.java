package ml.moncheptel.api.modele.enumeration;

/**
 * Gravite d'une maladie ou d'un signalement.
 *
 * <p>Les trois pastilles du catalogue : Faible, Moyen, Eleve.
 */
public enum NiveauGravite {

    FAIBLE("Faible", 1),
    MOYEN("Moyen", 2),
    ELEVE("Élevé", 3);

    private final String libelle;
    private final int niveau;

    NiveauGravite(String libelle, int niveau) {
        this.libelle = libelle;
        this.niveau = niveau;
    }

    public String getLibelle() {
        return libelle;
    }

    public int getNiveau() {
        return niveau;
    }

    /** La plus grave des deux. Sert a combiner la gravite d'une maladie et l'ampleur constatee. */
    public NiveauGravite max(NiveauGravite autre) {
        return this.niveau >= autre.niveau ? this : autre;
    }
}
