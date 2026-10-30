package ml.moncheptel.api.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import ml.moncheptel.api.dto.reponse.DetailSimulationReponse;
import ml.moncheptel.api.dto.reponse.LignePosteReponse;
import ml.moncheptel.api.dto.reponse.OptionInvestissementReponse;
import ml.moncheptel.api.dto.reponse.SimulationReponse;
import ml.moncheptel.api.dto.requete.SimulationRequete;
import ml.moncheptel.api.exception.RessourceIntrouvableException;
import ml.moncheptel.api.modele.PosteCout;
import ml.moncheptel.api.modele.TypeElevage;
import ml.moncheptel.api.modele.enumeration.PhaseCout;
import ml.moncheptel.api.repository.TypeElevageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulateur d'investissement — ecrans « Investir ».
 *
 * <h2>D'ou viennent les chiffres</h2>
 *
 * <p>De nulle part ailleurs que du referentiel. Ce service ne connait aucun
 * prix : il lit les postes de cout saisis par l'administration, les multiplie
 * et additionne. Un chiffre faux a l'ecran est un chiffre faux dans le
 * referentiel, corrigeable sans redeployer — c'est exactement l'inverse d'un
 * bareme code en dur.
 *
 * <h2>Les deux questions de l'ecran</h2>
 *
 * <p>« Si vous partez de zero » additionne INSTALLATION et CYCLE. « Vous avez
 * deja un poulailler ? » ne garde que le CYCLE. C'est
 * {@link PhaseCout} qui porte la distinction, et c'est pour cela qu'elle
 * existe.
 *
 * <h2>La mortalite previsionnelle est montree, pas absorbee</h2>
 *
 * <p>Viser 30 poulets vivants demande d'en acheter 33 a 8 %. Les deux nombres
 * sortent. Un eleveur qui decouvre en fin de cycle qu'il lui en manque trois
 * cesse de faire confiance a l'outil — et il a raison.
 */
@Service
@Transactional(readOnly = true)
public class InvestissementService {

    /**
     * Plafond de securite de la recherche par dichotomie. Un budget enorme sur
     * un poste a 1 FCFA donnerait sinon un nombre de sujets absurde et un
     * calcul tres long.
     */
    private static final int SUJETS_MAX = 1_000_000;

    private final TypeElevageRepository typeElevageRepository;

    public InvestissementService(TypeElevageRepository typeElevageRepository) {
        this.typeElevageRepository = typeElevageRepository;
    }

    public SimulationReponse simuler(SimulationRequete requete) {
        // Les variantes « avecCouts », pas les findBy... simples : le simulateur
        // parcourt les postes de cout de chaque type. Sans chargement groupe,
        // chiffrer quatre filieres declencherait une requete par type puis une
        // par collection de postes.
        List<TypeElevage> types = requete.filiere() == null
                ? typeElevageRepository.chercherTousAvecCouts()
                : typeElevageRepository.chercherParFiliereAvecCouts(requete.filiere());

        List<OptionInvestissementReponse> options = new ArrayList<>(types.size());
        for (TypeElevage type : types) {
            options.add(evaluer(type, requete));
        }

        // Les options tenables d'abord ; parmi les autres, la moins loin du
        // budget en premier : c'est celle qui merite d'etre connue.
        options.sort(Comparator
                .comparing(OptionInvestissementReponse::realisable).reversed()
                .thenComparing(o -> -o.ecartBudget()));

        return new SimulationReponse(requete.budget(), requete.inclureInstallation(), options);
    }

    /** Ecran « Investir - Detail » : la meme option, poste par poste. */
    public DetailSimulationReponse detailler(Long typeElevageId, SimulationRequete requete) {
        // Un type supprime « disparait du simulateur » (maquette) : son
        // detail aussi, sinon un ancien lien le ferait revivre.
        TypeElevage type = typeElevageRepository.chercherAvecCouts(typeElevageId)
                .filter(TypeElevage::isActif)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Type d'élevage introuvable : " + typeElevageId));

        OptionInvestissementReponse option = evaluer(type, requete);
        int achetes = option.sujetsAAcheter();

        return new DetailSimulationReponse(
                option,
                lignes(type, achetes, PhaseCout.INSTALLATION),
                lignes(type, achetes, PhaseCout.CYCLE));
    }

    // ── calcul ──────────────────────────────────────────────────────────────

