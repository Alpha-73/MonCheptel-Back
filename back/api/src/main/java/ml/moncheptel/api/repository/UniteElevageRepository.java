package ml.moncheptel.api.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import ml.moncheptel.api.modele.UniteElevage;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Acces aux unites d'elevage, lots et animaux confondus.
 *
 * <p>Requeter la classe de base permet aux ecrans transverses — « MesAnimaux -
 * Toutes », le choix du sujet dans un signalement — de ne pas faire deux
 * requetes et fusionner a la main.
 */
@Repository
public interface UniteElevageRepository extends JpaRepository<UniteElevage, Long> {

    /**
     * Le code est unique CHEZ SON PROPRIETAIRE, pas sur la plateforme (voir
     * {@code UniteElevage.code}). Toute recherche par code porte donc aussi
     * l'eleveur : {@code findByCode(code)} seul rendrait « LOT-001 » de
     * quelqu'un d'autre.
     */
    Optional<UniteElevage> findByProprietaireIdAndCode(Long eleveurId, String code);

    boolean existsByProprietaireIdAndCode(Long eleveurId, String code);

    /** Sert a numeroter la prochaine unite d'un eleveur. */
    long countByProprietaireId(Long eleveurId);

    /**
     * Charge l'unite en VERROUILLANT sa ligne jusqu'a la fin de la transaction.
     *
     * <p><b>C'est la requete la plus importante de ce fichier.</b> Sans elle,
     * deux saisies simultanees de sortie — l'eleveur sur son telephone, son
     * fils sur le sien — lisent toutes les deux « 180 tetes », retirent
     * chacune 5, et ecrivent 175 au lieu de 170. Cinq betes disparaissent de
     * la comptabilite sans que personne ne s'en apercoive.
     *
     * <p>Un verrou pessimiste et non optimiste : le conflit est rare mais son
     * cout est eleve, et un {@code @Version} obligerait a rejouer la saisie
     * cote mobile, ce qu'un eleveur au champ ne comprendrait pas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UniteElevage u where u.id = :id")
    Optional<UniteElevage> chercherPourMiseAJour(@Param("id") Long id);

    /** Cheptel d'un eleveur, toutes filieres — ecran « MesAnimaux - Toutes ». */
    @Query("""
           select u from UniteElevage u
           join fetch u.typeElevage t
           where u.proprietaire.id = :eleveurId
             and (:filiere is null or t.filiere = :filiere)
             and (:statut is null or u.statut = :statut)
           order by u.dateEntree desc
           """)
    List<UniteElevage> chercherParProprietaireEtFiliere(@Param("eleveurId") Long eleveurId,
                                       @Param("filiere") Filiere filiere,
                                       @Param("statut") StatutUnite statut);

    /**
     * Le comptage des tetes n'est PAS ici.
     *
     * <p>Il faudrait une jointure d'entites avec {@code on} pour additionner
     * l'effectif d'un lot et la presence d'un animal dans une seule requete :
     * valide, mais illisible et dependante du dialecte. Deux requetes simples
     * — {@code LotRepository.sommerEffectifActif} et
     * {@code AnimalRepository.compterActifs} — additionnees dans le service
     * disent la meme chose en se relisant.
     */

    /**
     * Combien d'unites utilisent ce type d'elevage.
     *
     * <p>Garde-fou de {@code ReferentielService.modifierTypeElevage} : la
     * filiere d'une unite est DERIVEE de son type ({@code UniteElevage.getFiliere}).
     * Changer la filiere d'un type reclasserait donc d'un coup toutes les
     * unites qui l'emploient — exactement ce que {@code CheptelService.modifier}
     * interdit unite par unite, mais en masse et sans trace.
     */
    long countByTypeElevageId(Long typeElevageId);

    /**
     * Combien de signalements de maladie visent cette unite.
     *
     * <p>Garde-fou de la suppression. {@code fk_signalement_unite} est NOT NULL
     * et sans cascade : sans ce controle, supprimer un lot qui a ete signale
     * levait une violation de contrainte, donc 500, au lieu du 422 motive que
     * la methode promet.
     */
    @Query("select count(s.id) from SignalementMaladie s where s.unite.id = :uniteId")
    long compterSignalements(@Param("uniteId") Long uniteId);
}
