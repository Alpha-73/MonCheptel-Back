package ml.moncheptel.api.mapper;

import java.util.List;
import ml.moncheptel.api.dto.reponse.ConseilDetailReponse;
import ml.moncheptel.api.dto.reponse.ConseilResumeReponse;
import ml.moncheptel.api.modele.Conseil;
import org.springframework.stereotype.Component;

@Component
public class ConseilMapper {

    /** Longueur de l'extrait affiche dans la liste, en caracteres. */
    private static final int LONGUEUR_EXTRAIT = 160;

    private final UtilisateurMapper utilisateurMapper;

    public ConseilMapper(UtilisateurMapper utilisateurMapper) {
        this.utilisateurMapper = utilisateurMapper;
    }

    public ConseilResumeReponse versResume(Conseil c) {
        if (c == null) {
            return null;
        }
        return new ConseilResumeReponse(
                c.getId(),
                c.getTitre(),
                extraire(c.getContenu()),
                c.getFiliere(),
                c.getCategorie(),
                c.getAuteur() == null ? null : c.getAuteur().getNom(),
                c.getDatePublication(),
                c.getNombreLectures());
    }

    public List<ConseilResumeReponse> versResumes(List<Conseil> conseils) {
        return conseils.stream().map(this::versResume).toList();
    }

    /**
     * @param avecModeration vrai pour l'auteur et l'administration : le statut
     *                       et le motif de retrait n'ont de sens que pour eux.
     *                       Un eleveur ne lit que des conseils publies.
     */
    public ConseilDetailReponse versDetail(Conseil c, boolean avecModeration) {
        if (c == null) {
            return null;
        }
        return new ConseilDetailReponse(
                c.getId(),
                c.getTitre(),
                c.getContenu(),
                c.getFiliere(),
                c.getCategorie(),
                utilisateurMapper.versResume(c.getAuteur()),
                c.getDatePublication(),
                c.getNombreLectures(),
                avecModeration ? c.getStatut() : null,
                avecModeration ? c.getMotifRetrait() : null,
                c.getDateCreation(),
                avecModeration ? c.getDateSoumission() : null);
    }

    /**
     * Coupe sur un espace, jamais au milieu d'un mot, et n'ajoute des points de
     * suspension que si le texte a ete tronque — un conseil de trois lignes
     * suivi de « ... » laisserait croire qu'il en manque.
     */
    private static String extraire(String contenu) {
        if (contenu == null) {
            return null;
        }
        String propre = contenu.strip();
        if (propre.length() <= LONGUEUR_EXTRAIT) {
            return propre;
        }
        int coupe = propre.lastIndexOf(' ', LONGUEUR_EXTRAIT);
        if (coupe < LONGUEUR_EXTRAIT / 2) {
            coupe = LONGUEUR_EXTRAIT;
        }
        return propre.substring(0, coupe).stripTrailing() + "...";
    }
}
