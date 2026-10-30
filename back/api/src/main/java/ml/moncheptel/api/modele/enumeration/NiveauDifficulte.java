package ml.moncheptel.api.modele.enumeration;

/**
 * Difficulte annoncee d'un type d'elevage. Pastille des ecrans
 * « Investir - Detail » et « Logistique ».
 *
 * <p>ATTENTION — ce niveau est aujourd'hui saisi a la main par
 * l'administrateur. Il gagnerait a etre derive de l'investissement, de la
 * duree du cycle et de la technicite vaccinale, faute de quoi c'est un
 * jugement porte sans connaitre l'utilisateur.
 */
public enum NiveauDifficulte {

    ACCESSIBLE("Accessible"),
    MOYEN("Moyen"),
    EXIGEANT("Exigeant");

    private final String libelle;

    NiveauDifficulte(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
