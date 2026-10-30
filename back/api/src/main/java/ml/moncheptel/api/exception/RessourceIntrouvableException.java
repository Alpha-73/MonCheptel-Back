package ml.moncheptel.api.exception;

/** 404 — l'identifiant demande ne designe rien. */
public class RessourceIntrouvableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RessourceIntrouvableException(String message) {
        super(message);
    }

    public static RessourceIntrouvableException de(String quoi, Object identifiant) {
        return new RessourceIntrouvableException(quoi + " introuvable : " + identifiant);
    }
}
