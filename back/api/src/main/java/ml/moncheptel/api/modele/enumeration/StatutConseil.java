package ml.moncheptel.api.modele.enumeration;

/**
 * Cycle de vie d'un conseil.
 *
 * <p>La moderation est conservee (decision de septembre 2026). L'ecran
 * « Veto - Mes conseils » affiche « En moderation », et
 * « Admin - Conseils moderation » porte les actions Valider et Retirer.
 */
public enum StatutConseil {

    BROUILLON("Brouillon"),
    EN_MODERATION("En modération"),
    PUBLIE("Publié"),
    RETIRE("Retiré");

    private final String libelle;

    StatutConseil(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /** Seuls les conseils publies sont visibles des eleveurs. */
    public boolean visiblePourEleveur() {
        return this == PUBLIE;
    }
}
