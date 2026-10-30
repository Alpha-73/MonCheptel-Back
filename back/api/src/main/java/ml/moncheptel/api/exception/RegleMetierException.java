package ml.moncheptel.api.exception;

/**
 * 422 — la demande est bien formee, mais une regle metier l'interdit.
 *
 * <p>A distinguer d'une erreur de validation (400) : « vendre 200 tetes d'un
 * lot qui en compte 180 » est une requete parfaitement valide au sens du
 * format. C'est l'etat du systeme qui la refuse, et seul le serveur peut le
 * savoir.
 */
public class RegleMetierException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RegleMetierException(String message) {
        super(message);
    }
}
