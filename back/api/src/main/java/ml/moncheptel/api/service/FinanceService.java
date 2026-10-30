package ml.moncheptel.api.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import ml.moncheptel.api.dto.reponse.LigneRepartitionReponse;
import ml.moncheptel.api.dto.reponse.OperationReponse;
import ml.moncheptel.api.dto.reponse.PointMensuelReponse;
import ml.moncheptel.api.dto.reponse.RapportReponse;
import ml.moncheptel.api.dto.reponse.SoldeReponse;
import ml.moncheptel.api.dto.requete.OperationRequete;
import ml.moncheptel.api.exception.AccesRefuseException;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.mapper.OperationMapper;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.OperationFinanciere;
import ml.moncheptel.api.modele.UniteElevage;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.repository.OperationFinanciereRepository;
import ml.moncheptel.api.repository.UniteElevageRepository;
import ml.moncheptel.api.repository.projection.EvolutionProjection;
import ml.moncheptel.api.repository.projection.RepartitionProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Depenses, revenus, soldes et rapports.
 *
 * <h2>Toutes les sommes sont calculees en base</h2>
 *
 * <p>Aucune methode ici ne charge une liste d'operations pour l'additionner en
 * Java. Cela marcherait aujourd'hui, avec quelques dizaines de lignes ; avec
 * trois ans d'historique, l'ecran des rapports ramenerait des milliers
 * d'enregistrements sur un reseau mobile pour afficher trois nombres.
 */
@Service
@Transactional(readOnly = true)
public class FinanceService {

    private final OperationFinanciereRepository operationRepository;
    private final UniteElevageRepository uniteRepository;
    private final OperationMapper mapper;

    public FinanceService(OperationFinanciereRepository operationRepository,
                          UniteElevageRepository uniteRepository,
                          OperationMapper mapper) {
        this.operationRepository = operationRepository;
        this.uniteRepository = uniteRepository;
        this.mapper = mapper;
    }

    // ── saisie ──────────────────────────────────────────────────────────────

    /**
     * Ecran « Ajouter une operation ».
     *
     * <p>Le constructeur de {@code OperationFinanciere} refuse une categorie
     * qui n'appartient pas au type — « Vente » classee en depense. Cette
     * verification est sur l'entite et pas ici, pour la meme raison que
     * partout : un second appelant ne doit pas pouvoir la contourner.
     */
    @Transactional
    public OperationReponse enregistrer(Eleveur eleveur, OperationRequete requete) {
        UniteElevage unite = null;
        if (requete.uniteId() != null) {
            unite = uniteRepository.findById(requete.uniteId())
                    .orElseThrow(() -> RessourceIntrouvableException.de("Unité", requete.uniteId()));
            if (!unite.getProprietaire().getId().equals(eleveur.getId())) {
                throw new AccesRefuseException();
            }
            if (unite.getFiliere() != requete.filiere()) {
                throw new RegleMetierException(
                        "L'unité " + unite.getCode() + " est en filière "
                        + unite.getFiliere().getLibelle()
                        + ", pas " + requete.filiere().getLibelle());
            }
        }
        if (requete.dateOperation().isAfter(LocalDate.now())) {
            throw new RegleMetierException("La date d'une opération ne peut pas être future");
        }

        try {
            OperationFinanciere operation = new OperationFinanciere(
                    eleveur, requete.type(), requete.categorie(), requete.filiere(),
                    unite, requete.montant(), requete.dateOperation(), requete.libelle());
            return mapper.versReponse(operationRepository.save(operation));
        } catch (IllegalArgumentException e) {
            throw new RegleMetierException(e.getMessage());
        }
    }

