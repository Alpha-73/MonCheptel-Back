package ml.moncheptel.api.modele;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.modele.enumeration.NiveauDifficulte;

/**
 * Type d'elevage du referentiel — « Poulet de chair », « Bovin production ».
 *
 * <p>Tous les champs viennent de l'ecran « Admin - Referentiel fiche ». C'est
 * le pivot de trois fonctions : le simulateur d'investissement, le calendrier
 * sanitaire, et le mode de calcul de la rentabilite.
 *
 * <p>Les sept types de la maquette : Volaille — Chair, Volaille — Locale,
 * Bovin — Embouche, Bovin — Production, Ovin — Embouche, Ovin — Production,
 * Caprin — Production.
 */
@Entity
@Table(name = "type_elevage")
public class TypeElevage extends EntiteBase {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Filiere filiere;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String libelle;

    /** « Duree du cycle : 45 jours ». Nul pour un elevage sans cycle. */
    @Column(name = "duree_cycle_jours")
    private Integer dureeCycleJours;

    /**
     * Comment se juge la rentabilite. La note de « ModifierAnimal - Rempli »
     * l'explique : « Le type d'elevage determine la frequence d'evaluation de
     * la rentabilite. »
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode_evaluation", nullable = false, length = 14)
    private ModeEvaluation modeEvaluation = ModeEvaluation.PAR_PERIODE;

    /**
     * Capacite pour laquelle l'installation est dimensionnee.
     *
     * <p>On ne batit pas un poulailler de 3 m2 : l'installation se calcule sur
     * une capacite choisie une fois, pas sur la bande en cours. C'est ce que
     * dit l'encart « PAYEE UNE FOIS, REUTILISABLE », et c'est ce qui rend les
     * 10 m2 de l'ecran « Investir » calculables.
     */
    @Column(name = "capacite_reference", nullable = false)
    private int capaciteReference = 100;

    // ── parametres logistiques (« Admin - Referentiel fiche ») ───────────────

    /** « Espace par sujet : 0,1 m2 ». */
    @Column(name = "espace_par_sujet_m2", precision = 6, scale = 3)
    private BigDecimal espaceParSujetM2;

    /** « Temperature : 25 a 30 degres ». */
    @Column(name = "temperature_min")
    private Integer temperatureMin;

    @Column(name = "temperature_max")
    private Integer temperatureMax;

    /** « Aliment par sujet : 1,8 kg » — sur toute la duree du cycle. */
    @Column(name = "aliment_par_sujet_kg", precision = 8, scale = 3)
    private BigDecimal alimentParSujetKg;

    /** « Eau par sujet et par jour : 0,17 L ». */
    @Column(name = "eau_par_sujet_jour_l", precision = 6, scale = 3)
    private BigDecimal eauParSujetJourL;

    /** « Vaccins obligatoires : Newcastle J7 et J21 ». */
    @Size(max = 500)
    @Column(name = "vaccins_obligatoires", length = 500)
    private String vaccinsObligatoires;

    /** « Mortalite previsionnelle : 8 % » — stockee en fraction (0,0800). */
    @Column(name = "taux_mortalite", nullable = false, precision = 5, scale = 4)
    private BigDecimal tauxMortalite = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private NiveauDifficulte difficulte = NiveauDifficulte.MOYEN;

    @Column(nullable = false)
    private boolean actif = true;

