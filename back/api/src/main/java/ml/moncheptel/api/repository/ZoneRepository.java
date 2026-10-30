package ml.moncheptel.api.repository;

import java.util.List;
import java.util.Optional;
import ml.moncheptel.api.modele.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {

    Optional<Zone> findByNomIgnoreCase(String nom);

    List<Zone> findAllByOrderByNomAsc();
}
