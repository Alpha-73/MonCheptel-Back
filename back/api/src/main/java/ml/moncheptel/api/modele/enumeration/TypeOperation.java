package ml.moncheptel.api.modele.enumeration;

/** Ecran « Operation » — deux segments, Depense et Revenu. */
public enum TypeOperation {

    DEPENSE("Dépense", -1),
    REVENU("Revenu", +1);

    private final String libelle;
    private final int signe;

    TypeOperation(String libelle, int signe) {
        this.libelle = libelle;
        this.signe = signe;
    }

    public String getLibelle() {
        return libelle;
    }

    /**
     * Signe a appliquer au montant pour calculer un solde.
     *
     * <p>Les montants sont toujours stockes positifs : un montant negatif en
     * base est une erreur de saisie, pas une depense. C'est ce signe qui fait
     * le calcul, et c'est lui qui donne le + 74 000 FCFA du tableau de bord.
     */
    public int getSigne() {
        return signe;
    }
}
