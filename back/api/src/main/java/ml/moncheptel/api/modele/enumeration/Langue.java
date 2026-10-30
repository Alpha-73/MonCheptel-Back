package ml.moncheptel.api.modele.enumeration;

/**
 * Langue d'interface. Ecran « Langue - Francais » et « Profil - Accueil ».
 *
 * <p>Le bambara est present des la V1 : c'est la langue de la majorite des
 * eleveurs maliens, et la reduire a une evolution future viderait la
 * plateforme de son public.
 */
public enum Langue {

    FR("Français"),
    BM("Bambara");

    private final String libelle;

    Langue(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
