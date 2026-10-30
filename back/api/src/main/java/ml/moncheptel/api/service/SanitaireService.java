package ml.moncheptel.api.service;

import java.time.LocalDate;
import java.util.List;
import ml.moncheptel.api.dto.reponse.EvenementReponse;
import ml.moncheptel.api.dto.reponse.RappelReponse;
import ml.moncheptel.api.dto.requete.EvenementSanitaireRequete;
import ml.moncheptel.api.exception.AccesRefuseException;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.mapper.UniteMapper;
import ml.moncheptel.api.modele.CalendrierSanitaire;
import ml.moncheptel.api.modele.EvenementSanitaire;
import ml.moncheptel.api.modele.RappelSanitaire;
import ml.moncheptel.api.modele.UniteElevage;
import ml.moncheptel.api.modele.Veterinaire;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;
import ml.moncheptel.api.modele.Utilisateur;
import ml.moncheptel.api.repository.CalendrierSanitaireRepository;
import ml.moncheptel.api.repository.EvenementSanitaireRepository;
import ml.moncheptel.api.repository.RappelSanitaireRepository;
import ml.moncheptel.api.repository.SignalementMaladieRepository;
import ml.moncheptel.api.repository.UniteElevageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carnet sanitaire et rappels.
 *
 * <h2>Pourquoi les rappels sont engendres a la creation du lot</h2>
 *
 * <p>Le referentiel porte un protocole par type d'elevage : « vaccination
 * Newcastle a J+7 », « Gumboro a J+14 ». A la creation d'un lot, ces lignes
 * deviennent des rappels dates.
 *
 * <p>Ils auraient pu etre calcules a la lecture, sans rien stocker. Mais un
 * rappel est un objet vivant : l'eleveur le solde, l'ignore, le decale. Un
 * calcul a la volee n'aurait nulle part ou ecrire cet etat, et l'eleveur
 * reverrait chaque jour un rappel qu'il a deja traite.
 */
@Service
@Transactional(readOnly = true)
public class SanitaireService {

    private final EvenementSanitaireRepository evenementRepository;
    private final RappelSanitaireRepository rappelRepository;
    private final CalendrierSanitaireRepository calendrierRepository;
    private final UniteElevageRepository uniteRepository;
    private final SignalementMaladieRepository signalementRepository;
    private final UniteMapper uniteMapper;

    public SanitaireService(EvenementSanitaireRepository evenementRepository,
                            RappelSanitaireRepository rappelRepository,
                            CalendrierSanitaireRepository calendrierRepository,
                            UniteElevageRepository uniteRepository,
                            SignalementMaladieRepository signalementRepository,
                            UniteMapper uniteMapper) {
        this.evenementRepository = evenementRepository;
        this.rappelRepository = rappelRepository;
        this.calendrierRepository = calendrierRepository;
        this.uniteRepository = uniteRepository;
        this.signalementRepository = signalementRepository;
        this.uniteMapper = uniteMapper;
    }

    /**
     * Deroule le protocole du referentiel sur une unite qui vient d'etre creee.
     *
     * <p>Aucune ligne de protocole : aucun rappel, et c'est normal — un bovin
     * adulte achete n'a pas de calendrier de poussin.
     */
    @Transactional
    public void programmerRappels(UniteElevage unite) {
        List<CalendrierSanitaire> protocole = calendrierRepository
                .findByTypeElevageIdOrderByJourDuCycleAsc(unite.getTypeElevage().getId());
        for (CalendrierSanitaire ligne : protocole) {
            rappelRepository.save(RappelSanitaire.depuisCalendrier(unite, ligne));
        }
    }

