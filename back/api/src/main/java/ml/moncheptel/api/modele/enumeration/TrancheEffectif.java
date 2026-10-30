package ml.moncheptel.api.modele.enumeration;

/**
 * Ampleur d'un signalement, telle que l'eleveur la declare.
 *
 * <p>Ecran « Signalement - Symptomes » : « Combien de sujets sont touches ? »
 * — trois boutons, <b>pas de champ numerique</b>. C'est un choix de conception
 * qu'il faut respecter : un eleveur qui voit ses poulets mourir ne compte pas,
 * il estime. Lui demander un nombre exact produirait un chiffre faux avec une
 * apparence de precision.
 */
public enum TrancheEffectif {

    MOINS_DE_10("Moins de 10", 1, 9),
    DE_10_A_50("10 à 50", 10, 50),
    PLUS_DE_50("Plus de 50", 51, null);

    private final String libelle;
    private final int borneBasse;
    private final Integer borneHaute;   // null = pas de plafond

    TrancheEffectif(String libelle, int borneBasse, Integer borneHaute) {
        this.libelle = libelle;
        this.borneBasse = borneBasse;
        this.borneHaute = borneHaute;
    }

    public String getLibelle() {
        return libelle;
    }

    public int getBorneBasse() {
        return borneBasse;
    }

    public Integer getBorneHaute() {
        return borneHaute;
    }

    /**
     * Contribution de l'ampleur a la gravite du signalement.
     *
     * <p>Un sujet malade sur cent-quatre-vingts n'est pas une epidemie ;
     * cinquante-quatre, si. C'est l'ampleur qui fait monter l'urgence, pas la
     * maladie seule.
     */
    public NiveauGravite graviteInduite() {
        return switch (this) {
            case MOINS_DE_10 -> NiveauGravite.FAIBLE;
            case DE_10_A_50 -> NiveauGravite.MOYEN;
            case PLUS_DE_50 -> NiveauGravite.ELEVE;
        };
    }
}