    /**
     * Corrige une ecriture : ecran « Modifier l'operation ».
     *
     * <p>Les memes controles qu'a l'enregistrement, parce que ce sont les
     * memes regles : l'unite doit vous appartenir, sa filiere doit
     * correspondre, la date ne peut pas etre future, et la categorie doit
     * aller avec le type — « Vente » ne se classe pas en depense.
     *
     * <h3>Une recette nee d'une vente reste intouchable</h3>
     *
     * <p>Comme pour la suppression. Modifier le montant d'une recette
     * engendree par une sortie de cheptel la ferait diverger du mouvement qui
     * l'a produite : le journal dirait 150 tetes a 1 000 FCFA, et la
     * comptabilite 120 000. C'est la vente qu'il faut corriger.
     *
     * <h3>Le couple type/categorie est pose d'un seul geste</h3>
     *
     * <p>Par {@code OperationFinanciere.requalifier}, qui refuse « Vente
     * d'animaux » sur une depense. L'entite portait un {@code setCategorie}
     * seul, qui permettait de casser cet invariant en silence ; il a ete
     * remplace en meme temps que cette methode a ete ecrite, parce que c'est
     * elle qui allait l'appeler.
     */
    @Transactional
    public OperationReponse modifier(Eleveur eleveur, Long operationId,
                                     OperationRequete requete) {
        OperationFinanciere operation = operationRepository.findById(operationId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Opération", operationId));
        if (!operation.getEleveur().getId().equals(eleveur.getId())) {
            throw new AccesRefuseException();
        }
        if (operation.getMouvement() != null) {
            throw new RegleMetierException(
                    "Cette recette provient d'une vente enregistrée. "
                    + "Corrigez la sortie du cheptel plutôt que l'écriture.");
        }
        if (requete.dateOperation().isAfter(LocalDate.now())) {
            throw new RegleMetierException("La date d'une opération ne peut pas être future");
        }

        UniteElevage unite = null;
        if (requete.uniteId() != null) {
            unite = uniteRepository.findById(requete.uniteId())
                    .orElseThrow(() -> RessourceIntrouvableException.de("Unité", requete.uniteId()));
            if (!unite.getProprietaire().getId().equals(eleveur.getId())) {
                throw new AccesRefuseException();
            }
            if (unite.getFiliere() != requete.filiere()) {
                throw new RegleMetierException(
                        "L'unité " + unite.getCode() + " est en filière "
                        + unite.getFiliere().getLibelle()
                        + ", pas " + requete.filiere().getLibelle());
            }
        }

        try {
            // requalifier verifie le couple type/categorie, comme le
            // constructeur, et pose les deux d'un bloc : impossible de laisser
            // une categorie « Vente » sur une depense.
            operation.requalifier(requete.type(), requete.categorie());
        } catch (IllegalArgumentException e) {
            throw new RegleMetierException(e.getMessage());
        }

        operation.setFiliere(requete.filiere());
        operation.setUnite(unite);
        operation.setMontant(requete.montant());
        operation.setDateOperation(requete.dateOperation());
        operation.setNote(requete.libelle());
        return mapper.versReponse(operation);
    }

    /**
     * Suppression d'une operation.
     *
     * <p>Une recette nee d'une vente n'est PAS supprimable ici : elle
     * decoulerait alors d'un mouvement qui, lui, existe toujours, et le solde
     * cesserait de correspondre au cheptel. C'est la vente qu'il faut
     * corriger, pas sa trace comptable.
     */
    @Transactional
    public void supprimer(Eleveur eleveur, Long operationId) {
        OperationFinanciere operation = operationRepository.findById(operationId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Opération", operationId));
        if (!operation.getEleveur().getId().equals(eleveur.getId())) {
            throw new AccesRefuseException();
        }
        if (operation.getMouvement() != null) {
            throw new RegleMetierException(
                    "Cette recette provient d'une vente enregistrée. "
                    + "Corrigez la sortie du cheptel plutôt que l'écriture.");
        }
        operationRepository.delete(operation);
    }

    // ── consultation ────────────────────────────────────────────────────────

    /**
     * Refuse un intervalle inverse.
     *
     * <h3>Pourquoi c'est une erreur et non un resultat vide</h3>
     *
     * <p>Une requete {@code debut=30/09, fin=01/09} ne decrit aucune periode.
     * La laisser passer rendait {@code []} — indistinguable, pour l'eleveur,
     * de « vous n'avez eu aucune operation ce mois-ci ». Il en concluait que
     * ses ecritures avaient disparu.
     *
     * <p>Le controle existait dans {@code rapport} depuis le debut, et nulle
     * part ailleurs. {@code journal} et {@code solde} alimentent pourtant les
     * deux ecrans les plus consultes. Mesure du 4 octobre : la route rendait
     * 200 et une liste vide.
     */
    private static void exigerIntervalleValide(LocalDate debut, LocalDate fin) {
        if (debut != null && fin != null && fin.isBefore(debut)) {
            throw new RegleMetierException("La date de fin précède la date de début");
        }
    }

    public List<OperationReponse> journal(Eleveur eleveur, Filiere filiere,
                                          LocalDate debut, LocalDate fin) {
        exigerIntervalleValide(debut, fin);
        return mapper.versReponses(
                operationRepository.chercherJournal(eleveur.getId(), filiere, debut, fin));
    }

    /** « Solde du mois — toutes filieres : + 74 000 FCFA ». */
    public SoldeReponse solde(Eleveur eleveur, Filiere filiere, LocalDate debut, LocalDate fin) {
        exigerIntervalleValide(debut, fin);
        return mapper.versSolde(
                operationRepository.calculerSolde(eleveur.getId(), filiere, debut, fin));
    }

    public SoldeReponse soldeDuMois(Eleveur eleveur, Filiere filiere, LocalDate reference) {
        LocalDate debut = reference.withDayOfMonth(1);
        return solde(eleveur, filiere, debut, debut.plusMonths(1).minusDays(1));
    }

    /**
     * Ecran « Rapports ».
     *
     * <p>Les bornes reelles sont renvoyees avec les chiffres. La maquette a
     * montre pourquoi : un ecran intitule « periode d'un an » affichait en
     * fait trois mois, et rien a l'ecran ne permettait de s'en apercevoir.
     */
    public RapportReponse rapport(Eleveur eleveur, Filiere filiere,
                                  LocalDate debut, LocalDate fin) {
        exigerIntervalleValide(debut, fin);
        SoldeReponse total = solde(eleveur, filiere, debut, fin);

        List<RepartitionProjection> brutes =
                operationRepository.repartirDepenses(eleveur.getId(), filiere, debut, fin);

        return new RapportReponse(debut, fin, total,
                repartir(brutes, total.depenses()),
                evolution(eleveur, filiere, debut, fin));
    }

    public SoldeReponse rentabilite(Eleveur eleveur, Long uniteId) {
        UniteElevage unite = uniteRepository.findById(uniteId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Unité", uniteId));
        if (!unite.getProprietaire().getId().equals(eleveur.getId())) {
            throw new AccesRefuseException();
        }
        return mapper.versSolde(operationRepository.calculerRentabiliteUnite(uniteId));
    }

    // ── mise en forme ───────────────────────────────────────────────────────

    /**
     * Ajoute le pourcentage a chaque poste de depense.
     *
     * <p>Le total vient de la requete de solde, pas de la somme des lignes :
     * les deux doivent coincider, et si un jour elles divergent — un filtre
     * oublie d'un cote — le pourcentage le montrera au lieu de le masquer par
     * une renormalisation.
     */
    private static List<LigneRepartitionReponse> repartir(List<RepartitionProjection> brutes,
                                                          long totalDepenses) {
        List<LigneRepartitionReponse> lignes = new ArrayList<>(brutes.size());
        for (RepartitionProjection b : brutes) {
            long montant = b.getTotal() == null ? 0L : b.getTotal();
            BigDecimal part = totalDepenses == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(montant)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(totalDepenses), 1, RoundingMode.HALF_UP);
            lignes.add(new LigneRepartitionReponse(b.getCategorie(), montant, part));
        }
        return lignes;
    }

    /**
     * Courbe mensuelle, avec les mois vides.
     *
     * <p>La requete ne rend que les mois ou il y a eu au moins une ecriture.
     * Les afficher tels quels tasserait la courbe : trois points cote a cote
     * pour janvier, mars et septembre donneraient a lire une progression
     * reguliere la ou il y a eu six mois sans rien. Les mois manquants sont
     * donc completes a zero.
     */
    private List<PointMensuelReponse> evolution(Eleveur eleveur, Filiere filiere,
                                                LocalDate debut, LocalDate fin) {
        List<EvolutionProjection> brutes =
                operationRepository.evolutionMensuelle(eleveur.getId(), filiere, debut, fin);

        List<PointMensuelReponse> points = new ArrayList<>();
        LocalDate curseur = debut.withDayOfMonth(1);
        LocalDate dernier = fin.withDayOfMonth(1);

        while (!curseur.isAfter(dernier)) {
            final LocalDate mois = curseur;
            EvolutionProjection trouve = brutes.stream()
                    .filter(b -> b.getAnnee() == mois.getYear()
                              && b.getMois() == mois.getMonthValue())
                    .findFirst().orElse(null);

            long revenus = trouve == null || trouve.getRevenus() == null ? 0L : trouve.getRevenus();
            long depenses = trouve == null || trouve.getDepenses() == null ? 0L : trouve.getDepenses();
            points.add(new PointMensuelReponse(
                    "%d-%02d".formatted(mois.getYear(), mois.getMonthValue()),
                    revenus, depenses, revenus - depenses));
            curseur = curseur.plusMonths(1);
        }
        return points;
    }
}
