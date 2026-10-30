package ml.moncheptel.api.modele.enumeration;

/**
 * Role d'un compte. Sert de discriminant a l'heritage de
 * {@code Utilisateur} et d'autorite a Spring Security.
 */
public enum Role {

    ELEVEUR,
    VETERINAIRE,
    ADMINISTRATEUR;

    /**
     * Nom de l'autorite attendue par Spring Security.
     * {@code hasRole("ELEVEUR")} cherche l'autorite {@code ROLE_ELEVEUR}.
     */
    public String autorite() {
        return "ROLE_" + name();
    }
}
