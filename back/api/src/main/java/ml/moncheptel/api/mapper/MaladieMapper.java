package ml.moncheptel.api.mapper;

import java.util.List;
import ml.moncheptel.api.dto.reponse.MaladieReponse;
import ml.moncheptel.api.dto.reponse.SymptomeReponse;
import ml.moncheptel.api.modele.Maladie;
import ml.moncheptel.api.modele.Symptome;
import org.springframework.stereotype.Component;

/**
 * Maladies et symptomes.
 *
 * <h2>Le masquage du traitement n'existe qu'ici</h2>
 *
 * <p>Quand une maladie est marquee {@code orienteVersVeterinaire}, son
 * protocole de traitement ne quitte pas le serveur. {@code versReponseEleveur}
 * lit {@code Maladie.traitementAffichable()}, qui rend {@code null} dans ce
 * cas.
 *
 * <p>Masquer le champ cote Flutter ne suffirait pas : le texte resterait dans
 * la reponse JSON, lisible par quiconque ouvre les outils de developpement ou
 * intercepte l'appel. Une seule methode decide, et c'est celle-ci.
 *
 * <p>{@code versReponseAdmin} rend le traitement en clair. C'est l'exception
 * assumee : l'administration edite le catalogue, elle doit voir ce qu'elle
 * ecrit. Les deux methodes portent des noms differents pour qu'aucun appel ne
 * choisisse la mauvaise par inadvertance.
 */
@Component
public class MaladieMapper {

    /** Vue ELEVEUR : le traitement est masque quand la maladie exige un veterinaire. */
    public MaladieReponse versReponseEleveur(Maladie m) {
        if (m == null) {
            return null;
        }
        return construire(m, m.traitementAffichable());
    }

    /** Vue ADMINISTRATION et VETERINAIRE : traitement complet. */
    public MaladieReponse versReponseAdmin(Maladie m) {
        if (m == null) {
            return null;
        }
        return construire(m, m.getTraitement());
    }

    private MaladieReponse construire(Maladie m, String traitement) {
        return new MaladieReponse(
                m.getId(),
                m.getNom(),
                m.getGravite(),
                m.getFilieres(),
                m.getSymptomesTexte(),
                traitement,
                m.isOrienteVersVeterinaire());
    }

    public SymptomeReponse versReponse(Symptome s) {
        if (s == null) {
            return null;
        }
        return new SymptomeReponse(s.getId(), s.getLibelle());
    }

    public List<SymptomeReponse> versReponses(List<Symptome> symptomes) {
        return symptomes.stream().map(this::versReponse).toList();
    }
}
