package ml.moncheptel.api.modele.enumeration;

/**
 * Etat d'une unite d'elevage.
 *
 * <p><b>Ce statut ne se saisit jamais.</b> Il decoule des mouvements
 * enregistres — l'ecran « Historique - LOT-014 » le dit noir sur blanc :
 * « Le statut ne se modifie pas a la main. Il suit les sorties enregistrees :
 * vente, mort, transfert. A zero tete, le lot passe a Termine. »
 *
 * <p>Un lot ne prend que {@link #ACTIF} ou {@link #CLOTURE} : une bande de
 * 185 poulets peut etre vendue en trois fois, et « vendu » ne dirait rien de
 * juste. Un animal individuel prend l'etat de sa sortie.
 */
public enum StatutUnite {

    /** En cours d'elevage. */
    ACTIF("Actif"),

    /**
     * Lot entierement sorti — effectif restant a zero — ou elevage termine a
     * la main (POST /api/cheptel/{id}/cloture), lot comme animal.
     */
    CLOTURE("Clôturé"),

    /** Animal individuel vendu. */
    VENDU("Vendu"),

    /** Animal individuel mort. */
    MORT("Mort"),

    /** Animal individuel cede a un tiers. */
    TRANSFERE("Transféré");

    private final String libelle;

    StatutUnite(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /** Vrai si l'unite est sortie du cheptel, quelle qu'en soit la raison. */
    public boolean estSortie() {
        return this != ACTIF;
    }
}
