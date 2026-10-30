package ml.moncheptel.api.modele.enumeration;

/**
 * Cycle de vie d'un signalement.
 *
 * <p>Ecrans « Veto - Eleveurs orientes » (« En attente (3) ») et
 * « Veto - Demande detail » (« Marquer comme traite »).
 */
public enum StatutSignalement {

    /** Enregistre, gravite faible ou moyenne : l'eleveur a recu un conseil, personne n'est saisi. */
    NOUVEAU("Nouveau"),

    /**
     * Confie a un veterinaire, qui doit l'ACCEPTER ou le REFUSER (V14). Un
     * refus le ramene a NOUVEAU : l'eleveur en choisit un autre.
     */
    ORIENTE("En attente de réponse"),

    /** Le veterinaire a accepte le cas. */
    EN_COURS("Pris en charge"),

    /** Cas clos par le veterinaire. */
    TRAITE("Traité");

    private final String libelle;

    StatutSignalement(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
