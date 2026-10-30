package ml.moncheptel.api.service;

import java.time.Duration;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import ml.moncheptel.api.dto.reponse.LigneStatistiqueReponse;
import ml.moncheptel.api.dto.reponse.SeriesStatistiquesReponse;
import ml.moncheptel.api.dto.reponse.SeriesStatistiquesReponse.Entonnoir;
import ml.moncheptel.api.dto.reponse.SeriesStatistiquesReponse.MaladieSignalee;
import ml.moncheptel.api.dto.reponse.SeriesStatistiquesReponse.Mois;
import ml.moncheptel.api.dto.reponse.SeriesStatistiquesReponse.Moteur;
import ml.moncheptel.api.dto.reponse.SeriesStatistiquesReponse.Semaine;
import ml.moncheptel.api.dto.reponse.SeriesStatistiquesReponse.Tranche;
import ml.moncheptel.api.dto.reponse.SignalementResumeReponse;
import ml.moncheptel.api.dto.reponse.StatistiquesReponse;
import ml.moncheptel.api.dto.reponse.TableauDeBordAdminReponse;
import ml.moncheptel.api.modele.SignalementMaladie;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;
import ml.moncheptel.api.repository.SignalementMaladieRepository;
import ml.moncheptel.api.repository.StatistiqueRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les deux ecrans de pilotage de l'administration.
 *
 * <h2>Pourquoi ces chiffres sont calcules a chaque appel</h2>
 *
 * <p>Aucun cache, aucune table d'agregats. Sur mille deux cents comptes et
 * quelques milliers de lignes, une vingtaine de {@code COUNT} indexes se
 * repondent en quelques dizaines de millisecondes. Un cache apporterait ici
 * une complexite — invalidation, fraicheur, incoherence entre deux tuiles —
 * pour un gain nul.
 *
 * <p><b>Ce qui changerait la donne :</b> des dizaines de milliers d'eleveurs,
 * ou des ecrans consultes en continu. On s'en apercevrait au temps de reponse,
 * et c'est a ce moment-la qu'il faudra un instantane nocturne — qui reglerait
 * du meme coup les variations « + 86 ce mois » et « + 5 pts » que ces ecrans
 * affichent et que l'API ne peut pas calculer aujourd'hui.
 *
 * <h2>La fenetre de trente jours</h2>
 *
 * <p>Tous les « ces 30 jours » de la maquette comptent a partir d'il y a
 * trente fois vingt-quatre heures, pas depuis le 1er du mois. Les deux se
 * defendent ; ce qui ne se defend pas, c'est de melanger les deux sur le meme
 * ecran. Une seule constante, {@link #FENETRE}, les tient tous.
 *
 * <p>Exception assumee : « + 86 ce mois » compte bien depuis le premier jour
 * du mois civil, parce que c'est ce que dit le mot « mois ».
 */
@Service
@Transactional(readOnly = true)
public class StatistiqueService {

    /** Fenetre des indicateurs « ces 30 jours ». */
    private static final Duration FENETRE = Duration.ofDays(30);

    /** Fenetre des graphiques qui ont besoin de plus de recul. */
    private static final Duration FENETRE_LONGUE = Duration.ofDays(90);

    /** Semaines et mois des series temporelles. */
    private static final int PERIODES = 12;

    /** Barres du graphique des maladies. */
    private static final int TOP_MALADIES_SERIES = 6;

    /** Le calendrier de l'application : les semaines commencent le lundi a Bamako. */
    private static final ZoneId FUSEAU = ZoneId.of("Africa/Bamako");

    /** Bornes hautes des tranches de delai, en minutes, et leurs libelles. */
    private static final long[] BORNES_MINUTES = {60, 6 * 60, 24 * 60, 3 * 24 * 60};
    private static final String[] TRANCHES = {"< 1 h", "1–6 h", "6–24 h", "1–3 j", "> 3 j"};

    /** Lignes du tableau « Derniers signalements ». */
    private static final int DERNIERS = 4;

    /** Lignes des barres « Maladies les plus signalees ». */
    private static final int TOP_MALADIES = 5;

    private final StatistiqueRepository statistiqueRepository;
    private final SignalementMaladieRepository signalementRepository;
    private final ConseilService conseilService;

    public StatistiqueService(StatistiqueRepository statistiqueRepository,
                              SignalementMaladieRepository signalementRepository,
                              ConseilService conseilService) {
        this.statistiqueRepository = statistiqueRepository;
        this.signalementRepository = signalementRepository;
        this.conseilService = conseilService;
    }

    public TableauDeBordAdminReponse tableauDeBord() {
        Instant depuis = Instant.now().minus(FENETRE);
        LocalDate debutDuMois = LocalDate.now().withDayOfMonth(1);

        long enLot = statistiqueRepository.sommerTetesEnLot(null);
        long individuels = statistiqueRepository.compterAnimauxSuivis(null);
        long volailleEnLot = statistiqueRepository.sommerTetesEnLot(Filiere.VOLAILLE);
        long volailleIndividuels = statistiqueRepository.compterAnimauxSuivis(Filiere.VOLAILLE);

        return new TableauDeBordAdminReponse(
                statistiqueRepository.compterEleveurs(),
                statistiqueRepository.compterEleveursInscritsDepuis(debutDuMois),
                enLot + individuels,
                volailleEnLot + volailleIndividuels,
                statistiqueRepository.compterAlertesActives(),
                statistiqueRepository.zonesSousAlerte(),
                statistiqueRepository.compterVeterinairesActifs(),
                statistiqueRepository.compterZonesCouvertes(),
                statistiqueRepository.compterSignalementsDepuis(depuis),
                statistiqueRepository.compterAlertesDepuis(depuis),
                statistiqueRepository.compterOrientesEnAttente(),
                signalementRepository.compterSansVeterinaire(),
                statistiqueRepository.compterConseilsPublies(),
                conseilService.compterEnModeration(),
                derniersSignalements(),
                lignes(statistiqueRepository.compterEleveursParFiliere()));
    }

    public StatistiquesReponse statistiques() {
        Instant depuis = Instant.now().minus(FENETRE);
        LocalDate debutDuMois = LocalDate.now().withDayOfMonth(1);

        long eleveurs = statistiqueRepository.compterEleveurs();
        long veterinaires = statistiqueRepository.compterVeterinaires();
        long administrateurs = statistiqueRepository.compterAdministrateurs();
        long total = eleveurs + veterinaires + administrateurs;

        // LE DENOMINATEUR EST CELUI DES CAS QUI ONT NECESSITE UN VETERINAIRE,
        // pas celui de tous les signalements. Voir le long commentaire de
        // StatistiqueRepository.compterNecessitantUnVeterinaireDepuis : un
        // cas benin n'est jamais oriente, donc jamais TRAITE, et il restait
        // au denominateur a vie. Le pourcentage portait un plafond
        // structurel que rien ne pouvait lever.
        long aOrienter = statistiqueRepository.compterNecessitantUnVeterinaireDepuis(depuis);
        long traites = statistiqueRepository.compterSignalementsTraitesDepuis(depuis);

        // LES TROIS TUILES COMPTENT LA MEME POPULATION.
        //
        // Mesure du 5 octobre : ce n'etait pas le cas. La premiere tuile
        // rendait `eleveurs`, les deux autres travaillaient sur `total`.
        // L'ecran affichait donc, cote a cote :
        //
        //   UTILISATEURS          les eleveurs seuls
        //   + N ce mois           les inscrits de TOUS les roles
        //                         (compterInscritsDepuis compte Utilisateur)
        //   TAUX DE RETENTION     actifs / total, donc tous les roles
        //
        // Trois populations sous un seul titre. Un taux de retention dont
        // le numerateur et le denominateur ne portent pas sur le meme
        // ensemble que le chiffre affiche juste au-dessus n'est pas un
        // taux : c'est un rapport entre deux choses differentes.
        //
        // POURQUOI `total` ET NON `eleveurs` : la maquette tranche. Ses
        // trois tuiles sont UTILISATEURS 1 248, TAUX DE RETENTION 30J
        // 64 %, SIGNALEMENTS TRAITES. Elles sont cote a cote, donc elles
        // partagent leur denominateur ; et 1 248 est le nombre de comptes
        // de la plateforme, pas celui des eleveurs. La repartition par
        // role, juste en dessous, est l'explication de ce total.
        //
        // L'invariant devient verifiable, et le scenario I29 le verifie
        // desormais : `utilisateurs` est la somme de `parRole`. C'est un
        // test qui ne peut pas devenir obsolete, contrairement a un
        // intervalle 0-100 qui laissait passer n'importe quel chiffre.
        return new StatistiquesReponse(
                total,
                statistiqueRepository.compterInscritsDepuis(debutDuMois),
                pourcentage(statistiqueRepository.compterActifsDepuis(depuis), total),
                pourcentage(traites, aOrienter),
                aOrienter,
                statistiqueRepository.delaiMoyenPriseEnChargeHeures(depuis),
                lignes(statistiqueRepository.compterEleveursParZone()),
                List.of(new LigneStatistiqueReponse("Eleveurs", eleveurs),
                        new LigneStatistiqueReponse("Veterinaires", veterinaires),
                        new LigneStatistiqueReponse("Administrateurs", administrateurs)),
                lignes(statistiqueRepository.compterSignalementsParMaladie(depuis))
                        .stream().limit(TOP_MALADIES).toList());
    }

    /** Les series des graphiques de l'ecran « Statistiques ». */
    public SeriesStatistiquesReponse series() {
        Instant maintenant = Instant.now();
        return new SeriesStatistiquesReponse(
                semaines(),
                inscriptions(),
                entonnoir(maintenant.minus(FENETRE)),
                delais(maintenant.minus(FENETRE_LONGUE)),
                maladies(maintenant.minus(FENETRE_LONGUE)),
                moteur(maintenant.minus(FENETRE_LONGUE)));
    }

    private List<Semaine> semaines() {
        LocalDate lundi = LocalDate.now(FUSEAU).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate premier = lundi.minusWeeks(PERIODES - 1L);
        long[][] comptes = new long[PERIODES][3];
        Instant depuis = premier.atStartOfDay(FUSEAU).toInstant();
        for (Object[] l : statistiqueRepository.signalementsDepuis(depuis)) {
            LocalDate jour = ((Instant) l[0]).atZone(FUSEAU).toLocalDate();
            int i = (int) java.time.temporal.ChronoUnit.WEEKS.between(premier, jour);
            if (i >= 0 && i < PERIODES && l[1] != null) {
                comptes[i][((NiveauGravite) l[1]).ordinal()]++;
            }
        }
        List<Semaine> semaines = new ArrayList<>();
        for (int i = 0; i < PERIODES; i++) {
            semaines.add(new Semaine(premier.plusWeeks(i), comptes[i][0], comptes[i][1], comptes[i][2]));
        }
        return semaines;
    }

    private List<Mois> inscriptions() {
        YearMonth courant = YearMonth.now(FUSEAU);
        YearMonth premier = courant.minusMonths(PERIODES - 1L);
        Map<YearMonth, long[]> parMois = new LinkedHashMap<>();
        for (int i = 0; i < PERIODES; i++) {
            parMois.put(premier.plusMonths(i), new long[2]);
        }
        LocalDate depuis = premier.atDay(1);
        statistiqueRepository.inscriptionsEleveursDepuis(depuis).forEach(d -> {
            long[] c = parMois.get(YearMonth.from(d));
            if (c != null) c[0]++;
        });
        statistiqueRepository.inscriptionsVeterinairesDepuis(depuis).forEach(d -> {
            long[] c = parMois.get(YearMonth.from(d));
            if (c != null) c[1]++;
        });
        return parMois.entrySet().stream()
                .map(e -> new Mois(e.getKey().toString(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    private Entonnoir entonnoir(Instant depuis) {
        Map<StatutSignalement, Long> parStatut = new java.util.EnumMap<>(StatutSignalement.class);
        for (Object[] l : statistiqueRepository.compterParStatutDepuis(depuis)) {
            parStatut.put((StatutSignalement) l[0], ((Number) l[1]).longValue());
        }
        long oriente = parStatut.getOrDefault(StatutSignalement.ORIENTE, 0L);
        long enCours = parStatut.getOrDefault(StatutSignalement.EN_COURS, 0L);
        long traite = parStatut.getOrDefault(StatutSignalement.TRAITE, 0L);
        return new Entonnoir(
                parStatut.values().stream().mapToLong(Long::longValue).sum(),
                statistiqueRepository.compterNecessitantUnVeterinaireDepuis(depuis),
                oriente + enCours + traite,
                enCours + traite,
                traite);
    }

    private List<Tranche> delais(Instant depuis) {
        long[] comptes = new long[TRANCHES.length];
        for (Object[] l : statistiqueRepository.prisesEnChargeDepuis(depuis)) {
            long minutes = Duration.between((Instant) l[0], (Instant) l[1]).toMinutes();
            int i = 0;
            while (i < BORNES_MINUTES.length && minutes >= BORNES_MINUTES[i]) {
                i++;
            }
            comptes[i]++;
        }
        List<Tranche> tranches = new ArrayList<>();
        for (int i = 0; i < TRANCHES.length; i++) {
            tranches.add(new Tranche(TRANCHES[i], comptes[i]));
        }
        return tranches;
    }

    private List<MaladieSignalee> maladies(Instant depuis) {
        Map<String, MaladieSignalee> parNom = new LinkedHashMap<>();
        for (Object[] l : statistiqueRepository.diagnosticsDepuis(depuis)) {
            boolean confirme = l[1] != null;
            String nom = (String) l[2];
            MaladieSignalee m = parNom.getOrDefault(nom, new MaladieSignalee(nom, (NiveauGravite) l[3], 0, 0));
            parNom.put(nom, new MaladieSignalee(nom, m.gravite(),
                    m.confirmes() + (confirme ? 1 : 0), m.proposes() + (confirme ? 0 : 1)));
        }
        return parNom.values().stream()
                .sorted(java.util.Comparator
                        .comparingLong((MaladieSignalee m) -> -(m.confirmes() + m.proposes()))
                        .thenComparing(MaladieSignalee::maladie))
                .limit(TOP_MALADIES_SERIES)
                .toList();
    }

    private Moteur moteur(Instant depuis) {
        long verifies = 0;
        long concordants = 0;
        for (Object[] l : statistiqueRepository.diagnosticsDepuis(depuis)) {
            if (l[1] != null) {
                verifies++;
                if (Objects.equals(l[0], l[1])) {
                    concordants++;
                }
            }
        }
        return new Moteur(verifies, concordants);
    }

    // ── outillage ───────────────────────────────────────────────────────────

    private List<SignalementResumeReponse> derniersSignalements() {
        return signalementRepository
                .derniers(PageRequest.of(0, DERNIERS))
                .stream().map(StatistiqueService::versResume).toList();
    }

    private static SignalementResumeReponse versResume(SignalementMaladie s) {
        return new SignalementResumeReponse(
                s.getId(),
                s.getEleveur().getNom(),
                s.getEleveur().getZone() == null ? null : s.getEleveur().getZone().getNom(),
                s.getMaladieProbable() == null ? null : s.getMaladieProbable().getNom(),
                s.getGravite(),
                s.getStatut(),
                s.getDateSignalement());
    }

    /**
     * Convertit une liste de couples {@code [libelle, nombre]} rendue par une
     * requete d'agregation.
     *
     * <p>Le premier element peut etre une chaine ou une constante
     * d'enumeration selon la requete — nom de zone, nom de maladie, ou
     * {@code Filiere}. {@code String.valueOf} couvre les deux ; pour une
     * filiere on prefere son libelle lisible, « Volaille » plutot que
     * « VOLAILLE ».
     */
    private static List<LigneStatistiqueReponse> lignes(List<Object[]> brut) {
        return brut.stream()
                .map(l -> new LigneStatistiqueReponse(
                        l[0] instanceof Filiere f ? f.getLibelle() : String.valueOf(l[0]),
                        ((Number) l[1]).longValue()))
                .toList();
    }

    /**
     * Un pourcentage entier, arrondi au plus proche.
     *
     * <p>Rend 0 quand le denominateur est nul, et c'est le seul comportement
     * defendable : une division par zero ferait planter l'ecran, et rendre
     * {@code null} obligerait chaque client a s'en souvenir. Sur une base
     * neuve, « 0 % » se lit correctement — il n'y a rien a traiter.
     */
    private static int pourcentage(long part, long total) {
        return total == 0 ? 0 : Math.round(part * 100f / total);
    }
}
