package ml.moncheptel.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import ml.moncheptel.api.dto.reponse.ErreurReponse;
import ml.moncheptel.api.exception.GestionnaireExceptions;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.Lot;
import ml.moncheptel.api.modele.Maladie;
import ml.moncheptel.api.modele.TypeElevage;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;
import ml.moncheptel.api.repository.MaladieRepository;
import ml.moncheptel.api.repository.MaladieSymptomeRepository;
import ml.moncheptel.api.repository.projection.ScoreMaladieProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** Seuil du diagnostic (L4), bornes des dates (F11), conflit de version (M4). */
class RegleDiagnosticEtDatesTest {

    private static ScoreMaladieProjection score(long maladieId, String poids, long correspondances) {
        return new ScoreMaladieProjection() {
            public Long getMaladieId() { return maladieId; }
            public BigDecimal getPoidsCumule() { return new BigDecimal(poids); }
            public long getCorrespondances() { return correspondances; }
        };
    }

    private static DiagnosticService moteur(List<ScoreMaladieProjection> lignes, Maladie... connues) {
        MaladieSymptomeRepository ms = mock(MaladieSymptomeRepository.class);
        MaladieRepository m = mock(MaladieRepository.class);
        when(ms.noterCandidates(anySet(), any())).thenReturn(lignes);
        when(m.findAllById(anyCollection())).thenReturn(List.of(connues));
        return new DiagnosticService(ms, m);
    }

    private static Maladie maladie(long id, String nom) {
        Maladie m = new Maladie(nom, NiveauGravite.MOYEN, Set.of(Filiere.VOLAILLE)) {
            @Override public Long getId() { return id; }
        };
        return m;
    }

    @Test
    @DisplayName("une seule maladie faiblement liee a 1 symptome sur 5 n'est pas « probable »")
    void correspondanceFaibleRejetee() {
        DiagnosticService d = moteur(List.of(score(1, "0.35", 1)), maladie(1, "Coccidiose"));
        DiagnosticService.Verdict v = d.verdict(Set.of(1L, 2L, 3L, 4L, 5L), Filiere.VOLAILLE);
        assertThat(v.probable()).isNull();
    }

    @Test
    @DisplayName("un symptome banal seul (poids 0,35) ne suffit pas, un symptome specifique (0,9) oui")
    void poidsMinimal() {
        assertThat(moteur(List.of(score(1, "0.35", 1)), maladie(1, "Coccidiose"))
                .verdict(Set.of(1L), Filiere.VOLAILLE).probable()).isNull();
        assertThat(moteur(List.of(score(2, "0.90", 1)), maladie(2, "Newcastle"))
                .verdict(Set.of(1L), Filiere.VOLAILLE).probable().getNom()).isEqualTo("Newcastle");
    }

    /**
     * Les poids REELS de V3 (audit de relecture, point 5) : « Abattement »
     * seul valait 0,55 pour la coccidiose, et passait l'ancien seuil de 0,5.
     */
    @Test
    @DisplayName("avec les poids de V3 : abattement seul ou fievre seule ne designent rien")
    void poidsReelsSignesBanaux() {
        // Abattement coche seul : coccidiose 0,55, Newcastle 0,40, bronchite 0,30.
        assertThat(moteur(List.of(score(1, "0.55", 1), score(2, "0.40", 1), score(3, "0.30", 1)),
                maladie(1, "Coccidiose"), maladie(2, "Newcastle"), maladie(3, "Bronchite"))
                .verdict(Set.of(5L), Filiere.VOLAILLE).probable()).isNull();
        // Fievre seule : peste des petits ruminants 0,75.
        assertThat(moteur(List.of(score(6, "0.75", 1)), maladie(6, "PPR"))
                .verdict(Set.of(8L), Filiere.CAPRINS).probable()).isNull();
    }

    @Test
    @DisplayName("avec les poids de V3 : un signe evocateur seul, ou deux signes banaux concordants, suffisent")
    void poidsReelsSignesEvocateurs() {
        // Torticolis : Newcastle 0,95.
        assertThat(moteur(List.of(score(2, "0.95", 1)), maladie(2, "Newcastle"))
                .verdict(Set.of(4L), Filiere.VOLAILLE).probable().getNom()).isEqualTo("Newcastle");
        // Morts subites : Newcastle 0,80, juste au seuil.
        assertThat(moteur(List.of(score(2, "0.80", 1)), maladie(2, "Newcastle"))
                .verdict(Set.of(7L), Filiere.VOLAILLE).probable().getNom()).isEqualTo("Newcastle");
        // Abattement + perte d'appetit : coccidiose 0,55 + 0,50 = 1,05 sur 2 coches.
        assertThat(moteur(List.of(score(1, "1.05", 2), score(2, "0.40", 1)),
                maladie(1, "Coccidiose"), maladie(2, "Newcastle"))
                .verdict(Set.of(5L, 10L), Filiere.VOLAILLE).probable().getNom()).isEqualTo("Coccidiose");
    }

    @Test
    @DisplayName("une correspondance forte reste proposee, l'alternative faible est ecartee")
    void correspondanceForte() {
        DiagnosticService d = moteur(List.of(score(2, "2.40", 3), score(1, "0.40", 1)),
                maladie(1, "Coccidiose"), maladie(2, "Newcastle"));
        DiagnosticService.Verdict v = d.verdict(Set.of(1L, 2L, 3L, 4L), Filiere.VOLAILLE);
        assertThat(v.probable().getNom()).isEqualTo("Newcastle");
        assertThat(v.alternative()).isNull();
    }

    private static Lot lot() {
        TypeElevage t = new TypeElevage(Filiere.VOLAILLE, "Poulet de chair", ModeEvaluation.PAR_PERIODE);
        t.setDureeCycleJours(45);
        Eleveur e = new Eleveur("Balla Kone", "70000000", "x", null, Set.of(Filiere.VOLAILLE));
        return new Lot("LOT-014", e, t, 100, LocalDate.of(2026, 9, 3));
    }

    @Test
    @DisplayName("une date future ou anterieure a l'entree est refusee avec un message clair (F11)")
    void bornesDesDates() {
        Lot lot = lot();
        assertThatThrownBy(() -> ControleDates.exigerDansLaVieDe(lot, LocalDate.now().plusDays(1), "de la sortie"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessage("La date de la sortie ne peut pas être dans le futur.");
        assertThatThrownBy(() -> ControleDates.exigerDansLaVieDe(lot, LocalDate.of(2026, 9, 1), "du soin"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessage("La date du soin (1 septembre 2026) précède l'entrée de LOT-014 (3 septembre 2026).");
        ControleDates.exigerDansLaVieDe(lot, LocalDate.of(2026, 9, 3), "du soin");
    }

    @Test
    @DisplayName("un conflit de version sur un signalement rend 409 avec un message clair (M4)")
    void conflitDeVersion() {
        Environment env = mock(Environment.class);
        GestionnaireExceptions g = new GestionnaireExceptions(env);
        MockHttpServletRequest requete = new MockHttpServletRequest("POST", "/api/signalements/4/cloture");
        ResponseEntity<ErreurReponse> r = g.conflitConcurrent(
                new ObjectOptimisticLockingFailureException("SignalementMaladie", 4L), requete);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().message()).contains("Ce cas vient d'être modifié");
    }
}
