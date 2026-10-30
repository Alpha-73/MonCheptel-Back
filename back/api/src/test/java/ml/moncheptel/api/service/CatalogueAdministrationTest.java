package ml.moncheptel.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.EnumSet;
import java.util.List;
import ml.moncheptel.api.dto.reponse.EtapeProtocoleReponse;
import ml.moncheptel.api.dto.requete.EtapeProtocoleRequete;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.modele.CalendrierSanitaire;
import ml.moncheptel.api.modele.Competence;
import ml.moncheptel.api.modele.Maladie;
import ml.moncheptel.api.modele.TypeElevage;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.modele.Zone;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;
import ml.moncheptel.api.modele.enumeration.TypeEvenement;
import ml.moncheptel.api.repository.CompetenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Protocole sanitaire modifiable et retrait d'une maladie (console). H2, transaction annulee. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogueAdministrationTest {

    @Autowired private EntityManager em;
    @Autowired private CatalogueAdministrationService service;
    @Autowired private CompetenceRepository competences;

    @Test
    @DisplayName("le protocole est rapproche par identifiant : modifie, ajoute, supprime")
    void remplacerProtocole() {
        TypeElevage type = new TypeElevage(Filiere.VOLAILLE, "Poulet test", ModeEvaluation.PAR_PERIODE);
        type.setDureeCycleJours(45);
        em.persist(type);
        CalendrierSanitaire j7 = new CalendrierSanitaire(type, "Newcastle", TypeEvenement.VACCINATION, 7, 0);
        CalendrierSanitaire j14 = new CalendrierSanitaire(type, "Gumboro", TypeEvenement.VACCINATION, 14, 1);
        em.persist(j7);
        em.persist(j14);
        em.flush();

        List<EtapeProtocoleReponse> apres = service.remplacerProtocole(type.getId(), List.of(
                new EtapeProtocoleRequete(j7.getId(), "Newcastle (rappel)", TypeEvenement.VACCINATION, 21, true),
                new EtapeProtocoleRequete(null, "Vermifuge", TypeEvenement.TRAITEMENT, 30, false)));

        assertThat(apres).extracting(EtapeProtocoleReponse::libelle)
                .containsExactly("Newcastle (rappel)", "Vermifuge");
        assertThat(apres.get(0).id()).isEqualTo(j7.getId());
        assertThat(em.find(CalendrierSanitaire.class, j14.getId())).isNull();
    }

    @Test
    @DisplayName("une etape apres la fin du cycle ou de type maladie est refusee")
    void refus() {
        TypeElevage type = new TypeElevage(Filiere.VOLAILLE, "Pondeuse test", ModeEvaluation.PAR_PERIODE);
        type.setDureeCycleJours(45);
        em.persist(type);
        assertThatThrownBy(() -> service.remplacerProtocole(type.getId(), List.of(
                new EtapeProtocoleRequete(null, "Trop tard", TypeEvenement.VACCINATION, 60, true))))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> service.remplacerProtocole(type.getId(), List.of(
                new EtapeProtocoleRequete(null, "Coccidiose", TypeEvenement.MALADIE, 3, true))))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    @DisplayName("retirer une maladie supprime les competences declarees dessus")
    void retirerMaladie() {
        Zone zone = new Zone("Segou-test", "Segou");
        em.persist(zone);
        Maladie maladie = new Maladie("Variole-test", NiveauGravite.MOYEN, EnumSet.of(Filiere.VOLAILLE));
        Maladie autre = new Maladie("Gale-test", NiveauGravite.FAIBLE, EnumSet.of(Filiere.OVINS));
        em.persist(maladie);
        em.persist(autre);
        Veterinaire veto = new Veterinaire("Dr Test", "veto-catalogue@exemple.ml", "x", zone, zone);
        em.persist(veto);
        em.persist(new Competence(veto, maladie));
        em.persist(new Competence(veto, autre));
        em.flush();

        assertThat(service.retirerMaladie(maladie.getId())).isEqualTo(1);
        assertThat(competences.existsByVeterinaireIdAndMaladieId(veto.getId(), maladie.getId())).isFalse();
        assertThat(competences.existsByVeterinaireIdAndMaladieId(veto.getId(), autre.getId())).isTrue();
        em.refresh(maladie);
        assertThat(maladie.isActive()).isFalse();
    }
}
