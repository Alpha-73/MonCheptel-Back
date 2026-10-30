package ml.moncheptel.api.modele.enumeration;

/**
 * Nature d'une notification.
 *
 * <p>Ecran « Notifications - Liste » et encarts du tableau de bord :
 * « Alerte epidemie — Newcastle », « Rappel vaccination ».
 */
public enum TypeNotification {

    /** Alerte sanitaire declenchee par l'administration sur une zone. */
    ALERTE("Alerte"),

    /** Echeance d'un rappel sanitaire : « LOT-014 — echeance dans 3 jours ». */
    RAPPEL("Rappel"),

    /** Un conseil attend la moderation, ou vient d'etre valide ou retire. */
    MODERATION("Modération"),

    /** Un signalement grave a ete oriente vers un veterinaire. */
    ORIENTATION("Orientation"),

    SYSTEME("Système");

    private final String libelle;

    TypeNotification(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
