package ml.moncheptel.api.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import ml.moncheptel.api.modele.Maladie;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.repository.MaladieRepository;
import ml.moncheptel.api.repository.MaladieSymptomeRepository;
import ml.moncheptel.api.repository.projection.ScoreMaladieProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moteur de rapprochement symptomes / maladie.
 *
 * <h2>Ce que ce service est, et ce qu'il n'est pas</h2>
 *
 * <p>Ce n'est pas un diagnostic. C'est un rapprochement statistique entre des
 * symptomes coches et un catalogue pondere. Le vocabulaire de l'interface doit
 * le refleter — « maladie probable », jamais « maladie detectee » — et c'est
 * la raison pour laquelle deux hypotheses sont toujours renvoyees plutot
 * qu'une.
 *
 * <h2>La formule</h2>
 *
 * <p>Pour une maladie {@code m} et l'ensemble {@code S} des symptomes coches :
 *
 * <pre>
 *   poidsCumule(m) = somme des poids des symptomes de m qui sont dans S
 *   couverture(m)  = |S inter m| / |S|
 *   score(m)       = poidsCumule(m) x couverture(m)
 *   part(m)        = score(m) / somme des scores        (fraction de 0 a 1)
 * </pre>
 *
 * <p><b>La part n'est jamais affichee.</b> Elle sert a classer, et elle est
 * conservee sur le signalement pour pouvoir un jour confronter ce que le
 * moteur proposait a ce que le veterinaire a conclu. Voir
 * {@code DiagnosticReponse} pour le raisonnement.
 *
 * <p><b>Pourquoi le facteur de couverture.</b> Sans lui, une maladie a vingt
 * symptomes ecrase toujours une maladie a trois, puisqu'elle a plus
 * d'occasions d'accumuler du poids. Une premiere version normalisait
 * simplement les poids : elle rendait Newcastle a 38,8 % contre bronchite a
 * 36,4 % — deux nombres si proches qu'ils ne pouvaient rien orienter du tout.
 * Le facteur de couverture penalise la maladie qui n'explique qu'une petite
 * partie de ce que l'eleveur a coche.
 *
 * <p><b>Pourquoi les poids sont necessaires.</b> Sur des symptomes tres
 * specifiques, ils changent peu le classement. Sur des symptomes communs —
 * abattement, perte d'appetit, que partagent presque toutes les maladies —
 * ils sont la SEULE chose qui departage : sans eux, deux maladies ressortent
 * a 50/50, ce qui n'est pas une orientation.
 *
 * <p><b>Ce que les poids valent aujourd'hui.</b> Les 24 lignes de ponderation
 * du jeu de donnees initial n'ont PAS ete validees par un veterinaire. Elles
 * sont vraisemblables, pas etablies. Elles doivent etre relues avant toute
 * mise en service reelle ; la structure, elle, est correcte et n'a pas a
 * changer pour cela.
 */
@Service
@Transactional(readOnly = true)
public class DiagnosticService {

    /** En deca, la seconde hypothese est du bruit et n'est pas retenue : 5 %. */
    private static final BigDecimal SEUIL_ALTERNATIVE = new BigDecimal("0.05");

    /**
     * Seuils ABSOLUS d'une hypothese credible (audit eleveur L4).
     *
     * <p>La part est normalisee entre les candidates : une seule maladie
     * faiblement liee a un symptome sur cinq obtenait 100 % et devenait
     * « maladie probable ». Une hypothese n'est donc retenue que si elle
     * explique au moins la moitie des symptomes coches ET cumule un poids
     * d'au moins 0,8. En deca : « aucune maladie identifiee ».
     *
     * <p><b>Pourquoi 0,8, mesure sur les 24 poids reels de V3.</b> Le seuil
     * valait 0,5 : « Abattement » seul (0,55 pour la coccidiose) suffisait a
     * annoncer une coccidiose, et « Fievre » seule (0,75) une peste des petits
     * ruminants. A 0,8 :
     * <ul>
     *   <li>un signe evocateur seul suffit — torticolis 0,95 (Newcastle),
     *       sang dans les fientes 0,95 (coccidiose), vesicules 0,95 (fievre
     *       aphteuse), morts subites et boiterie 0,80 ;</li>
     *   <li>un signe banal seul ne suffit plus — abattement (0,30 a 0,55),
     *       perte d'appetit (0,40 a 0,50), fievre (0,60 a 0,75) ;</li>
     *   <li>deux signes banaux concordants, si : abattement + perte d'appetit
     *       cumulent 1,05 pour la coccidiose.</li>
     * </ul>
     * Ces poids restent a faire valider par un veterinaire ; le seuil devra
     * etre revu avec eux (test {@code RegleDiagnosticEtDatesTest}).
     */
    static final BigDecimal COUVERTURE_MINIMALE = new BigDecimal("0.5");
    static final BigDecimal POIDS_MINIMAL = new BigDecimal("0.8");

    private final MaladieSymptomeRepository maladieSymptomeRepository;
    private final MaladieRepository maladieRepository;

