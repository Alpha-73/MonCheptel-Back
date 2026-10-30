package ml.moncheptel.api.dto.reponse;

import java.time.LocalDate;
import java.util.List;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;

/**
 * Les series des graphiques de l'ecran « Statistiques ».
 *
 * <p>Les series temporelles sont completes : douze semaines et douze mois,
 * meme a zero. Un trou dans l'axe se lirait comme une absence de donnees, pas
 * comme une semaine calme.
 */
public record SeriesStatistiquesReponse(
        List<Semaine> semaines,
        List<Mois> inscriptions,
        Entonnoir entonnoir,
        List<Tranche> delais,
        List<MaladieSignalee> maladies,
        Moteur moteur) {

    /** Signalements d'une semaine (du lundi), par gravite. */
    public record Semaine(LocalDate debut, long faible, long moyen, long eleve) { }

    /** Inscriptions d'un mois civil, « 2026-10 ». */
    public record Mois(String mois, long eleveurs, long veterinaires) { }

    /** Parcours des signalements des trente derniers jours. */
    public record Entonnoir(long signales, long aOrienter, long orientes,
                            long prisEnCharge, long traites) { }

    /** Delai entre le signalement et la prise en charge. */
    public record Tranche(String tranche, long nombre) { }

    /**
     * {@code confirmes} : cas ou le veterinaire a retenu cette maladie ;
     * {@code proposes} : cas ou le moteur la propose et qu'aucun veterinaire
     * n'a encore tranches.
     */
    public record MaladieSignalee(String maladie, NiveauGravite gravite, long confirmes, long proposes) { }

    /** Cas tranches par un veterinaire, et combien confirmaient le moteur. */
    public record Moteur(long verifies, long concordants) { }
}
