package ml.moncheptel.api.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import ml.moncheptel.api.modele.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByEmpreinteJeton(String empreinteJeton);

    List<Session> findByUtilisateurIdAndRevoqueeFalse(Long utilisateurId);

    /**
     * Revoque toutes les sessions d'un utilisateur.
     *
     * <p>Appelee a chaque changement de mot de passe : si le compte a ete
     * compromis, changer le mot de passe doit faire tomber l'acces sur TOUS
     * les appareils, pas seulement sur celui qu'on a sous la main.
     */
    @Modifying
    @Query("update Session s set s.revoquee = true where s.utilisateur.id = :utilisateurId")
    int revoquerToutesLesSessions(@Param("utilisateurId") Long utilisateurId);

    /** Menage des sessions expirees, par la tache planifiee. */
    @Modifying
    @Query("delete from Session s where s.expireLe < :limite")
    int supprimerExpirees(@Param("limite") Instant limite);
}
