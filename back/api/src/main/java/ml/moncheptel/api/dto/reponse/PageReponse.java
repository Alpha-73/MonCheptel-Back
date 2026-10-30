package ml.moncheptel.api.dto.reponse;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Enveloppe de pagination.
 *
 * <p>Pourquoi ne pas renvoyer directement le {@code Page} de Spring : sa
 * forme JSON n'est pas un contrat stable — elle a change entre versions de
 * Spring Data, et Spring Boot 3.3 emet meme un avertissement au demarrage
 * quand un controleur en serialise un. Un client Flutter compile aujourd'hui
 * cesserait de lire ses listes apres une montee de version du back.
 *
 * <p>Quatre champs suffisent a tout ce que la maquette fait : afficher les
 * elements, dire « page 2 sur 7 », et savoir s'il faut charger la suite.
 */
public record PageReponse<T>(
        List<T> elements,
        int page,
        int taille,
        long total,
        int nombrePages,
        boolean derniere) {

    public static <T> PageReponse<T> de(Page<T> page) {
        return new PageReponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    /** Meme enveloppe, contenu transforme : evite de mapper puis de re-paginer. */
    public <R> PageReponse<R> avec(List<R> autresElements) {
        return new PageReponse<>(autresElements, page, taille, total, nombrePages, derniere);
    }
}
