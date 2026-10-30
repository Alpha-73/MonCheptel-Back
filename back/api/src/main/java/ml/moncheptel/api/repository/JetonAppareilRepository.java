package ml.moncheptel.api.repository;

import java.util.List;
import java.util.Optional;
import ml.moncheptel.api.modele.JetonAppareil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JetonAppareilRepository extends JpaRepository<JetonAppareil, Long> {

    Optional<JetonAppareil> findByJeton(String jeton);

    List<JetonAppareil> findByUtilisateurIdAndActifTrue(Long utilisateurId);

    /**
     * Jetons a viser pour un envoi groupe.
     *
     * <p>Un envoi d'alerte touche des centaines d'eleveurs : les recuperer en
     * une requete plutot qu'un par un evite le probleme du N+1, qui ferait
     * 318 allers-retours pour une seule alerte.
     */
    @Query("""
           select j from JetonAppareil j
           where j.actif = true and j.utilisateur.id in :utilisateurIds
           """)
    List<JetonAppareil> chercherPourUtilisateurs(@Param("utilisateurIds") List<Long> utilisateurIds);
}