    /**
     * Remet les rappels du protocole en accord avec l'unite, apres une
     * correction de sa date d'entree ou de son type d'elevage.
     *
     * <h3>Le defaut que cette methode corrige</h3>
     *
     * <p>Les rappels sont calcules UNE FOIS, a la creation : « date d'entree
     * + jour du cycle ». Corriger la date d'entree — l'eleveur s'est trompe de
     * quelques jours, cas ordinaire — laissait les rappels sur l'ancienne
     * date. Le lot recalculait sa cloture, et la maquette annoncait que
     * « modifier recalcule la periode » ; le carnet, lui, restait faux, et
     * c'est lui qui dit quand vacciner.
     *
     * <h3>Ce qui bouge, et ce qui ne bouge pas</h3>
     *
     * <p>Seuls les rappels engendres par le protocole et encore ouverts sont
     * touches ({@link RappelSanitaire#estProgrammeEtOuvert}). Un rappel deja
     * fait, ignore, ou ajoute a la main est une decision de l'eleveur.
     *
     * <ul>
     *   <li><b>date seule</b> : les echeances sont recalculees en place. Rien
     *       n'est supprime, rien n'est cree : un rappel deja solde ne peut pas
     *       renaitre en double ;</li>
     *   <li><b>type change</b> : les rappels ouverts de l'ancien protocole
     *       sont supprimes et ceux du nouveau sont engendres. Les lignes d'un
     *       autre type n'ont pas les memes identifiants : un acte deja fait
     *       selon l'ancien protocole ne supprime donc pas son equivalent du
     *       nouveau. C'est une limite assumee, pour un cas rare — changer de
     *       type en cours de cycle.</li>
     * </ul>
     */
    @Transactional
    public void reprogrammerRappels(UniteElevage unite, boolean typeChange) {
        List<RappelSanitaire> ouverts = rappelRepository
                .findByUniteIdOrderByDateEcheanceAsc(unite.getId())
                .stream().filter(RappelSanitaire::estProgrammeEtOuvert).toList();
        if (typeChange) {
            rappelRepository.deleteAll(ouverts);
            programmerRappels(unite);
        } else {
            ouverts.forEach(r -> r.reprogrammer(unite.getDateEntree()));
        }
    }

    /**
     * Ecran « Ajouter un evenement ».
     *
     * <p>Enregistrer une vaccination solde automatiquement le rappel
     * correspondant, s'il en existe un dans la fenetre. Sans cela, l'eleveur
     * devrait faire le geste deux fois — saisir l'acte, puis cocher le rappel —
     * et il oublierait le second.
     */
    @Transactional
    public EvenementReponse enregistrer(Utilisateur auteur, Long uniteId,
                                        EvenementSanitaireRequete requete) {
        UniteElevage unite = charger(uniteId, auteur);
        ControleDates.exigerDansLaVieDe(unite, requete.dateEvenement(), "du soin");

        EvenementSanitaire evenement = evenementRepository.save(new EvenementSanitaire(
                unite, requete.type(), requete.dateEvenement(),
                requete.produitObservation(), auteur));

        solderRappelCorrespondant(unite, evenement);

        if (requete.rappelDansJours() != null && requete.rappelDansJours() > 0) {
            // Le type de l'acte est memorise : un soin du meme type, plus
            // tard, soldera ce rappel comme un rappel du protocole (F10).
            rappelRepository.save(RappelSanitaire.libre(
                    unite, requete.type(),
                    "Rappel " + requete.type().getLibelle().toLowerCase(),
                    requete.dateEvenement().plusDays(requete.rappelDansJours())));
        }
        return uniteMapper.versReponse(evenement);
    }

