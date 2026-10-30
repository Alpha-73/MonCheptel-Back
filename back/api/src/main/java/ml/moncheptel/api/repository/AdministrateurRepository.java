package ml.moncheptel.api.repository;

import ml.moncheptel.api.modele.Administrateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Comptes administrateur : sert a savoir s'il en existe au moins un (audit global G1). */
@Repository
public interface AdministrateurRepository extends JpaRepository<Administrateur, Long> {
}
