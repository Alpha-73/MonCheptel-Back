package ml.moncheptel.api.repository;

import java.time.Instant;
import java.util.List;
import ml.moncheptel.api.modele.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByDestinataireIdOrderByDateCreationDesc(Long destinataireId,
                                                                   Pageable pageable);

    /** La pastille « 2 » sur la cloche de l'en-tete. */
    long countByDestinataireIdAndLueFalse(Long destinataireId);

    @Modifying
    @Query("""
           update Notification n set n.lue = true
           where n.destinataire.id = :destinataireId and n.lue = false
           """)
    int marquerToutesLues(@Param("destinataireId") Long destinataireId);

    /**
     * Notifications a (re)tenter aupres de Firebase.
     *
     * <p>Le plafond de tentatives est passe en parametre, pas ecrit en dur :
     * c'est un reglage d'exploitation, il vit dans la configuration. Sans ce
     * plafond, une notification vers un appareil desinstalle serait reprise
     * indefiniment.
     */
    @Query("""
           select n from Notification n
           join fetch n.destinataire
           where n.statutEnvoi in (
                   ml.moncheptel.api.modele.enumeration.StatutEnvoi.EN_ATTENTE,
                   ml.moncheptel.api.modele.enumeration.StatutEnvoi.ECHEC)
             and n.tentativesEnvoi < :maxTentatives
           order by n.dateCreation asc
           """)
    List<Notification> chercherAEnvoyer(@Param("maxTentatives") int maxTentatives,
                                        Pageable pageable);

    /**
     * Retire les notifications qui pointent vers un objet supprime (audit du
     * 29 octobre, point 9) : sans cela, la cloche menait a un conseil disparu.
     */
    @Modifying
    @Query("""
           delete from Notification n
           where n.referenceType = :referenceType and n.referenceId = :referenceId
           """)
    int supprimerPourReference(@Param("referenceType") String referenceType,
                               @Param("referenceId") Long referenceId);

    /** Purge : l'historique des notifications n'a pas a grossir sans fin. */
    @Modifying
    @Query("delete from Notification n where n.lue = true and n.dateCreation < :avant")
    int purger(@Param("avant") Instant avant);
}
