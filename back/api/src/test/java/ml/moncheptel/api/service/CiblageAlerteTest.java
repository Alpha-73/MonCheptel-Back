package ml.moncheptel.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.modele.Administrateur;
import ml.moncheptel.api.modele.Alerte;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.Maladie;
import ml.moncheptel.api.modele.Zone;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;
import ml.moncheptel.api.repository.AlerteRepository;
import ml.moncheptel.api.repository.EleveurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciblage d'une alerte (audit console v2, A1) : « toutes les filieres » veut
 * dire toutes les filieres DE LA MALADIE, jamais toute la zone. Sur H2, dans
 * une transaction annulee a la fin de chaque test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CiblageAlerteTest {

    @Autowired private EntityManager em;
    @Autowired private EleveurRepository eleveurs;
    @Autowired private AlerteRepository alertes;
    @Autowired private AlerteService service;

    private Zone zone;
    private Zone ailleurs;
    private Maladie newcastle;
    private Maladie ppr;

    @BeforeEach
    void preparer() {
        zone = persister(new Zone("Koutiala-test", "Sikasso"));
        ailleurs = persister(new Zone("Kayes-test", "Kayes"));
        newcastle = persister(new Maladie("Newcastle-test", NiveauGravite.ELEVE, EnumSet.of(Filiere.VOLAILLE)));
        ppr = persister(new Maladie("PPR-test", NiveauGravite.ELEVE, EnumSet.of(Filiere.OVINS, Filiere.CAPRINS)));

        eleveur("volaille", zone, Filiere.VOLAILLE);
        eleveur("bovins", zone, Filiere.BOVINS);
        eleveur("ovins", zone, Filiere.OVINS);
        eleveur("mixte", zone, Filiere.CAPRINS, Filiere.VOLAILLE);
        eleveur("loin", ailleurs, Filiere.VOLAILLE);
        Eleveur inactif = eleveur("inactif", zone, Filiere.OVINS);
        inactif.setActif(false);
        em.flush();
    }

    @Test
    @DisplayName("toutes ses filieres : seuls les eleveurs des filieres de la maladie")
    void toutesSesFilieres() {
        // Newcastle (volaille) : volaille + mixte, pas bovins ni ovins.
        assertThat(eleveurs.compterCible(zone.getId(), newcastle.getId(), null)).isEqualTo(2);
        // PPR (ovins, caprins) : ovins + mixte ; l'inactif ne compte pas ; mixte une seule fois.
        assertThat(eleveurs.compterCible(zone.getId(), ppr.getId(), null)).isEqualTo(2);
        assertThat(eleveurs.chercherCible(zone.getId(), ppr.getId(), null))
                .extracting(Eleveur::getNom).containsExactlyInAnyOrder("ovins", "mixte");
    }

    @Test
    @DisplayName("une filiere precise de la maladie : ses seuls eleveurs")
    void filierePrecise() {
        assertThat(eleveurs.compterCible(zone.getId(), ppr.getId(), Filiere.CAPRINS)).isEqualTo(1);
        assertThat(service.compterEleveursCibles(ppr.getId(), zone.getId(), Filiere.OVINS)).isEqualTo(1);
    }

    @Test
    @DisplayName("une filiere que la maladie ne touche pas est refusee (422)")
    void filiereEtrangere() {
        assertThatThrownBy(() -> service.compterEleveursCibles(newcastle.getId(), zone.getId(), Filiere.BOVINS))
                .isInstanceOf(RegleMetierException.class);
        assertThat(eleveurs.compterCible(zone.getId(), newcastle.getId(), Filiere.BOVINS)).isZero();
    }

    @Test
    @DisplayName("une alerte toutes filieres ne s'affiche qu'aux eleveurs des filieres de la maladie")
    void affichageEleveur() {
        Administrateur admin = persister(new Administrateur("Admin test", "admin-test@exemple.ml", "x", zone));
        persister(new Alerte(newcastle, zone, null, "Newcastle signalee", 2, admin));
        em.flush();

        assertThat(alertes.chercherPourEleveur(zone.getId(), Set.of(Filiere.VOLAILLE))).hasSize(1);
        assertThat(alertes.chercherPourEleveur(zone.getId(), Set.of(Filiere.BOVINS))).isEmpty();
        assertThat(alertes.chercherPourEleveur(zone.getId(), Set.of(Filiere.BOVINS, Filiere.VOLAILLE))).hasSize(1);
    }

    private Eleveur eleveur(String nom, Zone z, Filiere... filieres) {
        return persister(new Eleveur(nom, nom + "@exemple.ml", "x", z, Set.copyOf(List.of(filieres))));
    }

    private <T> T persister(T entite) {
        em.persist(entite);
        return entite;
    }
}