    /**
     * Corrige un evenement deja enregistre : ecran « Modifier l'evenement ».
     *
     * <h3>Pourquoi l'unite n'est pas modifiable</h3>
     *
     * <p>Un evenement appartient a un elevage. Le deplacer vers un autre
     * reviendrait a dire qu'une vaccination a ete faite ailleurs : le carnet
     * sanitaire des deux unites deviendrait faux, et un rappel solde par cet
     * acte resterait solde sur la premiere. Si l'acte a ete saisi sur le
     * mauvais lot, il faut le supprimer et le ressaisir au bon endroit.
     *
     * <h3>Le rappel deja solde n'est pas rouvert</h3>
     *
     * <p>Si l'acte avait solde un rappel et qu'on change sa date de trois
     * jours, le rappel reste solde. Rouvrir puis re-chercher un correspondant
     * ferait osciller l'etat du rappel a chaque correction de frappe, et
     * pourrait en solder un autre. Le lien pose a l'enregistrement tient.
     *
     * <p><b>Limite assumee :</b> corriger la date de six mois laisserait un
     * rappel solde par un acte tres eloigne. Rare, et visible dans le carnet.
     *
     * <h3>Changer le TYPE rouvre le rappel</h3>
     *
     * <p>C'est la seule correction qui recalcule le soldage. Voir le corps de
     * la methode : un acte devenu « traitement » n'a pas vaccine.
     */
    @Transactional
    public EvenementReponse modifier(Utilisateur auteur, Long evenementId,
                                     EvenementSanitaireRequete requete) {
        EvenementSanitaire evenement = chargerEvenement(auteur, evenementId);
        ControleDates.exigerDansLaVieDe(evenement.getUnite(), requete.dateEvenement(), "du soin");
        boolean typeChange = evenement.getType() != requete.type();
        evenement.setType(requete.type());
        evenement.setDateEvenement(requete.dateEvenement());
        evenement.setProduitObservation(requete.produitObservation());

        // Changer le TYPE de l'acte change ce qu'il a accompli : une
        // « vaccination » devenue « traitement » n'a pas vaccine. Le rappel de
        // vaccin qu'elle avait regle doit se rouvrir — sinon l'eleveur croit
        // son lot vaccine, personne ne le relance, et c'est exactement le
        // silence que rouvrir() existe pour eviter a la suppression.
        //
        // Puis on cherche un correspondant pour le NOUVEAU type : remettre
        // « vaccination » regle de nouveau le rappel, et la correction est
        // reversible. Seul le type declenche ce recalcul ; une date corrigee
        // de trois jours ne le fait pas (voir ci-dessus).
        if (typeChange) {
            UniteElevage unite = evenement.getUnite();
            rappelRepository.findByUniteIdOrderByDateEcheanceAsc(unite.getId()).stream()
                    .filter(r -> r.getEvenement() != null
                              && r.getEvenement().getId().equals(evenement.getId()))
                    .forEach(RappelSanitaire::rouvrir);
            solderRappelCorrespondant(unite, evenement);
        }
        return uniteMapper.versReponse(evenement);
    }

    /**
     * Supprime un evenement : « Supprimer cet evenement », avec confirmation.
     *
     * <p>Contrairement a un mouvement de cheptel, un evenement sanitaire ne
     * porte ni argent ni effectif : le supprimer ne desynchronise rien. C'est
     * pourquoi il est, lui, reellement supprimable.
     *
     * <p>Le rappel qu'il avait solde est ROUVERT. Sans cela, une vaccination
     * saisie par erreur puis supprimee laisserait le rappel marque « fait » :
     * l'eleveur croirait son lot vaccine, et personne ne le relancerait. C'est
     * le cas ou l'etat du rappel doit bouger, a l'inverse de la correction.
     */
    @Transactional
    public void supprimerEvenement(Utilisateur auteur, Long evenementId) {
        EvenementSanitaire evenement = chargerEvenement(auteur, evenementId);
        rappelRepository.findByUniteIdOrderByDateEcheanceAsc(evenement.getUnite().getId())
                .stream()
                .filter(r -> r.getEvenement() != null
                          && r.getEvenement().getId().equals(evenementId))
                .forEach(RappelSanitaire::rouvrir);
        evenementRepository.delete(evenement);
    }

