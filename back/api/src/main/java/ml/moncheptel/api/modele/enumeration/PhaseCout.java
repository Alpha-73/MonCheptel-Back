package ml.moncheptel.api.modele.enumeration;

/**
 * A quel moment un poste de cout se paie.
 *
 * <p>Les deux intertitres de l'ecran « Admin - Referentiel fiche » :
 * « INSTALLATION — PAYEE UNE FOIS, REUTILISABLE » et « A CHAQUE CYCLE ».
 *
 * <p>La distinction n'est pas cosmetique : c'est elle qui permet a l'ecran
 * « Investir » d'annoncer « Si vous partez de zero : 72 075 FCFA » et
 * « Vous avez deja un poulailler ? 46 075 FCFA suffisent ».
 */
public enum PhaseCout {

    INSTALLATION("Installation"),
    CYCLE("Cycle");

    private final String libelle;

    PhaseCout(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
