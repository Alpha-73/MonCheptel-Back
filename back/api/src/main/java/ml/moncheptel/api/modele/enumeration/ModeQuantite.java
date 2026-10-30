package ml.moncheptel.api.modele.enumeration;

/**
 * Sur quoi se multiplie un cout unitaire.
 *
 * <p>C'est ce qui permet a l'administrateur de saisir des PRIX et non des
 * totaux. Un budget de 50 000 donne 30 poulets, un de 80 000 en donne 55,
 * sans que personne ne retouche quoi que ce soit.
 */
public enum ModeQuantite {

    /**
     * Multiplie par le nombre de sujets ACHETES — mortalite comprise.
     * On nourrit et on vaccine aussi les sujets qui mourront en cours de cycle.
     */
    PAR_SUJET("Par sujet"),

    /**
     * Multiplie par la capacite de reference du type d'elevage, pas par la
     * bande en cours. On ne batit pas un poulailler de 3 m2 : on le construit
     * une fois pour une capacite, et il ressert a chaque bande.
     */
    PAR_CAPACITE("Par capacité"),

    /** Montant fixe, quel que soit l'effectif. */
    FORFAIT("Forfait");

    private final String libelle;

    ModeQuantite(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