    /**
     * Charge un evenement en verifiant que l'appelant a le droit d'y toucher.
     *
     * <p>Le proprietaire de l'unite, ou le veterinaire qui a pose l'acte. Un
     * veterinaire ne corrige pas une saisie faite par l'eleveur, et
     * reciproquement : chacun repond de ce qu'il a ecrit.
     */
    private EvenementSanitaire chargerEvenement(Utilisateur auteur, Long evenementId) {
        EvenementSanitaire evenement = evenementRepository.findById(evenementId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Événement", evenementId));
        UniteElevage unite = evenement.getUnite();
        boolean proprietaire = unite.getProprietaire().getId().equals(auteur.getId());
        boolean sonActe = evenement.getAuteur() != null
                && evenement.getAuteur().getId().equals(auteur.getId());
        if (!proprietaire && !sonActe) {
            throw new AccesRefuseException();
        }
        // Un veterinaire ne corrige son acte que tant que le cas est ouvert :
        // c'est le meme lien que pour l'ecrire (audit global G3).
        if (!proprietaire && auteur instanceof Veterinaire veterinaire
                && !aUnCasOuvert(unite, veterinaire)) {
            throw new AccesRefuseException();
        }
        // L'eleveur ne reecrit pas l'acte pose par le veterinaire (audit F14,
        // rappele en G3) : l'application l'affiche deja en lecture seule.
        boolean acteDUnTiers = evenement.getAuteur() != null && !sonActe;
        if (proprietaire && acteDUnTiers) {
            throw new RegleMetierException(
                    "Cet acte a été noté par le vétérinaire : seul lui peut le modifier.");
        }
        // Carnet clos apres « Terminer » (audit global G10) : la creation
        // etait deja refusee, la correction et la suppression aussi.
        if (unite.getStatut().estSortie()) {
            throw new RegleMetierException(
                    "L'unité " + unite.getCode() + " est sortie du cheptel : "
                    + "son carnet sanitaire est clos");
        }
        return evenement;
    }

    /**
     * Cherche un rappel du meme type autour de la date de l'acte et le solde.
     *
     * <p>La fenetre est large — sept jours de part et d'autre — parce qu'un
     * eleveur vaccine quand le veterinaire passe, pas au jour dit. Exiger la
     * date exacte reviendrait a ne jamais rien solder.
     */
    private void solderRappelCorrespondant(UniteElevage unite, EvenementSanitaire evenement) {
        LocalDate acte = evenement.getDateEvenement();
        rappelRepository.findByUniteIdOrderByDateEcheanceAsc(unite.getId()).stream()
                // Rappels du protocole ET rappels libres « dans N jours » : les
                // seconds restaient ouverts pour toujours (audit F10).
                .filter(r -> r.attend(evenement.getType()))
                .filter(r -> !r.getDateEcheance().isBefore(acte.minusDays(FENETRE_JOURS))
                          && !r.getDateEcheance().isAfter(acte.plusDays(FENETRE_JOURS)))
                .findFirst()
                .ifPresent(r -> r.solder(evenement));
    }

    /** Tolerance, en jours, entre l'echeance d'un rappel et l'acte qui le solde. */
    private static final int FENETRE_JOURS = 7;

    /**
     * L'eleveur ecarte un rappel : « je ne ferai pas ce vaccin ».
     *
     * <h3>Le PROPRIETAIRE seul, pas le veterinaire</h3>
     *
     * <p>C'est la seule methode du service reservee au proprietaire, et c'est
     * deliberе. Ecrire un acte au carnet constate un fait — le veterinaire est
     * passe, il a vaccine. Ignorer un rappel est une DECISION sur l'elevage de
     * quelqu'un d'autre : renoncer a un vaccin, en acceptant le risque.
     *
     * <p>Un veterinaire peut la conseiller ; il n'a pas a la prendre, et
     * surtout pas a la prendre en silence sur un lot qu'il ne possede pas.
     */
    @Transactional
    public void ignorerRappel(Utilisateur auteur, Long rappelId) {
        RappelSanitaire rappel = rappelRepository.findById(rappelId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Rappel", rappelId));
        if (!rappel.getUnite().getProprietaire().getId().equals(auteur.getId())) {
            throw new AccesRefuseException();
        }
        rappel.ignorer();
    }

    /**
     * Rappels a afficher : jusqu'a {@code horizonJours} devant, et au plus
     * {@link #RETARD_MAX_JOURS} jours de retard. Au-dela, un rappel manque
     * n'est plus une action a faire mais du bruit : il restait affiche
     * « en retard de 200 jours » (audit F9). Il reste lisible dans le carnet.
     */
    public List<RappelReponse> rappelsDuTableauDeBord(Long eleveurId, int horizonJours) {
        LocalDate aujourdHui = LocalDate.now();
        return uniteMapper.versRappels(rappelRepository.chercherAAfficher(
                eleveurId, aujourdHui.minusDays(RETARD_MAX_JOURS), aujourdHui.plusDays(horizonJours)));
    }

    /**
     * Notifie aux eleveurs les soins du jour et du lendemain (audit eleveur
     * F5, audit global G9) : {@code TypeNotification.RAPPEL} n'etait jamais
     * emis. Une notification par eleveur et par jour d'echeance, qui liste
     * ses soins — pas une par rappel, qui noierait la cloche. Seules les
     * unites encore actives comptent.
     *
     * @return le nombre de notifications creees
     */
    @Transactional
    public int notifierRappels(LocalDate aujourdHui, NotificationService notifications) {
        int creees = 0;
        for (LocalDate jour : List.of(aujourdHui, aujourdHui.plusDays(1))) {
            java.util.Map<Long, List<RappelSanitaire>> parEleveur = new java.util.LinkedHashMap<>();
            for (RappelSanitaire r : rappelRepository.chercherPourNotification(jour)) {
                if (r.getUnite().getStatut().estSortie()) {
                    continue;
                }
                parEleveur.computeIfAbsent(r.getUnite().getProprietaire().getId(),
                        k -> new java.util.ArrayList<>()).add(r);
            }
            boolean demain = jour.isAfter(aujourdHui);
            for (List<RappelSanitaire> rappels : parEleveur.values()) {
                var eleveur = rappels.get(0).getUnite().getProprietaire();
                String liste = resumerSoins(rappels.stream()
                        .map(r -> r.getLibelle() + " (" + r.getUnite().getCode() + ")")
                        .toList());
                java.util.Set<Long> unites = rappels.stream()
                        .map(r -> r.getUnite().getId())
                        .collect(java.util.stream.Collectors.toSet());
                Long uniteSeule = unites.size() == 1 ? unites.iterator().next() : null;
                notifications.creer(eleveur,
                        ml.moncheptel.api.modele.enumeration.TypeNotification.RAPPEL,
                        demain ? "Soins à faire demain" : "Soins à faire aujourd'hui",
                        liste + ".",
                        uniteSeule == null ? null : "UNITE", uniteSeule);
                creees++;
            }
        }
        return creees;
    }

    /** Soins nommes dans une notification ; les suivants sont comptes. */
    static final int SOINS_NOMMES_MAX = 3;

    /** Longueur maximale d'un soin nomme, code de l'unite compris. */
    static final int LONGUEUR_SOIN_MAX = 80;

    /**
     * Corps de la notification de rappel : les trois premiers soins, puis
     * « et N autres ».
     *
     * <p>Audit du 10 octobre : la liste complete etait concatenee. Au-dela
     * d'environ treize rappels le meme jour (quinze bovins enregistres
     * ensemble, par exemple), le texte depassait les 500 caracteres de
     * {@code Notification.corps}. La validation levait alors une exception
     * et annulait TOUTE la tache du jour : plus aucun eleveur ne recevait de
     * rappel. Le resume tient toujours sous 300 caracteres ; le detail reste
     * dans l'ecran Sante.
     */
    static String resumerSoins(List<String> soins) {
        List<String> nommes = soins.stream()
                .limit(SOINS_NOMMES_MAX)
                .map(t -> t.length() <= LONGUEUR_SOIN_MAX ? t : t.substring(0, LONGUEUR_SOIN_MAX - 1) + "…")
                .toList();
        String texte = String.join(", ", nommes);
        int reste = soins.size() - nommes.size();
        if (reste > 0) {
            texte += " et " + reste + (reste > 1 ? " autres soins" : " autre soin");
        }
        return texte;
    }

    /** Retard au-dela duquel un rappel n'est plus affiche hors du carnet. */
    public static final int RETARD_MAX_JOURS = 30;

    /**
     * Ecarte tous les rappels encore ouverts d'une unite : elle vient d'etre
     * terminee, ses echeances n'ont plus d'objet.
     */
    @Transactional
    public int solderRappelsOuverts(UniteElevage unite) {
        List<RappelSanitaire> ouverts = rappelRepository
                .findByUniteIdOrderByDateEcheanceAsc(unite.getId()).stream()
                .filter(r -> r.getStatut() == ml.moncheptel.api.modele.enumeration.StatutRappel.A_VENIR
                          || r.getStatut() == ml.moncheptel.api.modele.enumeration.StatutRappel.ECHU)
                .toList();
        ouverts.forEach(RappelSanitaire::ecarterALaSortie);
        return ouverts.size();
    }

    /**
     * La sortie qui avait termine l'unite est annulee : ses rappels ecartes
     * a cette occasion reviennent (audit du 29 octobre, point 7). Ceux que
     * l'eleveur avait ignores lui-meme restent ignores.
     */
    @Transactional
    public int rouvrirRappelsEcartes(UniteElevage unite) {
        List<RappelSanitaire> ecartes = rappelRepository
                .findByUniteIdOrderByDateEcheanceAsc(unite.getId()).stream()
                .filter(RappelSanitaire::isEcarteALaSortie)
                .toList();
        ecartes.forEach(RappelSanitaire::rouvrirApresSortieAnnulee);
        return ecartes.size();
    }

    /**
     * Reevalue les rappels echus. Appelee une fois par jour.
     *
     * <p>Un rappel ne devient pas « echu » tout seul : son statut est stocke,
     * et rien ne le relit entre deux consultations. Sans ce passage
     * quotidien, un rappel depasse resterait affiche « a venir » jusqu'a ce
     * qu'un eleveur ouvre l'ecran.
     */
    @Transactional
    public int reevaluerEcheances() {
        LocalDate aujourdHui = LocalDate.now();
        List<RappelSanitaire> echus = rappelRepository.chercherEchus(aujourdHui);
        echus.forEach(r -> r.reevaluer(aujourdHui));
        return echus.size();
    }

    // ── outillage ───────────────────────────────────────────────────────────

    private UniteElevage charger(Long uniteId, Utilisateur auteur) {
        UniteElevage unite = uniteRepository.findById(uniteId)
                .orElseThrow(() -> RessourceIntrouvableException.de("Unité", uniteId));
        exigerAutorisationEcriture(unite, auteur);
        if (unite.getStatut().estSortie()) {
            throw new RegleMetierException(
                    "L'unité " + unite.getCode() + " est sortie du cheptel : "
                    + "son carnet sanitaire est clos");
        }
        return unite;
    }

    /**
     * Qui a le droit d'ecrire au carnet de cette unite.
     *
     * <p>Le proprietaire, toujours. Un veterinaire, seulement s'il a un cas
     * OUVERT (oriente ou en cours) sur cette unite.
     *
     * <h3>Le defaut que cette regle corrige (audit global G3)</h3>
     *
     * <p>La regle acceptait aussi tout veterinaire dont la zone d'intervention
     * etait celle de l'eleveur, sans aucun signalement en commun. Les
     * identifiants d'unite se suivent : un veterinaire pouvait inscrire des
     * actes — qui soldent les rappels — sur toutes les unites de sa zone,
     * sans que l'eleveur l'ait appele. La zone est retiree ; un cas deja
     * traite ne suffit plus non plus.
     */
    private void exigerAutorisationEcriture(UniteElevage unite, Utilisateur auteur) {
        if (unite.getProprietaire().getId().equals(auteur.getId())) {
            return;
        }
        if (auteur instanceof Veterinaire veterinaire && aUnCasOuvert(unite, veterinaire)) {
            return;
        }
        throw new AccesRefuseException();
    }

    private boolean aUnCasOuvert(UniteElevage unite, Veterinaire veterinaire) {
        return signalementRepository.existsByUniteIdAndVeterinaireIdAndStatutIn(
                unite.getId(), veterinaire.getId(), CAS_OUVERTS);
    }

    /** Statuts d'un cas sur lequel le veterinaire travaille encore. */
    private static final java.util.Set<StatutSignalement> CAS_OUVERTS =
            java.util.EnumSet.of(StatutSignalement.ORIENTE, StatutSignalement.EN_COURS);
}