    /**
     * Chiffre une filiere pour ce budget.
     *
     * <h3>Le cas « meme un seul sujet ne tient pas dans le budget »</h3>
     *
     * <p>{@code sujetsTenablesDansLeBudget} rend alors {@code 0}. Compter les
     * postes avec {@code sujetsAchetes = 0} donnait un resultat absurde et
     * dangereux : les postes PAR_SUJET tombaient a zero, les postes FORFAIT
     * restaient entiers, et l'option s'affichait
     *
     *     « Ovins — realisable, 25 000 FCFA, il vous reste 25 000 »
     *
     * pour ZERO mouton. Pire, le tri place d'abord le plus gros reliquat :
     * cette option fantome passait DEVANT la volaille, qui etait la seule
     * vraie reponse. Sur un budget de 50 000 FCFA, l'ecran recommandait donc
     * la filiere la moins accessible des quatre.
     *
     * <p>Ce que la maquette demande dans ce cas, c'est le TICKET D'ENTREE :
     * « il vous manque 30 000 FCFA ». On chiffre donc le plus petit elevage
     * possible — un sujet vise — et on declare l'option hors budget. Le
     * manque devient lisible, et {@code realisable} dit la verite.
     *
     * <p>{@code sujetsVises} rend alors 1 et non 0 : c'est le nombre que le
     * cout affiche decrit. Rendre 0 avec un cout non nul etait precisement
     * l'incoherence a corriger.
     */
    private OptionInvestissementReponse evaluer(TypeElevage type, SimulationRequete requete) {
        boolean choisiParLEleveur = requete.sujetsVises() != null && requete.sujetsVises() > 0;
        int vises = choisiParLEleveur
                ? requete.sujetsVises()
                : sujetsTenablesDansLeBudget(type, requete.budget(), requete.inclureInstallation());

        // Hors budget des le premier sujet : on chiffre le ticket d'entree.
        boolean horsBudget = (vises <= 0);
        if (horsBudget) {
            vises = 1;
        }

        int achetes = type.sujetsAAcheter(vises);

        long installation = coutDeLaPhase(type, achetes, PhaseCout.INSTALLATION);
        long cycle = coutDeLaPhase(type, achetes, PhaseCout.CYCLE);
        long totalDu = requete.inclureInstallation() ? installation + cycle : cycle;

        return new OptionInvestissementReponse(
                type.getId(),
                type.getLibelle(),
                type.getFiliere(),
                type.getDifficulte(),
                type.getDureeCycleJours(),
                vises,
                achetes,
                installation,
                cycle,
                totalDu,
                requete.budget() - totalDu,
                requete.budget() >= totalDu);
    }

    private long coutDeLaPhase(TypeElevage type, int sujetsAchetes, PhaseCout phase) {
        long total = 0;
        for (PosteCout poste : type.getPostesCout()) {
            if (poste.getPhase() == phase) {
                total += poste.calculer(sujetsAchetes, type.getCapaciteReference());
            }
        }
        return total;
    }

    /**
     * Combien de sujets ce budget permet-il de viser.
     *
     * <p>Resolu par dichotomie, pas par une division. Le cout n'est pas
     * proportionnel au nombre de sujets : les postes FORFAIT et PAR_CAPACITE
     * ne bougent pas, la mortalite previsionnelle arrondit au superieur, et
     * chaque poste arrondit son propre resultat au franc. Une division
     * donnerait un nombre plausible et faux d'un ou deux sujets — c'est-a-dire
     * un budget depasse a l'arrivee.
     *
     * <p>La dichotomie, elle, rend le plus grand {@code n} tel que le cout
     * reel de {@code n} tienne dans le budget. Elle interroge la vraie
     * fonction de cout, arrondis compris.
     */
    private int sujetsTenablesDansLeBudget(TypeElevage type, long budget, boolean avecInstallation) {
        if (coutPour(type, 1, avecInstallation) > budget) {
            return 0;
        }
        int bas = 1;
        int haut = SUJETS_MAX;
        while (bas < haut) {
            int milieu = bas + (haut - bas + 1) / 2;
            if (coutPour(type, milieu, avecInstallation) <= budget) {
                bas = milieu;
            } else {
                haut = milieu - 1;
            }
        }
        return bas;
    }

    private long coutPour(TypeElevage type, int sujetsVises, boolean avecInstallation) {
        int achetes = type.sujetsAAcheter(sujetsVises);
        long cycle = coutDeLaPhase(type, achetes, PhaseCout.CYCLE);
        return avecInstallation ? cycle + coutDeLaPhase(type, achetes, PhaseCout.INSTALLATION) : cycle;
    }

    /**
     * Decompose une phase en lignes affichables.
     *
     * <p>La quantite est recalculee ici a partir du mode et du coefficient —
     * c'est elle qui permet d'ecrire « 59,4 kg x 500 FCFA » plutot qu'un total
     * opaque que personne ne peut contester.
     */
    private List<LignePosteReponse> lignes(TypeElevage type, int sujetsAchetes, PhaseCout phase) {
        List<LignePosteReponse> lignes = new ArrayList<>();
        for (PosteCout poste : type.getPostesCout()) {
            if (poste.getPhase() != phase) {
                continue;
            }
            BigDecimal base = switch (poste.getModeQuantite()) {
                case PAR_SUJET -> BigDecimal.valueOf(sujetsAchetes);
                case PAR_CAPACITE -> BigDecimal.valueOf((long) type.getCapaciteReference()
                        * PosteCout.batiments(sujetsAchetes, type.getCapaciteReference()));
                case FORFAIT -> BigDecimal.ONE;
            };
            BigDecimal quantite = base.multiply(poste.getCoefficient())
                    .setScale(2, RoundingMode.HALF_UP)
                    .stripTrailingZeros();
            lignes.add(new LignePosteReponse(
                    poste.getLibelle(),
                    poste.getPhase(),
                    quantite,
                    poste.getUnite(),
                    poste.getMontant(),
                    poste.calculer(sujetsAchetes, type.getCapaciteReference())));
        }
        return lignes;
    }
}
