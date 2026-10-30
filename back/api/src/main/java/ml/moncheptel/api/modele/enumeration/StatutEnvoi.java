package ml.moncheptel.api.modele.enumeration;

/**
 * Suivi de l'envoi push d'une notification.
 *
 * <p>Firebase Cloud Messaging est le seul canal de la V1 — le SMS est retire
 * du perimetre. Conserver l'etat d'envoi permet de rejouer les echecs : un
 * telephone eteint au moment d'une alerte epidemique doit la recevoir a son
 * rallumage, sinon l'alerte ne sert a rien.
 */
public enum StatutEnvoi {

    EN_ATTENTE("En attente"),
    ENVOYE("Envoyé"),
    ECHEC("Échec"),
    /** Jeton FCM invalide ou revoque : inutile de reessayer. */
    ABANDONNE("Abandonné");

    private final String libelle;

    StatutEnvoi(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    public boolean aRejouer() {
        return this == EN_ATTENTE || this == ECHEC;
    }
}
