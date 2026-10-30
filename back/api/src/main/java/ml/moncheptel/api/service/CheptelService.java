package ml.moncheptel.api.service;

import java.time.LocalDate;
import java.util.List;
import ml.moncheptel.api.dto.reponse.LotDetailReponse;
import ml.moncheptel.api.dto.reponse.UniteResumeReponse;
import ml.moncheptel.api.dto.requete.AnimalRequete;
import ml.moncheptel.api.dto.requete.ClotureRequete;
import ml.moncheptel.api.dto.requete.LotRequete;
import ml.moncheptel.api.dto.requete.SortieRequete;
import ml.moncheptel.api.dto.requete.UniteRequete;
import ml.moncheptel.api.exception.AccesRefuseException;
import ml.moncheptel.api.exception.ConflitException;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.mapper.OperationMapper;
import ml.moncheptel.api.mapper.UniteMapper;
import ml.moncheptel.api.modele.Animal;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.Lot;
import ml.moncheptel.api.modele.MouvementCheptel;
import ml.moncheptel.api.modele.OperationFinanciere;
import ml.moncheptel.api.modele.TypeElevage;
import ml.moncheptel.api.modele.UniteElevage;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import ml.moncheptel.api.modele.enumeration.TypeMouvement;
import ml.moncheptel.api.repository.AnimalRepository;
import ml.moncheptel.api.repository.EvenementSanitaireRepository;
import ml.moncheptel.api.repository.LotRepository;
import ml.moncheptel.api.repository.MouvementCheptelRepository;
import ml.moncheptel.api.repository.OperationFinanciereRepository;
import ml.moncheptel.api.repository.RappelSanitaireRepository;
import ml.moncheptel.api.repository.TypeElevageRepository;
import ml.moncheptel.api.repository.UniteElevageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion du cheptel : creation des unites, sorties, consultation.
 *
 * <h2>Ce que ce service NE fait pas</h2>
 *
 * <p>Il ne decide pas si une sortie est permise. Cette regle est dans
 * {@link UniteElevage#retirer} — sur l'entite, pas ici. Un service qui
 * verifierait lui-meme l'effectif laisserait la porte ouverte a un second
 * appelant qui oublierait de le faire ; sur l'entite, la regle est
 * incontournable.
 *
 * <p>Le service fait trois choses que l'entite ne peut pas faire : ouvrir la
 * transaction, poser le verrou, et enregistrer les consequences (ecriture au
 * journal, operation financiere).
 */
@Service
@Transactional(readOnly = true)
public class CheptelService {

    private final UniteElevageRepository uniteRepository;
    private final LotRepository lotRepository;
    private final AnimalRepository animalRepository;
    private final TypeElevageRepository typeElevageRepository;
    private final MouvementCheptelRepository mouvementRepository;
    private final OperationFinanciereRepository operationRepository;
    private final EvenementSanitaireRepository evenementRepository;
    private final RappelSanitaireRepository rappelRepository;
    private final SanitaireService sanitaireService;
    private final UniteMapper uniteMapper;
    private final OperationMapper operationMapper;

    public CheptelService(UniteElevageRepository uniteRepository,
                          LotRepository lotRepository,
                          AnimalRepository animalRepository,
                          TypeElevageRepository typeElevageRepository,
                          MouvementCheptelRepository mouvementRepository,
                          OperationFinanciereRepository operationRepository,
                          EvenementSanitaireRepository evenementRepository,
                          RappelSanitaireRepository rappelRepository,
                          SanitaireService sanitaireService,
                          UniteMapper uniteMapper,
                          OperationMapper operationMapper) {
        this.uniteRepository = uniteRepository;
        this.lotRepository = lotRepository;
        this.animalRepository = animalRepository;
        this.typeElevageRepository = typeElevageRepository;
        this.mouvementRepository = mouvementRepository;
        this.operationRepository = operationRepository;
        this.evenementRepository = evenementRepository;
        this.rappelRepository = rappelRepository;
        this.sanitaireService = sanitaireService;
        this.uniteMapper = uniteMapper;
        this.operationMapper = operationMapper;
    }

    // ── consultation ────────────────────────────────────────────────────────

    public List<UniteResumeReponse> lister(Eleveur eleveur, Filiere filiere, StatutUnite statut) {
        return uniteMapper.versResumes(
                uniteRepository.chercherParProprietaireEtFiliere(eleveur.getId(), filiere, statut));
    }

    public LotDetailReponse detailler(Eleveur eleveur, Long uniteId) {
        UniteElevage unite = charger(uniteId, eleveur);
        List<MouvementCheptel> mouvements =
                mouvementRepository.findByUniteIdOrderByDateMouvementAscIdAsc(uniteId);
        return new LotDetailReponse(
                uniteMapper.versResume(unite),
                mouvements.stream().map(uniteMapper::versReponse).toList(),
                evenementRepository.findByUniteIdOrderByDateEvenementDescIdDesc(uniteId)
                        .stream().map(uniteMapper::versReponse).toList(),
                uniteMapper.versRappels(rappelRepository.findByUniteIdOrderByDateEcheanceAsc(uniteId)),
                operationMapper.versSolde(operationRepository.calculerRentabiliteUnite(uniteId)),
                // Les sorties seules, de la plus recente a la plus ancienne :
                // c'est la liste ou l'eleveur choisit celle a annuler (F1).
                mouvements.stream().filter(MouvementCheptel::estSortie)
                        .sorted(java.util.Comparator.comparing(MouvementCheptel::getDateMouvement)
                                .thenComparing(MouvementCheptel::getId).reversed())
                        .map(uniteMapper::versSortie).toList());
    }

    // ── creation ────────────────────────────────────────────────────────────

    @Transactional
    public UniteResumeReponse creerLot(Eleveur eleveur, LotRequete requete) {
        TypeElevage type = typeElevage(requete.typeElevageId());
        exigerTypePropose(type);
        exigerFilierePratiquee(eleveur, type.getFiliere());
        ControleDates.exigerPasFuture(requete.dateMiseEnPlace(), "de mise en place");
        if (!type.getFiliere().suiviParLot()) {
            throw new RegleMetierException(
                    "La filière " + type.getFiliere().getLibelle()
                    + " se suit animal par animal, pas par lot");
        }

        String code = codeOuGenere(requete.code(), "LOT", eleveur);
        Lot lot = new Lot(code, eleveur, type, requete.effectifInitial(), requete.dateMiseEnPlace());
        lotRepository.save(lot);

        // Le journal enregistre l'entree. Sans cette ligne, l'effectif stocke
        // et le journal divergeraient des la creation, et
        // LotRepository.chercherIncoherences remonterait le lot a tort.
        mouvementRepository.save(MouvementCheptel.entree(
                lot, TypeMouvement.MISE_EN_PLACE, requete.effectifInitial(), eleveur));

        sanitaireService.programmerRappels(lot);
        return uniteMapper.versResume(lot);
    }

    @Transactional
    public UniteResumeReponse creerAnimal(Eleveur eleveur, AnimalRequete requete) {
        TypeElevage type = typeElevage(requete.typeElevageId());
        exigerTypePropose(type);
        exigerFilierePratiquee(eleveur, type.getFiliere());
        ControleDates.exigerPasFuture(requete.dateEntree(), "d'entrée");
        exigerNaissanceAvantEntree(requete.dateNaissance(), requete.dateEntree());
        if (type.getFiliere().suiviParLot()) {
            throw new RegleMetierException(
                    "La filière " + type.getFiliere().getLibelle()
                    + " se suit par lots, pas animal par animal");
        }

        String code = codeOuGenere(requete.code(), prefixe(type.getFiliere()), eleveur);
        Animal animal = new Animal(code, eleveur, type, requete.dateEntree(),
                requete.race(), requete.sexe(), requete.dateNaissance());
        animalRepository.save(animal);

        mouvementRepository.save(MouvementCheptel.entree(animal, TypeMouvement.ACQUISITION, 1, eleveur));
        sanitaireService.programmerRappels(animal);
        return uniteMapper.versResume(animal);
    }

    // ── sortie du cheptel ───────────────────────────────────────────────────

    /**
     * Vente, mortalite ou transfert.
     *
     * <h3>Pourquoi un verrou pessimiste</h3>
     *
     * <p>Deux appels simultanes sur le meme lot liraient tous deux
     * « 180 tetes », verifieraient tous deux que 100 tient dans 180, et
     * ecriraient tous deux. Le lot finirait a 80 apres avoir perdu 200 tetes.
     * {@code chercherPourMiseAJour} pose un {@code select ... for update} :
     * le second appel attend que le premier ait commis.
     *
     * <p>Un verrou optimiste (@Version) aurait suffi techniquement, mais il
     * fait echouer le second appel par une exception que l'eleveur verrait
     * comme un plantage. Ici la deuxieme vente reussit, simplement apres la
     * premiere — et refuse si l'effectif ne suffit plus, avec un message
     * comprehensible.
     *
     * <h3>Pourquoi l'operation financiere est creee ici</h3>
     *
     * <p>L'ecran de vente demande un montant. Si l'eleveur devait ensuite
     * saisir la recette dans « Depenses et revenus », il l'oublierait une fois
     * sur deux, et le solde du mois ne correspondrait plus au cheptel. Une
     * seule saisie, deux ecritures.
     */
    @Transactional
    public UniteResumeReponse sortir(Eleveur eleveur, Long uniteId, SortieRequete requete) {
        UniteElevage unite = uniteRepository.chercherPourMiseAJour(uniteId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Unité", uniteId));
        exigerProprietaire(unite, eleveur);
        enregistrerSortie(eleveur, unite, requete);
        // La derniere tete sortie termine l'unite : ses rappels de soins
        // n'ont plus d'objet (audit global G5, meme regle que « Terminer »).
        if (unite.getStatut().estSortie()) {
            sanitaireService.solderRappelsOuverts(unite);
        }
        return uniteMapper.versResume(unite);
    }

    /** Le coeur d'une sortie, partage avec {@link #terminer}. */
    private void enregistrerSortie(Eleveur eleveur, UniteElevage unite, SortieRequete requete) {
        if (!requete.type().estSortie()) {
            throw new RegleMetierException(
                    "« " + requete.type().getLibelle() + " » n'est pas une sortie : "
                    + "choisissez Vente, Mort ou Transfert.");
        }
        ControleDates.exigerDansLaVieDe(unite, requete.date(), "de la sortie");

        if (requete.type() == TypeMouvement.VENTE
                && (requete.montant() == null || requete.montant() <= 0)) {
            throw new RegleMetierException(
                    "Indiquez le montant de la vente, supérieur à zéro.");
        }
        if (requete.type() != TypeMouvement.VENTE && requete.montant() != null) {
            throw new RegleMetierException(
                    "Seule une vente porte un montant ; " + requete.type().getLibelle()
                    + " n'en a pas");
        }

        try {
            unite.retirer(requete.nombreTetes(), requete.type(), requete.date());
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Les regles du modele levent des messages deja rediges pour
            // l'utilisateur ; on ne fait que les reclasser en 422.
            throw new RegleMetierException(e.getMessage());
        }

        MouvementCheptel mouvement = mouvementRepository.save(new MouvementCheptel(
                unite, requete.type(), requete.date(), requete.nombreTetes(),
                requete.motif(), requete.montant(), eleveur));

        if (requete.type() == TypeMouvement.VENTE) {
            OperationFinanciere recette = OperationFinanciere.revenuDeVente(
                    mouvement, "Vente " + unite.getCode()
                               + " — " + UniteElevage.tetes(requete.nombreTetes()));
            operationRepository.save(recette);
        }
    }

    /**
     * Annule une sortie saisie par erreur (audit F1) :
     * {@code DELETE /api/cheptel/{uniteId}/sorties/{sortieId}}.
     *
     * <p>{@code sortieId} est l'identifiant du mouvement de sortie, celui que
     * le detail de l'unite expose dans {@code mouvements[].id} et
     * {@code sorties[].id}. Les tetes reviennent a l'effectif, l'unite que
     * cette sortie avait fait sortir redevient active, et le revenu engendre
     * par une vente est supprime avec elle : le solde du mois suit.
     *
     * <p>Le journal est ici reellement corrige, et non compense par un
     * mouvement inverse : une sortie saisie par erreur n'a jamais eu lieu, et
     * un mouvement « entree » de correction fausserait les statistiques de
     * ventes et de mortalite.
     */
    @Transactional
    public void annulerSortie(Eleveur eleveur, Long uniteId, Long sortieId) {
        UniteElevage unite = uniteRepository.chercherPourMiseAJour(uniteId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Unité", uniteId));
        exigerProprietaire(unite, eleveur);
        MouvementCheptel sortie = mouvementRepository.findById(sortieId)
                .filter(m -> m.getUnite().getId().equals(uniteId))
                .orElseThrow(() -> RessourceIntrouvableException.de("Sortie", sortieId));
        if (!sortie.estSortie()) {
            throw new RegleMetierException(
                    "Ce mouvement est une entrée (" + sortie.getType().getLibelle().toLowerCase()
                    + ") : seule une sortie s'annule.");
        }
        boolean etaitSortie = unite.getStatut().estSortie();
        try {
            unite.restituer(sortie.getNombreTetes());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new RegleMetierException(e.getMessage());
        }
        // L'unite redevient active : ses rappels de soins ecartes a la sortie
        // reviennent, sinon plus aucun « Soins à faire » (audit du 29/10, point 7).
        if (etaitSortie && !unite.getStatut().estSortie()) {
            sanitaireService.rouvrirRappelsEcartes(unite);
        }
        operationRepository.findByMouvementId(sortieId).ifPresent(op -> {
            operationRepository.delete(op);
            // La cle etrangere operation -> mouvement : le revenu doit partir
            // AVANT le mouvement.
            operationRepository.flush();
        });
        if (org.hibernate.Hibernate.isInitialized(unite.getMouvements())) {
            unite.getMouvements().removeIf(m -> sortieId.equals(m.getId()));
        }
        mouvementRepository.delete(sortie);
    }

    /**
     * Termine un elevage a la main (audit F2) :
     * {@code POST /api/cheptel/{uniteId}/cloture}.
     *
     * <p>Fin de cycle, animal reforme, lot abandonne : l'unite passe a
     * « Terminé », quitte les listes actives, et ses rappels encore ouverts
     * sont ecartes.
     *
     * <h3>Les tetes restantes (audit global G5)</h3>
     *
     * <p>Terminer un lot de 40 tetes sans mouvement les faisait disparaitre
     * des comptes : ni restantes, ni vendues, ni mortes. La regle retenue est
     * la plus juste pour l'eleveur : on ne lui interdit pas de terminer, on
     * lui demande ce que sont devenues ces tetes.
     *
     * <ul>
     *   <li>sans corps, et des tetes restantes : 422 qui dit combien il en
     *       reste et quoi faire ;</li>
     *   <li>avec {@link ClotureRequete} : une sortie de TOUTES les tetes
     *       restantes est enregistree (vente avec son revenu, mort ou
     *       transfert), ce qui termine l'unite — CLOTURE pour un lot, VENDU,
     *       MORT ou TRANSFERE pour un animal ;</li>
     *   <li>plus aucune tete : l'unite est deja sortie d'elle-meme, 422
     *       « deja termine ».</li>
     * </ul>
     */
    @Transactional
    public UniteResumeReponse terminer(Eleveur eleveur, Long uniteId, ClotureRequete requete) {
        UniteElevage unite = uniteRepository.chercherPourMiseAJour(uniteId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Unité", uniteId));
        exigerProprietaire(unite, eleveur);
        int restantes = unite.getEffectifRestant();
        if (unite.getStatut().estSortie() || restantes == 0) {
            try {
                unite.terminer(LocalDate.now());
            } catch (IllegalStateException e) {
                throw new RegleMetierException(e.getMessage());
            }
        } else if (requete == null) {
            throw new RegleMetierException(messageTetesRestantes(unite, restantes));
        } else {
            // Toutes les tetes restantes sortent, par le meme chemin qu'une
            // sortie saisie a la main : memes regles, meme revenu de vente.
            enregistrerSortie(eleveur, unite, new SortieRequete(
                    requete.type(), restantes, LocalDate.now(),
                    requete.montant(), requete.motif()));
            if (!unite.getStatut().estSortie()) {
                unite.terminer(LocalDate.now());
            }
        }
        sanitaireService.solderRappelsOuverts(unite);
        return uniteMapper.versResume(unite);
    }

    /** « LOT-014 compte encore 40 têtes... » : le 422 de {@link #terminer}. */
    static String messageTetesRestantes(UniteElevage unite, int restantes) {
        return unite.getCode() + " compte encore " + UniteElevage.tetes(restantes)
                + ". Pour le clôturer, dites ce qu'elles sont devenues : "
                + "vente, mort ou transfert.";
    }

    // ── outillage ───────────────────────────────────────────────────────────

    private static void exigerNaissanceAvantEntree(LocalDate naissance, LocalDate entree) {
        if (naissance == null) {
            return;
        }
        ControleDates.exigerPasFuture(naissance, "de naissance");
        if (entree != null && naissance.isAfter(entree)) {
            throw new RegleMetierException(
                    "La date de naissance (" + ControleDates.jour(naissance)
                    + ") ne peut pas suivre la date d'entrée (" + ControleDates.jour(entree) + ").");
        }
    }

    /**
     * Charge une unite en verifiant qu'elle appartient bien a l'appelant.
     *
     * <p>Toute lecture par identifiant passe par ici. Sans ce controle, un
     * eleveur qui incrementerait l'identifiant dans l'URL lirait le cheptel de
     * son voisin — la faille la plus banale et la plus repandue des API REST.
     */
    // ── correction et suppression ───────────────────────────────────────────

    /**
     * Corrige un lot ou un animal : ecran « Modifier le lot ».
     *
     * <p>Ce qui est modifiable et ce qui ne l'est pas est explique dans
     * {@link UniteRequete} — l'effectif, en particulier, n'en est pas.
     *
     * <h3>Changer la filiere en changeant le type d'elevage</h3>
     *
     * <p>Interdit. Un lot de volaille qui deviendrait bovin emporterait avec
     * lui son journal, ses evenements sanitaires et ses operations
     * financieres, tous classes dans l'ancienne filiere. Le solde par filiere
     * et les rapports deviendraient faux retroactivement, sans trace.
     *
     * <p>Le mode de suivi est verrouille pour la meme raison : une filiere
     * suivie par lot ne peut pas devenir une filiere suivie animal par animal,
     * les deux n'ont pas la meme table.
     */
    @Transactional
    public UniteResumeReponse modifier(Eleveur eleveur, Long uniteId, UniteRequete requete) {
        UniteElevage unite = charger(uniteId, eleveur);
        // Un lot cloture, un animal vendu ou mort ne se modifie plus. Le
        // controle manquait ici alors que SanitaireService et
        // SignalementService le font tous deux : on pouvait changer le type
        // d'elevage d'un lot termine et recalculer l'echeance d'un cycle qui
        // n'existe plus, ce qui faussait la rentabilite deja publiee.
        if (unite.getStatut().estSortie()) {
            throw new RegleMetierException(
                    "L'élevage " + unite.getCode() + " est sorti du cheptel : "
                    + "son dossier est clos et ne se modifie plus.");
        }
        TypeElevage type = typeElevage(requete.typeElevageId());
        // Garder le type actuel reste permis meme s'il a ete supprime depuis :
        // seul un CHANGEMENT vers un type retire est refuse.
        if (!java.util.Objects.equals(type.getId(), unite.getTypeElevage().getId())) {
            exigerTypePropose(type);
        }

        if (type.getFiliere() != unite.getFiliere()) {
            throw new RegleMetierException(
                    "Un élevage ne change pas de filière. " + unite.getCode()
                    + " est en " + unite.getFiliere().getLibelle()
                    + " ; son journal et sa comptabilité y sont rattachés. "
                    + "Clôturez-le (« Clôturer » dans son carnet) et créez-en un autre.");
        }
        exigerFilierePratiquee(eleveur, type.getFiliere());

        // On NORMALISE avant de comparer et avant de tester l'unicite.
        // Le controle portait sur requete.code() brut alors que la ligne
        // suivante stockait requete.code().strip() : envoyer « LOT-001 » avec
        // un espace devant quand LOT-001 existe passait le test, puis violait
        // uk_unite_proprietaire_code — 500 au lieu du 409 que ce bloc produit.
        // La casse est alignee sur codeOuGenere, qui met en majuscules.
        String codeVoulu = requete.code() == null ? null
                : requete.code().strip().toUpperCase(java.util.Locale.ROOT);
        if (codeVoulu != null && !codeVoulu.isBlank()
                && !codeVoulu.equalsIgnoreCase(unite.getCode())) {
            // L'unicite est « chez son proprietaire », pas sur la plateforme :
            // c'est la contrainte uk_unite_proprietaire_code. On la verifie ici
            // pour rendre 409 avec une phrase utile.
            if (uniteRepository.existsByProprietaireIdAndCode(eleveur.getId(), codeVoulu)) {
                throw new ConflitException(
                        "Vous avez déjà un élevage portant le code " + codeVoulu);
            }
            unite.setCode(codeVoulu);
        }

        // Releves AVANT l'ecriture : apres, il n'y a plus rien a comparer.
        boolean typeChange = !type.getId().equals(unite.getTypeElevage().getId());
        boolean dateChange = !java.util.Objects.equals(requete.dateEntree(), unite.getDateEntree());

        List<MouvementCheptel> journal = mouvementRepository.findByUniteIdOrderByDateMouvementAscIdAsc(uniteId);
        if (dateChange) {
            exigerEntreeAvantHistoire(unite, requete.dateEntree(), journal);
        }
        if (unite instanceof Animal) {
            exigerNaissanceAvantEntree(requete.dateNaissance(), requete.dateEntree());
        }

        unite.setTypeElevage(type);
        unite.setDateEntree(requete.dateEntree());
        unite.setNote(requete.note());
        if (dateChange) {
            // Le mouvement d'entree suit la date corrigee (F12) : le journal
            // ne doit pas garder l'ancienne mise en place.
            journal.stream().filter(m -> !m.estSortie()).findFirst()
                    .ifPresent(m -> m.setDateMouvement(requete.dateEntree()));
        }

        if (unite instanceof Lot lot) {
            // La durée de cycle a pu changer avec le type : la date de cloture
            // prevue en decoule, et la maquette l'annonce a l'ecran.
            lot.recalculerCloture();
        } else if (unite instanceof Animal animal) {
            animal.setRace(requete.race());
            animal.setSexe(requete.sexe());
            animal.setDateNaissance(requete.dateNaissance());
        }
        // Les rappels du protocole suivent la date et le type : voir
        // SanitaireService.reprogrammerRappels.
        if (typeChange || dateChange) {
            sanitaireService.reprogrammerRappels(unite, typeChange);
        }
        return uniteMapper.versResume(unite);
    }

    /**
     * Supprime un elevage — et refuse des qu'il a une histoire.
     *
     * <h3>Pourquoi ce n'est presque jamais possible</h3>
     *
     * <p>La maquette annonce « LOT-014 et son carnet sanitaire seront
     * definitivement supprimes ». <b>Le schema l'interdit</b>, et c'est
     * volontaire : {@code mouvement_cheptel} n'a pas de cascade depuis
     * l'unite, parce que « la trace d'une vente survit a tout le reste ». Une
     * suppression emporterait des recettes deja comptees dans un solde
     * mensuel, et un rapport imprime le mois dernier cesserait d'etre
     * reproductible.
     *
     * <p>Cette methode ne supprime donc que ce qui n'a RIEN : pas de sortie,
     * pas d'evenement sanitaire, pas d'operation financiere, et un seul
     * mouvement — sa propre mise en place. Autrement dit le cas reel de la
     * suppression : « je viens de le creer, je me suis trompe ».
     *
     * <p>Dans tous les autres cas, 422 avec la raison exacte et la marche a
     * suivre : cloturer. Un elevage cloture disparait des listes actives et
     * garde son histoire.
     *
     * <h3>Ce qui part avec</h3>
     *
     * <p>Les rappels sanitaires, par cascade de la base — ils ne sont que des
     * echeances calculees, aucune information ne s'y perd. Et le mouvement de
     * mise en place, supprime explicitement puisque rien ne le fait a notre
     * place.
     */
    @Transactional
    public void supprimer(Eleveur eleveur, Long uniteId) {
        UniteElevage unite = charger(uniteId, eleveur);

        if (!evenementRepository.findByUniteIdOrderByDateEvenementDescIdDesc(uniteId).isEmpty()) {
            throw new RegleMetierException(
                    "Cet élevage porte un carnet sanitaire. Clôturez-le (« Clôturer » dans son carnet) plutôt que "
                    + "de le supprimer : les soins déjà enregistrés ne doivent pas "
                    + "disparaître.");
        }
        if (operationRepository.countByUniteId(uniteId) > 0) {
            throw new RegleMetierException(
                    "Des opérations financières sont rattachées à cet élevage. "
                    + "Les supprimer fausserait un solde mensuel déjà consulté. "
                    + "Clôturez-le plutôt (« Clôturer » dans son carnet).");
        }
        // Ce controle manquait. On peut signaler une maladie sur un lot tout
        // juste cree, sans aucun mouvement ni evenement : les trois gardes
        // precedentes passaient alors, et la suppression violait
        // fk_signalement_unite — NOT NULL, sans cascade. Le client recevait
        // 500 au lieu du 422 motive que cette methode promet.
        if (uniteRepository.compterSignalements(uniteId) > 0) {
            throw new RegleMetierException(
                    "Un signalement de maladie vise cet élevage. Le supprimer "
                    + "effacerait une alerte sanitaire de l'historique de la "
                    + "plateforme. Clôturez-le plutôt (« Clôturer » dans son carnet).");
        }
        List<MouvementCheptel> mouvements =
                mouvementRepository.findByUniteIdOrderByDateMouvementAscIdAsc(uniteId);
        if (mouvements.size() > 1) {
            throw new RegleMetierException(
                    "Cet élevage a une histoire : " + mouvements.size()
                    + " mouvements enregistrés. Clôturez-le (« Clôturer » dans son carnet), ne le supprimez pas.");
        }

        rappelRepository.deleteAll(rappelRepository.findByUniteIdOrderByDateEcheanceAsc(uniteId));
        mouvementRepository.deleteAll(mouvements);
        uniteRepository.delete(unite);
    }

    /**
     * Une nouvelle date d'entree ne peut ni etre future, ni suivre la
     * premiere sortie ou le premier soin deja enregistres (F12).
     */
    private void exigerEntreeAvantHistoire(UniteElevage unite, LocalDate entree,
                                           List<MouvementCheptel> journal) {
        ControleDates.exigerPasFuture(entree, "d'entrée");
        journal.stream().filter(MouvementCheptel::estSortie)
                .map(MouvementCheptel::getDateMouvement).min(LocalDate::compareTo)
                .filter(entree::isAfter)
                .ifPresent(d -> {
                    throw new RegleMetierException(
                            "La date d'entrée ne peut pas suivre la première sortie, enregistrée le "
                            + ControleDates.jour(d) + ".");
                });
        evenementRepository.findByUniteIdOrderByDateEvenementDescIdDesc(unite.getId()).stream()
                .map(e -> e.getDateEvenement()).min(LocalDate::compareTo)
                .filter(entree::isAfter)
                .ifPresent(d -> {
                    throw new RegleMetierException(
                            "La date d'entrée ne peut pas suivre le premier soin, enregistré le "
                            + ControleDates.jour(d) + ".");
                });
    }

    private UniteElevage charger(Long uniteId, Eleveur eleveur) {
        UniteElevage unite = uniteRepository.findById(uniteId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Unité", uniteId));
        exigerProprietaire(unite, eleveur);
        return unite;
    }

    private static void exigerProprietaire(UniteElevage unite, Eleveur eleveur) {
        if (!unite.getProprietaire().getId().equals(eleveur.getId())) {
            throw new AccesRefuseException();
        }
    }

    private static void exigerFilierePratiquee(Eleveur eleveur, Filiere filiere) {
        if (!eleveur.pratique(filiere)) {
            throw new RegleMetierException(
                    "Ajoutez la filière " + filiere.getLibelle()
                    + " à votre profil avant d'y enregistrer un élevage");
        }
    }

    /**
     * Un type supprime par l'administration (actif = false) n'accueille plus
     * de nouvel elevage. Les elevages qui l'emploient deja ne sont pas
     * touches : c'est la promesse de la pop-up « Supprimer » de la maquette.
     */
    private static void exigerTypePropose(TypeElevage type) {
        if (!type.isActif()) {
            throw new RegleMetierException(
                    "Le type d'élevage « " + type.getLibelle() + " » n'est plus proposé. "
                    + "Choisissez-en un autre.");
        }
    }

    private TypeElevage typeElevage(Long id) {
        return typeElevageRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Type d'élevage", id));
    }

    private static String prefixe(Filiere filiere) {
        return filiere.name().substring(0, 3);
    }

    /**
     * Code fourni par l'eleveur, ou engendre.
     *
     * <p>Le compteur repart du nombre d'unites DEJA creees par cet eleveur,
     * toutes filieres confondues : le code n'a a etre unique que chez lui.
     * Un compteur global ferait sauter la numerotation de chacun des que
     * quelqu'un d'autre cree un lot.
     */
    private String codeOuGenere(String fourni, String prefixe, Eleveur eleveur) {
        if (fourni != null && !fourni.isBlank()) {
            String propre = fourni.strip().toUpperCase();
            if (uniteRepository.existsByProprietaireIdAndCode(eleveur.getId(), propre)) {
                throw new ConflitException("Le code " + propre + " est déjà utilisé");
            }
            return propre;
        }
        long rang = uniteRepository.countByProprietaireId(eleveur.getId()) + 1;
        String candidat;
        do {
            candidat = "%s-%03d".formatted(prefixe, rang++);
        } while (uniteRepository.existsByProprietaireIdAndCode(eleveur.getId(), candidat));
        return candidat;
    }

    /** Sert au tableau de bord : le total des tetes vivantes d'une filiere. */
    public long compterTetesActives(Eleveur eleveur, Filiere filiere) {
        return lotRepository.sommerEffectifActif(eleveur.getId(), filiere)
             + animalRepository.compterActifs(eleveur.getId(), filiere);
    }

    /** Sert au tableau de bord : les tetes deja sorties du cheptel. */
    public long compterTetesSorties(Eleveur eleveur, Filiere filiere, LocalDate depuis) {
        return lotRepository.sommerTetesSorties(eleveur.getId(), filiere, depuis);
    }
}
