package ml.moncheptel.api.modele.enumeration;

/**
 * Nature d'un evenement sanitaire.
 *
 * <p>Les quatre valeurs de la liste deroulante de
 * « AjouterEvenement - Type ouvert », AU-DESSUS du separateur
 * « SORTIE DU CHEPTEL ». Les sorties, elles, sont des
 * {@link TypeMouvement} : elles ont d'autres champs, d'autres effets, et
 * n'ont rien a faire dans un calendrier sanitaire.
 */
public enum TypeEvenement {

    VACCINATION("Vaccination"),
    TRAITEMENT("Traitement"),
    MALADIE("Maladie"),
    CONSULTATION("Consultation");

    private final String libelle;

    TypeEvenement(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
