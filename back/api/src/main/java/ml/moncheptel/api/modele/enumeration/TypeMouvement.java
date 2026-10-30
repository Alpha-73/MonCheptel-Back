package ml.moncheptel.api.modele.enumeration;

/**
 * Nature d'un mouvement de cheptel.
 *
 * <p>Les trois sorties sont exactement celles de la liste deroulante de
 * « AjouterEvenement - Type ouvert », sous l'intitule
 * <b>SORTIE DU CHEPTEL</b>, chacune avec l'effet que l'ecran annonce :
 * « deduit l'effectif vendu / perdu / cede ».
 *
 * <p>Les entrees, elles, ne sont pas saisies par un formulaire dedie : elles
 * naissent de la creation d'un lot ou d'un animal. Les modeliser quand meme
 * rend l'effectif verifiable — l'ecran « Historique - LOT-014 » affiche bien
 * « Lot cree, 185 tetes mises en place » comme une ligne du journal.
 */
public enum TypeMouvement {

    MISE_EN_PLACE("Mise en place", SensMouvement.ENTREE),
    ACQUISITION("Acquisition", SensMouvement.ENTREE),
    NAISSANCE("Naissance", SensMouvement.ENTREE),

    VENTE("Vente", SensMouvement.SORTIE),
    MORT("Mort", SensMouvement.SORTIE),
    TRANSFERT("Transfert", SensMouvement.SORTIE);

    private final String libelle;
    private final SensMouvement sens;

    TypeMouvement(String libelle, SensMouvement sens) {
        this.libelle = libelle;
        this.sens = sens;
    }

    public String getLibelle() {
        return libelle;
    }

    public SensMouvement getSens() {
        return sens;
    }

    public boolean estSortie() {
        return sens == SensMouvement.SORTIE;
    }

    /**
     * Statut que prend un ANIMAL INDIVIDUEL apres ce mouvement de sortie.
     * Un lot, lui, passe a {@link StatutUnite#CLOTURE} quand son effectif
     * atteint zero, quelle que soit la raison.
     *
     * @throws IllegalStateException si appele sur un mouvement d'entree
     */
    public StatutUnite statutResultant() {
        return switch (this) {
            case VENTE -> StatutUnite.VENDU;
            case MORT -> StatutUnite.MORT;
            case TRANSFERT -> StatutUnite.TRANSFERE;
            default -> throw new IllegalStateException(
                    "Un mouvement d'entrée ne fait pas sortir l'élevage : " + libelle);
        };
    }
}
