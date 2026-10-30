package ml.moncheptel.api.exception;

/**
 * 403 — l'utilisateur est authentifie, mais la ressource n'est pas a lui.
 *
 * <p>Le message reste volontairement vague : « Cette ressource ne vous
 * appartient pas », jamais « Le lot LOT-014 appartient a Fatoumata Traore ».
 * Un message precis confirmerait l'existence de la ressource et renseignerait
 * son proprietaire a qui essaie des identifiants au hasard.
 */
public class AccesRefuseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccesRefuseException() {
        super("Cette ressource ne vous appartient pas");
    }

    public AccesRefuseException(String message) {
        super(message);
    }
}
