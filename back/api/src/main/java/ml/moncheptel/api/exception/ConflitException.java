package ml.moncheptel.api.exception;

/** 409 — doublon, ou action deja effectuee (alerte deja active, conseil deja publie). */
public class ConflitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflitException(String message) {
        super(message);
    }
}
