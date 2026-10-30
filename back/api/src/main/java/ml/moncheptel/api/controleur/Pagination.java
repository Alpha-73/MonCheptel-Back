package ml.moncheptel.api.controleur;

import ml.moncheptel.api.exception.RegleMetierException;
import org.springframework.data.domain.PageRequest;

/**
 * Construit le {@code PageRequest} des routes paginees, en refusant d'abord
 * les numeros de page que Spring Data n'accepte pas.
 *
 * <h2>Le defaut qu'elle corrige</h2>
 *
 * <p>Quatre routes ecrivaient directement {@code PageRequest.of(page,
 * TAILLE_PAGE)} sur un {@code @RequestParam} non borne :
 * {@code /api/notifications}, {@code /api/conseils},
 * {@code /api/signalements/file} et {@code /api/admin/utilisateurs}.
 *
 * <p>{@code PageRequest.of(-1, 20)} leve {@code IllegalArgumentException}.
 * Cette exception n'a aucun gestionnaire dedie — {@code RegleMetierException}
 * etend {@code RuntimeException} et ne la couvre pas — donc elle tombait dans
 * le filet {@code @ExceptionHandler(Exception.class)} et sortait en <b>500</b>.
 *
 * <p>Or {@code ?page=-1} est une faute du CLIENT, pas une panne du serveur :
 * la reponse doit etre 400. Un 500 dit a l'application mobile de reessayer, et
 * fait chercher le probleme du mauvais cote.
 *
 * <h2>Pourquoi pas un gestionnaire sur {@code IllegalArgumentException}</h2>
 *
 * <p>Ce serait plus court et nettement plus mauvais. {@code
 * IllegalArgumentException} est aussi ce que levent les vrais defauts
 * internes — un argument incoherent passe a une methode du domaine. Les
 * traduire en 400 masquerait precisement les pannes que le filet 500 sert a
 * rendre visibles.
 *
 * <p>On borne donc a l'entree, la ou le parametre arrive, et la regle est
 * ecrite une fois pour les quatre routes.
 *
 * <h2>Pourquoi aucune borne SUPERIEURE</h2>
 *
 * <p>Elle a ete envisagee puis ecartee : {@code PageRequest.of(2000000000,
 * 20)} calcule son decalage en {@code long}, et la requete rend une page vide
 * sans parcourir la table. Aucun probleme concret a resoudre, donc aucune
 * limite a maintenir.
 */
final class Pagination {

    private Pagination() {
    }

    static PageRequest de(int page, int taille) {
        if (page < 0) {
            throw new RegleMetierException(
                    "Le numéro de page ne peut pas être négatif (reçu : " + page + ")");
        }
        return PageRequest.of(page, taille);
    }
}
