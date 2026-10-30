package ml.moncheptel.api.service;

import java.util.List;
import ml.moncheptel.api.dto.reponse.MaladieAdminReponse;
import ml.moncheptel.api.dto.reponse.MaladieReponse;
import ml.moncheptel.api.dto.reponse.SymptomeReponse;
import ml.moncheptel.api.dto.reponse.TypeElevageReponse;
import ml.moncheptel.api.dto.reponse.ZoneReponse;
import ml.moncheptel.api.dto.requete.MaladieRequete;
import ml.moncheptel.api.dto.requete.PoidsSymptomeRequete;
import ml.moncheptel.api.dto.requete.PosteCoutRequete;
import ml.moncheptel.api.dto.requete.TypeElevageRequete;
import ml.moncheptel.api.exception.ConflitException;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.mapper.MaladieMapper;
import ml.moncheptel.api.mapper.ReferentielMapper;
import ml.moncheptel.api.mapper.ZoneMapper;
import ml.moncheptel.api.modele.Maladie;
import ml.moncheptel.api.modele.enumeration.TypeNotification;
import ml.moncheptel.api.modele.MaladieSymptome;
import ml.moncheptel.api.modele.PosteCout;
import ml.moncheptel.api.modele.Symptome;
import ml.moncheptel.api.modele.TypeElevage;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.repository.MaladieRepository;
import ml.moncheptel.api.repository.MaladieSymptomeRepository;
import ml.moncheptel.api.repository.SymptomeRepository;
import ml.moncheptel.api.repository.TypeElevageRepository;
import ml.moncheptel.api.repository.UniteElevageRepository;
import ml.moncheptel.api.repository.VeterinaireRepository;
import ml.moncheptel.api.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Referentiel : types d'elevage, maladies, symptomes, zones.
 *
 * <h2>Ce referentiel n'est pas de la documentation</h2>
 *
 * <p>Chaque nombre saisi ici ressort en FCFA sur l'ecran d'investissement d'un
 * eleveur, ou en pourcentage sur un ecran de diagnostic. Une ligne de
 * ponderation mal remplie deplace un diagnostic ; un montant mal saisi
 * deplace une decision d'investissement. C'est pourquoi les bornes sont
 * verifiees ici, en plus des annotations.
 */
@Service
@Transactional(readOnly = true)
public class ReferentielService {

    private final TypeElevageRepository typeElevageRepository;
    private final MaladieRepository maladieRepository;
    private final MaladieSymptomeRepository maladieSymptomeRepository;
    private final SymptomeRepository symptomeRepository;
    private final ZoneRepository zoneRepository;
    private final VeterinaireRepository veterinaireRepository;
    private final UniteElevageRepository uniteRepository;
    private final ReferentielMapper referentielMapper;
    private final MaladieMapper maladieMapper;
    private final ZoneMapper zoneMapper;

    public ReferentielService(TypeElevageRepository typeElevageRepository,
                              MaladieRepository maladieRepository,
                              MaladieSymptomeRepository maladieSymptomeRepository,
                              SymptomeRepository symptomeRepository,
                              ZoneRepository zoneRepository,
                              VeterinaireRepository veterinaireRepository,
                              UniteElevageRepository uniteRepository,
                              ReferentielMapper referentielMapper,
                              MaladieMapper maladieMapper,
                              ZoneMapper zoneMapper) {
        this.typeElevageRepository = typeElevageRepository;
        this.maladieRepository = maladieRepository;
        this.maladieSymptomeRepository = maladieSymptomeRepository;
        this.symptomeRepository = symptomeRepository;
        this.zoneRepository = zoneRepository;
        this.veterinaireRepository = veterinaireRepository;
        this.uniteRepository = uniteRepository;
        this.referentielMapper = referentielMapper;
        this.maladieMapper = maladieMapper;
        this.zoneMapper = zoneMapper;
    }

    // ── types d'elevage ─────────────────────────────────────────────────────

    /** Version administration : actifs d'abord, puis les types retires. */
    public List<TypeElevageReponse> typesElevage(Filiere filiere) {
        return referentielMapper.versReponses(
                typeElevageRepository.chercherPourAdministration(filiere));
    }

    /** Version eleveur : les types desactives par l'administration sont exclus. */
    public List<TypeElevageReponse> typesElevageActifs(Filiere filiere) {
        return referentielMapper.versReponses(filiere == null
                ? typeElevageRepository.chercherTousAvecCouts()
                : typeElevageRepository.chercherParFiliereAvecCouts(filiere));
    }

