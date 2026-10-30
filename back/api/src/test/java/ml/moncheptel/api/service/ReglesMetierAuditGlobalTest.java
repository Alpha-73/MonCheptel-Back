package ml.moncheptel.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import ml.moncheptel.api.dto.requete.ClotureRequete;
import ml.moncheptel.api.dto.requete.EvenementSanitaireRequete;
import ml.moncheptel.api.dto.requete.LotRequete;
import ml.moncheptel.api.dto.requete.ProfilRequete;
import ml.moncheptel.api.exception.AccesRefuseException;
import ml.moncheptel.api.exception.GestionnaireExceptions;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.modele.CalendrierSanitaire;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.Notification;
import ml.moncheptel.api.modele.RappelSanitaire;
import ml.moncheptel.api.modele.SignalementMaladie;
import ml.moncheptel.api.modele.TypeElevage;
import ml.moncheptel.api.modele.UniteElevage;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.modele.Zone;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.Langue;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.modele.enumeration.StatutRappel;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import ml.moncheptel.api.modele.enumeration.TrancheEffectif;
import ml.moncheptel.api.modele.enumeration.TypeEvenement;
import ml.moncheptel.api.modele.enumeration.TypeMouvement;
import ml.moncheptel.api.modele.enumeration.TypeNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regles metier de l'audit global, sur le contexte complet (H2, transaction
 * annulee) : cloture d'un lot qui a des tetes (G5), carnet ecrit par le
 * veterinaire seulement avec un cas ouvert (G3), carnet clos (G10), filiere
 * non retirable (G7), rappels notifies (G9), accueil veterinaire compte en
 * base (point 15), contrainte de base rendue en 409.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReglesMetierAuditGlobalTest {

    @Autowired private EntityManager em;
    @Autowired private CheptelService cheptel;
    @Autowired private SanitaireService sanitaire;
    @Autowired private UtilisateurService utilisateurs;
    @Autowired private SignalementService signalements;
    @Autowired private NotificationService notifications;

    private Zone zone;
    private Eleveur eleveur;
    private TypeElevage type;
    private final LocalDate aujourdHui = LocalDate.now();

    @BeforeEach
    void preparer() {
        zone = new Zone("Zone-audit", "Segou");
        em.persist(zone);
        eleveur = new Eleveur("Awa Traore", "awa-audit@example.ml", "{noop}x", zone,
                Set.of(Filiere.VOLAILLE, Filiere.BOVINS));
        em.persist(eleveur);
        type = new TypeElevage(Filiere.VOLAILLE, "Poulet audit", ModeEvaluation.PAR_PERIODE);
        type.setDureeCycleJours(45);
        em.persist(type);
        em.persist(new CalendrierSanitaire(type, "Newcastle", TypeEvenement.VACCINATION, 3, 0));
        em.flush();
    }

    /** Lot de 40 tetes mis en place avant-hier : son rappel « J3 » tombe demain. */
    private UniteElevage lot() {
        Long id = cheptel.creerLot(eleveur, new LotRequete(null, type.getId(), 40,
                aujourdHui.minusDays(2))).id();
        em.flush();
        return em.find(UniteElevage.class, id);
    }

    private List<RappelSanitaire> rappels(UniteElevage u) {
        return em.createQuery("select r from RappelSanitaire r where r.unite.id = :id",
                RappelSanitaire.class).setParameter("id", u.getId()).getResultList();
    }

    @Test
    @DisplayName("G5 : cloturer un lot qui a des tetes demande ce qu'elles sont devenues")
    void clotureAvecTetes() {
        UniteElevage lot = lot();
        assertThatThrownBy(() -> cheptel.terminer(eleveur, lot.getId(), null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("compte encore 40 têtes");

        var resume = cheptel.terminer(eleveur, lot.getId(),
                new ClotureRequete(TypeMouvement.VENTE, 400_000L, null));
        em.flush();

        assertThat(resume.statut()).isEqualTo(StatutUnite.CLOTURE);
        assertThat(resume.effectifRestant()).isZero();
        // Les 40 tetes sont comptees comme sorties (requete du tableau de bord).
        assertThat(cheptel.compterTetesSorties(eleveur, Filiere.VOLAILLE, aujourdHui.minusDays(1)))
                .isEqualTo(40);
        assertThat(rappels(lot)).extracting(RappelSanitaire::getStatut)
                .containsOnly(StatutRappel.IGNORE);
    }

    @Test
    @DisplayName("G3/G10 : le veterinaire n'ecrit qu'avec un cas ouvert ; carnet clos apres la sortie")
    void carnetSanitaire() {
        UniteElevage lot = lot();
        Veterinaire veto = new Veterinaire("Dr Diallo", "veto-audit@example.ml", "{noop}x", zone, zone);
        em.persist(veto);
        var acte = new EvenementSanitaireRequete(TypeEvenement.TRAITEMENT, aujourdHui, "Oxytetracycline", null);

        // Meme zone, mais aucun cas : refuse (l'ancienne regle de zone ouvrait tout).
        assertThatThrownBy(() -> sanitaire.enregistrer(veto, lot.getId(), acte))
                .isInstanceOf(AccesRefuseException.class);

        SignalementMaladie cas = new SignalementMaladie(eleveur, lot, Set.of(1L),
                TrancheEffectif.DE_10_A_50, null);
        cas.enregistrerDiagnostic(null, null, null, null);
        cas.orienterVers(veto);
        em.persist(cas);
        em.flush();

        Long idActe = sanitaire.enregistrer(veto, lot.getId(), acte).id();
        assertThatThrownBy(() -> sanitaire.modifier(eleveur, idActe, acte))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("seul lui peut le modifier");

        // Point 15 : l'accueil compte le cas ouvert, en base.
        assertThat(signalements.accueil(veto, 0, 0).enAttente()).isEqualTo(1);

        cas.prendreEnCharge();
        cas.marquerTraite("Traite sur place");
        em.flush();
        assertThatThrownBy(() -> sanitaire.modifier(veto, idActe, acte))
                .isInstanceOf(AccesRefuseException.class);
        assertThat(signalements.accueil(veto, 0, 0).traitesCeMois()).isEqualTo(1);

        Long idActeEleveur = sanitaire.enregistrer(eleveur, lot.getId(), acte).id();
        cheptel.terminer(eleveur, lot.getId(), new ClotureRequete(TypeMouvement.MORT, null, null));
        em.flush();
        assertThatThrownBy(() -> sanitaire.modifier(eleveur, idActeEleveur, acte))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("carnet sanitaire est clos");
    }

    @Test
    @DisplayName("G7 : une filiere qui porte un lot actif ne se retire pas du profil")
    void filiereNonRetirable() {
        UniteElevage lot = lot();
        ProfilRequete sansVolaille = new ProfilRequete("Awa Traore", "awa-audit@example.ml",
                null, zone.getId(), Langue.FR, Set.of(Filiere.BOVINS), null);

        assertThatThrownBy(() -> utilisateurs.modifierProfil(eleveur, sansVolaille))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("retirer la filière Volaille")
                .hasMessageContaining(lot.getCode());
    }

    @Test
    @DisplayName("G9 : une notification RAPPEL par eleveur et par jour, liee a l'unite")
    void rappelsNotifies() {
        UniteElevage lot = lot();

        assertThat(sanitaire.notifierRappels(aujourdHui, notifications)).isEqualTo(1);
        em.flush();

        List<Notification> recues = em.createQuery(
                "select n from Notification n where n.destinataire.id = :id", Notification.class)
                .setParameter("id", eleveur.getId()).getResultList();
        assertThat(recues).hasSize(1);
        Notification n = recues.get(0);
        assertThat(n.getType()).isEqualTo(TypeNotification.RAPPEL);
        assertThat(n.getTitre()).isEqualTo("Soins à faire demain");
        assertThat(n.getReferenceType()).isEqualTo("UNITE");
        assertThat(n.getReferenceId()).isEqualTo(lot.getId());

        // Lot sorti : plus rien a rappeler.
        lot.terminer(aujourdHui);
        em.flush();
        assertThat(sanitaire.notifierRappels(aujourdHui, notifications)).isZero();
    }

    @Test
    @DisplayName("une contrainte de base violee rend 409, sans detail SQL")
    void contrainteEn409() {
        var reponse = new GestionnaireExceptions(new MockEnvironment()).contrainteViolee(
                new org.springframework.dao.DataIntegrityViolationException("Duplicate entry 'x' for key 'uk'"),
                new MockHttpServletRequest("POST", "/api/cheptel"));
        assertThat(reponse.getStatusCode().value()).isEqualTo(409);
        assertThat(reponse.getBody().message()).doesNotContain("Duplicate");
    }

    @Test
    @DisplayName("G14 : sans SMTP configure, pas de mot de passe provisoire envoye dans le vide")
    void courrielNonConfigure() {
        assertThatThrownBy(() -> utilisateurs.reinitialiserMotDePasse(eleveur.getId()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("n'est pas configuré");
        em.flush();
        assertThat(em.find(Eleveur.class, eleveur.getId()).isDoitChangerMotDePasse()).isFalse();
    }
}