    @OneToMany(mappedBy = "typeElevage", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("phase ASC, ordre ASC")
    private List<PosteCout> postesCout = new ArrayList<>();

    protected TypeElevage() {
    }

    public TypeElevage(Filiere filiere, String libelle, ModeEvaluation modeEvaluation) {
        this.filiere = filiere;
        this.libelle = libelle;
        this.modeEvaluation = modeEvaluation;
    }

    // ── comportement ────────────────────────────────────────────────────────

    /**
     * Nombre de sujets a acheter pour en vendre {@code sujetsVises}, mortalite
     * previsionnelle comprise.
     *
     * <p>« 33 sujets achetes pour 30 vendus (8 % de mortalite) ». On arrondit
     * au superieur : acheter 32,4 poussins n'a pas de sens, et arrondir au
     * plus proche ferait rater l'objectif une fois sur deux.
     *
     * <p>Sur une filiere suivie BETE PAR BETE, la mortalite previsionnelle
     * n'est pas appliquee : elle produirait « achetez deux chevres pour en
     * garder une ». Voir le corps de la methode.
     */
    public int sujetsAAcheter(int sujetsVises) {
        if (sujetsVises <= 0) {
            throw new IllegalArgumentException("Le nombre de sujets visés doit être supérieur à zéro.");
        }
        if (!filiere.suiviParLot()) {
            // Provisionner la mortalite n'a de sens que sur une BANDE. Sur une
            // filiere suivie bete par bete, arrondir au superieur donne :
            // « pour garder une chevre, achetez-en deux » — CEILING(1 x 1,05)
            // vaut 2. Le simulateur doublait alors tous les couts par sujet,
            // et annoncait 83 000 FCFA la ou la maquette dit 48 000.
            //
            // Un eleveur qui achete un belier sait qu'il peut le perdre ; il
            // n'en achete pas deux pour autant. La mortalite previsionnelle
            // est une statistique de bande, pas une assurance individuelle.
            return sujetsVises;
        }
        BigDecimal facteur = BigDecimal.ONE.add(tauxMortalite);
        return BigDecimal.valueOf(sujetsVises)
                .multiply(facteur)
                .setScale(0, RoundingMode.CEILING)
                .intValueExact();
    }

    /** Date de cloture prevue d'un cycle demarre a cette date. */
    public java.time.LocalDate clotureDuCycle(java.time.LocalDate miseEnPlace) {
        return dureeCycleJours == null ? null : miseEnPlace.plusDays(dureeCycleJours);
    }

    public void ajouterPosteCout(PosteCout poste) {
        postesCout.add(poste);
    }

    // ── accesseurs ──────────────────────────────────────────────────────────

    public Filiere getFiliere() { return filiere; }
    public void setFiliere(Filiere f) { this.filiere = f; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String l) { this.libelle = l; }

    public Integer getDureeCycleJours() { return dureeCycleJours; }
    public void setDureeCycleJours(Integer d) { this.dureeCycleJours = d; }

    public ModeEvaluation getModeEvaluation() { return modeEvaluation; }
    public void setModeEvaluation(ModeEvaluation m) { this.modeEvaluation = m; }

    public int getCapaciteReference() { return capaciteReference; }
    public void setCapaciteReference(int c) { this.capaciteReference = c; }

    public BigDecimal getEspaceParSujetM2() { return espaceParSujetM2; }
    public void setEspaceParSujetM2(BigDecimal v) { this.espaceParSujetM2 = v; }

    public Integer getTemperatureMin() { return temperatureMin; }
    public void setTemperatureMin(Integer v) { this.temperatureMin = v; }

    public Integer getTemperatureMax() { return temperatureMax; }
    public void setTemperatureMax(Integer v) { this.temperatureMax = v; }

    public BigDecimal getAlimentParSujetKg() { return alimentParSujetKg; }
    public void setAlimentParSujetKg(BigDecimal v) { this.alimentParSujetKg = v; }

    public BigDecimal getEauParSujetJourL() { return eauParSujetJourL; }
    public void setEauParSujetJourL(BigDecimal v) { this.eauParSujetJourL = v; }

    public String getVaccinsObligatoires() { return vaccinsObligatoires; }
    public void setVaccinsObligatoires(String v) { this.vaccinsObligatoires = v; }

    public BigDecimal getTauxMortalite() { return tauxMortalite; }
    public void setTauxMortalite(BigDecimal v) { this.tauxMortalite = v; }

    public NiveauDifficulte getDifficulte() { return difficulte; }
    public void setDifficulte(NiveauDifficulte d) { this.difficulte = d; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public List<PosteCout> getPostesCout() { return postesCout; }
}
