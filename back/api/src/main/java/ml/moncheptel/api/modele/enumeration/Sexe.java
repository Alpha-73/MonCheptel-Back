package ml.moncheptel.api.modele.enumeration;

/** Ecran « AjouterAnimal - Rempli » — deux boutons, Male et Femelle. */
public enum Sexe {

    MALE("Mâle"),
    FEMELLE("Femelle");

    private final String libelle;

    Sexe(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
