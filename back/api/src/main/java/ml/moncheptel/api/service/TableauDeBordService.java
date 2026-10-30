package ml.moncheptel.api.service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import ml.moncheptel.api.dto.reponse.TableauDeBordReponse;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.StatutUnite;
import ml.moncheptel.api.repository.AnimalRepository;
import ml.moncheptel.api.repository.LotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ecran d'accueil de l'eleveur.
 *
 * <h2>Un seul appel pour tout l'ecran</h2>
 *
 * <p>Compteurs, solde, alertes, rappels, cloche de notifications : six sources
 * differentes, une seule reponse. Six appels HTTP feraient apparaitre l'ecran
 * par morceaux sur un reseau lent — et un morceau qui echoue laisserait un
 * tableau de bord a moitie vide sans que l'eleveur sache lequel.
 *
 * <h2>Les compteurs suivent la filiere, le solde non</h2>
 *
 * <p>C'est ce que dit la maquette : les tuiles changent avec l'onglet, mais la
 * carte porte « Solde du mois — toutes filieres ». Ce n'est pas une
 * incoherence : un eleveur a plusieurs troupeaux et une seule tresorerie.
 *
 * <h2>Le mode de suivi decide du libelle</h2>
 *
 * <p>« Lots actifs » en volaille, « Animaux actifs » en bovins. Le serveur ne
 * renvoie pas ces libelles — il renvoie les nombres et la filiere affichee ;
 * c'est le client qui choisit les mots, parce qu'ils dependent de la langue.
 */
@Service
@Transactional(readOnly = true)
public class TableauDeBordService {

    /** Horizon des rappels affiches, en jours. */
    private static final int HORIZON_RAPPELS = 30;

    private final LotRepository lotRepository;
    private final AnimalRepository animalRepository;
    private final FinanceService financeService;
    private final AlerteService alerteService;
    private final SanitaireService sanitaireService;
    private final NotificationService notificationService;

    public TableauDeBordService(LotRepository lotRepository,
                                AnimalRepository animalRepository,
                                FinanceService financeService,
                                AlerteService alerteService,
                                SanitaireService sanitaireService,
                                NotificationService notificationService) {
        this.lotRepository = lotRepository;
        this.animalRepository = animalRepository;
        this.financeService = financeService;
        this.alerteService = alerteService;
        this.sanitaireService = sanitaireService;
        this.notificationService = notificationService;
    }

    public TableauDeBordReponse pour(Eleveur eleveur, Filiere filiere) {
        LocalDate aujourdHui = LocalDate.now();
        Filiere affichee = filiere != null ? filiere : premiereFiliere(eleveur);

        boolean parLot = affichee != null && affichee.suiviParLot();

        long actives = parLot
                ? lotRepository.compterParStatut(eleveur.getId(), affichee, StatutUnite.ACTIF)
                : animalRepository.compterActifs(eleveur.getId(), affichee);
        long cloturees = parLot
                ? lotRepository.compterParStatut(eleveur.getId(), affichee, StatutUnite.CLOTURE)
                : animalRepository.compterSortis(eleveur.getId(), affichee);

        long tetesRestantes = lotRepository.sommerEffectifActif(eleveur.getId(), affichee)
                + animalRepository.compterActifs(eleveur.getId(), affichee);
        long tetesSorties = lotRepository.sommerTetesSorties(eleveur.getId(), affichee, null)
                + animalRepository.compterSortis(eleveur.getId(), affichee);

        return new TableauDeBordReponse(
                eleveur.getNom(),
                eleveur.getZone() == null ? null : eleveur.getZone().getNom(),
                // filieresOrdonnees, pas getFilieresPratiquees : cette liste
                // devient la barre d'onglets, son ordre doit etre stable d'un
                // demarrage a l'autre.
                eleveur.filieresOrdonnees(),
                affichee,
                (int) actives,
                (int) tetesRestantes,
                (int) cloturees,
                (int) tetesSorties,
                // Volontairement sans filiere : « toutes filieres ».
                financeService.soldeDuMois(eleveur, null, aujourdHui),
                aujourdHui.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH),
                alerteService.pourEleveur(eleveur),
                sanitaireService.rappelsDuTableauDeBord(eleveur.getId(), HORIZON_RAPPELS),
                notificationService.compterNonLues(eleveur.getId()));
    }

    /**
     * Filiere affichee par defaut : la premiere de {@link Filiere#values()}
     * que l'eleveur pratique — donc {@code VOLAILLE} des qu'il en fait.
     *
     * <h3>Le defaut que cette methode corrige</h3>
     *
     * <p>Elle etait ecrite {@code getFilieresPratiquees().stream().findFirst()}
     * et son commentaire annoncait « la premiere que l'eleveur pratique ».
     * Mais {@code filieresPratiquees} est un {@code Set} — une
     * {@code @ElementCollection} qu'Hibernate materialise en {@code HashSet}.
     * <b>Un {@code HashSet} n'a pas de premiere</b> : {@code findFirst()} y
     * prend ce que l'ordre de hachage donne. Pour un eleveur pratiquant les
     * quatre filieres, le tableau de bord ouvrait sur {@code CAPRINS} et
     * affichait « 2 unites, 2 tetes » au lieu des trois lots de volaille et de
     * leurs 320 tetes.
     *
     * <p>Les compteurs n'etaient pas faux : ils etaient justes <i>pour la
     * mauvaise filiere</i>. C'est la forme de defaut la plus couteuse a
     * trouver, parce que rien n'echoue.
     *
     * <h3>Pourquoi parcourir {@code Filiere.values()}</h3>
     *
     * <p>L'ordre de declaration de l'enumeration est un ordre <b>stable et
     * lisible</b> : {@code VOLAILLE, BOVINS, OVINS, CAPRINS}. Il place la
     * volaille en tete, ce qu'exige la maquette — son ecran d'accueil est
     * « Dashboard - Volaille » — et il ne changera pas d'une execution a
     * l'autre.
     *
     * <p><b>La contrainte que cela cree :</b> reordonner les constantes de
     * {@link Filiere} change l'ecran d'accueil de tous les eleveurs. C'est
     * ecrit ici pour qu'on le sache avant de le faire.
     *
     * <p>Nulle si l'eleveur ne pratique aucune filiere — un compte tout juste
     * cree dont le profil n'est pas complete. Les compteurs valent alors zero
     * et l'ecran doit inviter a ajouter une filiere, pas planter.
     */
    private static Filiere premiereFiliere(Eleveur eleveur) {
        // filieresOrdonnees rend un EnumSet : sa premiere valeur EST la
        // premiere de l'ordre de declaration. Une seule methode possede la
        // regle d'ordre, ici comme pour la barre d'onglets.
        return eleveur.filieresOrdonnees().stream().findFirst().orElse(null);
    }
}