    public DiagnosticService(MaladieSymptomeRepository maladieSymptomeRepository,
                             MaladieRepository maladieRepository) {
        this.maladieSymptomeRepository = maladieSymptomeRepository;
        this.maladieRepository = maladieRepository;
    }

    /**
     * Classe les maladies candidates, la plus probable en tete.
     *
     * @param symptomeIds symptomes coches par l'eleveur, non vide
     * @param filiere     filiere de l'unite concernee — indispensable : sans
     *                    ce filtre, des symptomes de volaille remontaient une
     *                    maladie bovine, ce qui a rendu la premiere version
     *                    inutilisable
     * @return liste possiblement vide, quand aucune maladie du catalogue
     *         n'explique le moindre symptome coche
     */
    public List<Hypothese> classer(Set<Long> symptomeIds, Filiere filiere) {
        if (symptomeIds == null || symptomeIds.isEmpty()) {
            throw new IllegalArgumentException("Un signalement porte au moins un symptome");
        }

        List<ScoreMaladieProjection> lignes =
                maladieSymptomeRepository.noterCandidates(symptomeIds, filiere);
        if (lignes.isEmpty()) {
            return List.of();
        }

        BigDecimal nombreCoches = BigDecimal.valueOf(symptomeIds.size());

        // 1. score brut de chaque candidate
        record Brut(Long maladieId, BigDecimal score, BigDecimal couverture, BigDecimal poids) { }
        List<Brut> bruts = new ArrayList<>(lignes.size());
        BigDecimal total = BigDecimal.ZERO;

        for (ScoreMaladieProjection l : lignes) {
            BigDecimal couverture = BigDecimal.valueOf(l.getCorrespondances())
                    .divide(nombreCoches, 6, RoundingMode.HALF_UP);
            BigDecimal score = l.getPoidsCumule().multiply(couverture);
            bruts.add(new Brut(l.getMaladieId(), score, couverture, l.getPoidsCumule()));
            total = total.add(score);
        }

        if (total.signum() == 0) {
            // Toutes les ponderations valent zero : le referentiel est mal
            // renseigne. Mieux vaut ne rien proposer qu'un classement arbitraire.
            return List.of();
        }

        // 2. une seule requete pour toutes les maladies retenues : pas de N+1
        Map<Long, Maladie> maladies = maladieRepository
                .findAllById(bruts.stream().map(Brut::maladieId).toList())
                .stream()
                .collect(Collectors.toMap(Maladie::getId, Function.identity()));

        final BigDecimal somme = total;
        return bruts.stream()
                .map(b -> new Hypothese(
                        maladies.get(b.maladieId()),
                        // Fraction de 0 a 1, arrondie a trois decimales : c'est
                        // ce que la colonne DECIMAL(4,3) accepte. Un pourcentage
                        // (65,5) y deborderait — DECIMAL(4,3) plafonne a 9,999.
                        b.score().divide(somme, 3, RoundingMode.HALF_UP),
                        b.couverture(), b.poids()))
                .filter(h -> h.maladie() != null)
                .sorted(Comparator.comparing(Hypothese::part).reversed()
                        .thenComparing(h -> h.maladie().getNom()))
                .toList();
    }

    /**
     * Les deux hypotheses a figer sur un signalement.
     *
     * <p>La seconde est omise sous 5 % : proposer « ou bien ceci » a une
     * candidate qui n'explique presque rien dilue la premiere sans rien
     * apporter.
     */
    public Verdict verdict(Set<Long> symptomeIds, Filiere filiere) {
        List<Hypothese> classement = classer(symptomeIds, filiere).stream()
                .filter(Hypothese::credible).toList();
        if (classement.isEmpty()) {
            return new Verdict(null, null, null, null);
        }
        Hypothese premiere = classement.get(0);
        Hypothese seconde = classement.size() > 1 ? classement.get(1) : null;
        if (seconde != null && seconde.part().compareTo(SEUIL_ALTERNATIVE) < 0) {
            seconde = null;
        }
        return new Verdict(
                premiere.maladie(), premiere.part(),
                seconde == null ? null : seconde.maladie(),
                seconde == null ? null : seconde.part());
    }

    /**
     * Une maladie candidate, sa part du total (fraction de 0 a 1), la part
     * des symptomes coches qu'elle explique et son poids cumule.
     */
    public record Hypothese(Maladie maladie, BigDecimal part,
                            BigDecimal couverture, BigDecimal poidsCumule) {

        public Hypothese(Maladie maladie, BigDecimal part) {
            this(maladie, part, BigDecimal.ONE, BigDecimal.ONE);
        }

        /** Assez etayee pour etre proposee a l'eleveur (voir les seuils). */
        public boolean credible() {
            return couverture != null && poidsCumule != null
                    && couverture.compareTo(COUVERTURE_MINIMALE) >= 0
                    && poidsCumule.compareTo(POIDS_MINIMAL) >= 0;
        }
    }

    /** Les deux hypotheses retenues. Tous les champs peuvent etre nuls. */
    public record Verdict(Maladie probable, BigDecimal probabilite,
                          Maladie alternative, BigDecimal probabiliteAlternative) { }
}
