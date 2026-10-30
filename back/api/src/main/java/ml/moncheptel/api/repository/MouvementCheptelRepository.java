package ml.moncheptel.api.repository;

import java.time.LocalDate;
import java.util.List;
import ml.moncheptel.api.modele.MouvementCheptel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MouvementCheptelRepository extends JpaRepository<MouvementCheptel, Long> {

    /** Le journal d'une unite — ecran « Historique - LOT-014 ». */
    List<MouvementCheptel> findByUniteIdOrderByDateMouvementAscIdAsc(Long uniteId);

    /**
     * Activite recente d'un eleveur, pour la fiche de l'administration :
     * « A enregistre la vente du LOT-003 — 150 tetes ».
     */
    @Query("""
           select m from MouvementCheptel m
           join fetch m.unite u
           where u.proprietaire.id = :eleveurId
           order by m.dateMouvement desc, m.id desc
           """)
    List<MouvementCheptel> chercherActiviteRecente(@Param("eleveurId") Long eleveurId);

    /**
     * Mortalite constatee sur une periode.
     *
     * <p>Sert a confronter le taux reel au taux previsionnel du referentiel :
     * si un eleveur perd 20 % la ou le referentiel annonce 8 %, ce n'est pas
     * le referentiel qu'il faut corriger, c'est lui qu'il faut aider.
     */
    @Query("""
           select coalesce(sum(m.nombreTetes), 0) from MouvementCheptel m
           where m.unite.id = :uniteId
             and m.type = ml.moncheptel.api.modele.enumeration.TypeMouvement.MORT
             and m.dateMouvement between :debut and :fin
           """)
    long compterMorts(@Param("uniteId") Long uniteId,
                      @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
}