    public TypeElevageReponse typeElevage(Long id) {
        return referentielMapper.versReponse(typeElevageRepository.chercherAvecCouts(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Type d'élevage", id)));
    }

    /**
     * Creation d'un type d'elevage.
     *
     * <p>La durée de cycle est exigee en PAR_PERIODE et refusee en PAR_ENTREE.
     * C'est une coherence entre deux champs : une annotation ne peut pas la
     * poser, et sans elle un lot de bovins se verrait attribuer une date de
     * cloture prevue qui n'a aucun sens.
     */
    @Transactional
    public TypeElevageReponse creerTypeElevage(TypeElevageRequete requete) {
        exigerCoherenceDuCycle(requete);
        exigerLibelleLibre(requete, null);

        TypeElevage type = new TypeElevage(
                requete.filiere(), requete.libelle().strip(), requete.modeEvaluation());
        appliquer(type, requete);
        typeElevageRepository.save(type);
        return referentielMapper.versReponse(type);
    }

    @Transactional
    public TypeElevageReponse modifierTypeElevage(Long id, TypeElevageRequete requete) {
        // LA FILIERE D'UN TYPE NE CHANGE PAS S'IL EST EMPLOYE.
        //
        // UniteElevage.getFiliere() est DERIVEE du type. Changer la filiere
        // ici reclasserait toutes les unites qui l'emploient — ce que
        // CheptelService.modifier interdit unite par unite, avec un
        // argumentaire detaille, mais en masse et sans aucune trace.
        //
        // Et la divergence serait permanente : OperationFinanciere.filiere est
        // une colonne STOCKEE, qui ne suivrait pas. Les ecritures passees
        // resteraient dans l'ancienne filiere et les tetes dans la nouvelle ;
        // le solde par filiere et le nombre de tetes raconteraient deux
        // histoires, definitivement.
        TypeElevage existant = typeElevageRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Type d'élevage", id));
        if (existant.getFiliere() != requete.filiere()) {
            long unites = uniteRepository.countByTypeElevageId(id);
            if (unites > 0) {
                throw new RegleMetierException(
                        "Ce type d'élevage est employé par " + unites + " élevage(s) : "
                        + "sa filière ne peut plus changer. Leur journal et leur "
                        + "comptabilité sont rattachés à " + existant.getFiliere().getLibelle()
                        + ". Créez un nouveau type dans l'autre filière.");
            }
        }
        exigerCoherenceDuCycle(requete);
        exigerLibelleLibre(requete, id);
        TypeElevage type = typeElevageRepository.chercherAvecCouts(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Type d'élevage", id));

        type.setFiliere(requete.filiere());
        type.setLibelle(requete.libelle().strip());
        type.setModeEvaluation(requete.modeEvaluation());
        appliquer(type, requete);

        // Les postes sont remplaces en bloc : l'ecran d'administration les
        // envoie tous. Un rapprochement ligne a ligne demanderait des
        // identifiants stables que le formulaire ne porte pas.
        type.getPostesCout().clear();
        ajouterPostes(type, requete.postesCout());
        return referentielMapper.versReponse(type);
    }

    /**
     * Retrait d'un type (suppression LOGIQUE, actif = false) : il disparait de
     * la page Logistique, du simulateur et des formulaires de l'eleveur ; les
     * elevages qui l'emploient gardent leur historique. Reversible
     * (reactivation). Idempotent.
     */
    @Transactional
    public void retirerTypeElevage(Long id) {
        TypeElevage type = typeElevageRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Type d'élevage", id));
        type.setActif(false);
    }

    /**
     * Suppression DEFINITIVE d'un type : la ligne, ses postes de cout et son
     * protocole sanitaire disparaissent de la base.
     *
     * <p>Refusee (409) si un elevage, meme cloture, emploie ce type : sa
     * fiche, son carnet et sa rentabilite en dependent. L'administration le
     * retire alors a la place.
     */
    @Transactional
    public void supprimerTypeElevage(Long id) {
        TypeElevage type = typeElevageRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Type d'élevage", id));
        long utilisations = uniteRepository.countByTypeElevageId(id);
        if (utilisations > 0) {
            throw new ConflitException("« " + type.getLibelle() + " » est utilisé par "
                    + utilisations + (utilisations > 1 ? " élevages" : " élevage")
                    + " : il ne peut pas être supprimé définitivement. Retirez-le à la place.");
        }
        typeElevageRepository.delete(type);
    }

    /** Remet en service un type retire : il revient dans les listes et le simulateur. */
    @Transactional
    public TypeElevageReponse reactiverTypeElevage(Long id) {
        TypeElevage type = typeElevageRepository.chercherAvecCouts(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Type d'élevage", id));
        type.setActif(true);
        return referentielMapper.versReponse(type);
    }

    /**
     * Deux « Poulet de chair » dans la meme filiere : l'eleveur ne saurait
     * pas lequel choisir. Un type retire garde son nom : on propose de le
     * remettre en service plutot que d'en creer un second.
     */
    private void exigerLibelleLibre(TypeElevageRequete requete, Long exclu) {
        if (typeElevageRepository.libelleDejaPris(requete.filiere(), requete.libelle().strip(), exclu)) {
            throw new ConflitException("Un type « " + requete.libelle().strip() + " » existe déjà en "
                    + requete.filiere().getLibelle()
                    + ". S'il a été retiré, remettez-le en service plutôt que d'en créer un second.");
        }
    }

    private static void exigerCoherenceDuCycle(TypeElevageRequete requete) {
        boolean parPeriode = requete.modeEvaluation() == ModeEvaluation.PAR_PERIODE;
        if (parPeriode && requete.dureeCycleJours() == null) {
            throw new RegleMetierException(
                    "Un suivi par période demande une durée de cycle en jours");
        }
        if (!parPeriode && requete.dureeCycleJours() != null) {
            throw new RegleMetierException(
                    "Un suivi par entrée n'a pas de durée de cycle : "
                    + "un bovin ne se clôt pas au bout de 45 jours");
        }
        if (requete.temperatureMin() != null && requete.temperatureMax() != null
                && requete.temperatureMin().compareTo(requete.temperatureMax()) > 0) {
            throw new RegleMetierException(
                    "La température minimale dépasse la température maximale");
        }
    }

    private void appliquer(TypeElevage type, TypeElevageRequete r) {
        type.setDureeCycleJours(r.dureeCycleJours());
        type.setCapaciteReference(r.capaciteReference());
        type.setEspaceParSujetM2(r.espaceParSujetM2());
        type.setTemperatureMin(r.temperatureMin());
        type.setTemperatureMax(r.temperatureMax());
        type.setAlimentParSujetKg(r.alimentParSujetKg());
        type.setEauParSujetJourL(r.eauParSujetJourL());
        type.setVaccinsObligatoires(r.vaccinsObligatoires());
        type.setTauxMortalite(r.tauxMortalite());
        type.setDifficulte(r.difficulte());
        if (type.getPostesCout().isEmpty()) {
            ajouterPostes(type, r.postesCout());
        }
    }

    private void ajouterPostes(TypeElevage type, List<PosteCoutRequete> postes) {
        if (postes == null) {
            return;
        }
        for (PosteCoutRequete p : postes) {
            type.ajouterPosteCout(new PosteCout(type, p.libelle().strip(), p.montant(),
                    p.unite(), p.phase(), p.modeQuantite(), p.coefficient(), p.ordre()));
        }
    }

    // ── maladies et symptomes ───────────────────────────────────────────────

    /** Catalogue tel qu'un ELEVEUR le voit : traitement masque si orientation. */
    public List<MaladieReponse> maladiesPourEleveur(Filiere filiere) {
        List<Maladie> maladies = filiere == null
                ? maladieRepository.findByActiveTrueOrderByNomAsc()
                : maladieRepository.chercherParFiliere(filiere);
        return maladies.stream().map(maladieMapper::versReponseEleveur).toList();
    }

    /** Catalogue complet, pour l'administration et les veterinaires. */
    public List<MaladieReponse> maladiesCompletes() {
        return maladieRepository.findByActiveTrueOrderByNomAsc()
                .stream().map(maladieMapper::versReponseAdmin).toList();
    }

    /** Ecran « Catalogue des maladies » : avec ponderations et competences. */
    public List<MaladieAdminReponse> maladiesPourAdministration(boolean avecRetirees) {
        List<Maladie> maladies = avecRetirees
                ? maladieRepository.findAll(org.springframework.data.domain.Sort.by("nom"))
                : maladieRepository.findByActiveTrueOrderByNomAsc();
        return maladies.stream().map(this::versAdministration).toList();
    }

    public MaladieAdminReponse maladiePourAdministration(Long id) {
        return versAdministration(maladieRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Maladie", id)));
    }

    private MaladieAdminReponse versAdministration(Maladie m) {
        List<MaladieAdminReponse.PoidsSymptome> poids = maladieSymptomeRepository
                .findByMaladieId(m.getId()).stream()
                .sorted(java.util.Comparator.comparing(MaladieSymptome::getPoids).reversed())
                .map(ms -> new MaladieAdminReponse.PoidsSymptome(
                        ms.getSymptome().getId(), ms.getSymptome().getLibelle(), ms.getPoids()))
                .toList();
        return new MaladieAdminReponse(m.getId(), m.getNom(), m.getGravite(),
                new java.util.TreeSet<>(m.getFilieres()), m.getSymptomesTexte(),
                m.getTraitement(), m.isOrienteVersVeterinaire(), m.isActive(), poids,
                veterinaireRepository.compterCompetents(m.getId(), null),
                m.getAjouteePar());
    }

    public List<SymptomeReponse> symptomes(Filiere filiere) {
        List<Symptome> trouves = filiere == null
                ? symptomeRepository.findAllByOrderByOrdreAscLibelleAsc()
                : symptomeRepository.chercherParFiliere(filiere);
        return maladieMapper.versReponses(trouves);
    }

    /**
     * Ajoute un symptome a la liste (administration ou veterinaire).
     *
     * <p>Un libelle deja present, a la casse pres, rend le symptome existant
     * au lieu d'echouer : deux « Toux » feraient deux cases identiques chez
     * l'eleveur.
     */
    @Transactional
    public SymptomeReponse creerSymptome(String libelle) {
        String propre = libelle.strip().replaceAll("\\s+", " ");
        if (propre.isEmpty()) {
            throw new RegleMetierException("Indiquez le symptôme");
        }
        Symptome symptome = symptomeRepository.findByLibelleIgnoreCase(propre)
                .orElseGet(() -> symptomeRepository.save(
                        new Symptome(Character.toUpperCase(propre.charAt(0)) + propre.substring(1),
                                symptomeRepository.ordreMaximum() + 1)));
        return maladieMapper.versReponse(symptome);
    }

    @Transactional
    public MaladieAdminReponse creerMaladie(MaladieRequete requete) {
        maladieRepository.findByNomIgnoreCase(requete.nom().strip()).ifPresent(existante -> {
            throw new ConflitException(existante.isActive()
                    ? "Une maladie porte déjà ce nom : " + requete.nom()
                    : "« " + existante.getNom() + " » a été retirée du catalogue : "
                      + "réactivez-la plutôt que d'en créer une autre.");
        });
        Maladie maladie = new Maladie(requete.nom().strip(), requete.gravite(), requete.filieres());
        maladie.setSymptomesTexte(requete.symptomesTexte());
        maladie.setTraitement(requete.traitement());
        maladie.setOrienteVersVeterinaire(requete.orienteVersVeterinaire());
        maladieRepository.save(maladie);
        ponderer(maladie, requete.symptomes());
        maladieSymptomeRepository.flush();
        return versAdministration(maladie);
    }

    /**
     * Ajout par un veterinaire : meme regles que {@link #creerMaladie}, plus la
     * TRACE de son auteur et un avis a l'administration.
     *
     * <p>Decision du projet : la maladie est utilisable tout de suite. Le
     * jugement medical appartient aux veterinaires ; l'administrateur n'en est
     * pas forcement un. Il garde la tracabilite (« Ajoutée par Dr X ») et le
     * droit de retrait.
     */
    @Transactional
    public MaladieAdminReponse creerMaladieParVeterinaire(MaladieRequete requete, String nomVeterinaire) {
        MaladieAdminReponse creee = creerMaladie(requete);
        Maladie maladie = maladieRepository.findById(creee.id())
                .orElseThrow(() -> RessourceIntrouvableException.de("Maladie", creee.id()));
        maladie.setAjouteePar(nomVeterinaire);
        if (avisAdministration != null) {
            avisAdministration.prevenir(TypeNotification.MODERATION,
                    "Maladie ajoutée par un vétérinaire",
                    "« " + maladie.getNom() + " » a été ajoutée au catalogue par " + nomVeterinaire
                    + ". Elle est déjà utilisée par le diagnostic ; vous pouvez la retirer si besoin.",
                    "MALADIE", maladie.getId());
        }
        return versAdministration(maladie);
    }

    /** Facultatif : voir SignalementService.setAvisAdministration. */
    private AvisAdministration avisAdministration;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAvisAdministration(AvisAdministration avisAdministration) {
        this.avisAdministration = avisAdministration;
    }

    /**
     * Corrige une maladie du catalogue : ecran « Modifier une maladie ».
     *
     * <h3>Les ponderations sont REMPLACEES, pas fusionnees</h3>
     *
     * <p>L'ecran envoie la liste complete des symptomes avec leur poids. On
     * efface donc les lignes existantes avant de reposer les nouvelles.
     *
     * <p>Fusionner laisserait vivre une ponderation que l'administrateur vient
     * de retirer a l'ecran : le moteur de diagnostic continuerait a en tenir
     * compte, et personne ne comprendrait pourquoi la maladie sort encore sur
     * un symptome qu'on a decoche.
     *
     * <h3>Ce que cette correction change immediatement</h3>
     *
     * <p>Le classement du diagnostic assiste. La maquette l'annonce : « Toute
     * modification est immediatement visible par les eleveurs et reconnue par
     * le signalement ». Les signalements DEJA enregistres gardent en revanche
     * la maladie qui leur avait ete attribuee — ils portent un resultat, pas
     * une regle de calcul.
     */
    @Transactional
    public MaladieAdminReponse modifierMaladie(Long id, MaladieRequete requete) {
        Maladie maladie = maladieRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Maladie", id));

        if (!maladie.getNom().equalsIgnoreCase(requete.nom())
                && maladieRepository.existsByNomIgnoreCase(requete.nom())) {
            throw new ConflitException("Une maladie porte déjà ce nom : " + requete.nom());
        }
        maladie.setNom(requete.nom().strip());
        maladie.setGravite(requete.gravite());
        maladie.setFilieres(requete.filieres());
        maladie.setSymptomesTexte(requete.symptomesTexte());
        maladie.setTraitement(requete.traitement());
        maladie.setOrienteVersVeterinaire(requete.orienteVersVeterinaire());

        maladieSymptomeRepository.deleteByMaladieId(maladie.getId());
        // Vide la file avant de reecrire : sans ce flush, les suppressions
        // resteraient en attente, et le controle de doublon de ponderer()
        // verrait encore les anciennes lignes — il refuserait alors toutes les
        // ponderations, y compris celles qui n'ont pas change.
        maladieSymptomeRepository.flush();
        ponderer(maladie, requete.symptomes());
        maladieSymptomeRepository.flush();
        return versAdministration(maladie);
    }

    /**
     * Retire une maladie du catalogue — en la DESACTIVANT.
     *
     * <h3>Pourquoi pas une suppression</h3>
     *
     * <p>La maquette dit « Supprimer du catalogue », et sa pop-up « Oui,
     * retirer ». C'est le second mot qui est juste.
     *
     * <p>Une maladie est citee par des signalements, des alertes, des
     * competences de veterinaires et des conseils. La supprimer violerait
     * quatre cles etrangeres — ou, pire, emporterait l'historique sanitaire de
     * tous les eleveurs qui l'ont signalee.
     *
     * <p>{@code active = false} la retire du catalogue, du diagnostic et des
     * listes de signalement. Tout ce qui la cite continue de se relire.
     *
     * <p>L'ecran doit donc dire « retirer », pas « supprimer definitivement ».
     */
    @Transactional
    public void retirerMaladie(Long id) {
        Maladie maladie = maladieRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Maladie", id));
        maladie.setActive(false);
    }

    @Transactional
    public MaladieAdminReponse reactiverMaladie(Long id) {
        Maladie maladie = maladieRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.de("Maladie", id));
        maladie.setActive(true);
        return versAdministration(maladie);
    }

    /**
     * Enregistre les ponderations symptome/maladie.
     *
     * <p>Un symptome pondere deux fois sur la meme maladie doublerait son
     * poids dans le calcul, sans que rien ne l'indique a l'ecran. Le doublon
     * est donc refuse plutot qu'ecrase.
     */
    private void ponderer(Maladie maladie, List<PoidsSymptomeRequete> lignes) {
        for (PoidsSymptomeRequete ligne : lignes) {
            Symptome symptome = symptomeRepository.findById(ligne.symptomeId())
                    .orElseThrow(() -> RessourceIntrouvableException.de(
                            "Symptome", ligne.symptomeId()));
            if (maladieSymptomeRepository.existsByMaladieIdAndSymptomeId(
                    maladie.getId(), symptome.getId())) {
                throw new ConflitException(
                        "Le symptôme « " + symptome.getLibelle()
                        + " » est déjà pondéré pour cette maladie");
            }
            maladieSymptomeRepository.save(
                    new MaladieSymptome(maladie, symptome, ligne.poids()));
        }
    }

    // ── zones ───────────────────────────────────────────────────────────────

    public List<ZoneReponse> zones() {
        return zoneRepository.findAllByOrderByNomAsc()
                .stream().map(zoneMapper::versReponse).toList();
    }
}
