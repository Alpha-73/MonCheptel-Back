package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.ModeEvaluation;
import ml.moncheptel.api.modele.enumeration.NiveauDifficulte;

/**
 * Ecran « Admin - Referentiel : fiche ». C'est ce referentiel qui alimente le
 * simulateur d'investissement : chaque chiffre saisi ici sort en FCFA a
 * l'ecran d'un eleveur.
 *
 * <p>{@code tauxMortalite} est une FRACTION (0,08 pour 8 %), pas un
 * pourcentage. Le meme champ interprete des deux facons multiplierait par cent
 * le nombre de sujets a acheter ; le libelle de l'ecran doit dire lequel des
 * deux il attend.
 */
public record TypeElevageRequete(
        @NotNull Filiere filiere,
        @NotBlank @Size(max = 80) String libelle,
        @NotNull ModeEvaluation modeEvaluation,

        /** Obligatoire en PAR_PERIODE, ignore en PAR_ENTREE — verifie dans le service. */
        @Positive Integer dureeCycleJours,

        @Positive int capaciteReference,
        @DecimalMin("0.0") BigDecimal espaceParSujetM2,
        Integer temperatureMin,
        Integer temperatureMax,
        @DecimalMin("0.0") BigDecimal alimentParSujetKg,
        @DecimalMin("0.0") BigDecimal eauParSujetJourL,
        @Size(max = 500) String vaccinsObligatoires,

        @NotNull @DecimalMin("0.0")
        @DecimalMax(value = "0.9", message = "Un taux de mortalite previsionnel de 90 % n'a pas de sens")
        BigDecimal tauxMortalite,

        @NotNull NiveauDifficulte difficulte,

        /**
         * Au moins un poste de cout. Mesure du 5 octobre, en lisant
         * {@code modifierTypeElevage} :
         *
         * <pre>
         *   type.getPostesCout().clear();          // orphanRemoval = true
         *   ajouterPostes(type, requete.postesCout());
         * </pre>
         *
         * <p>et {@code ajouterPostes} sort en silence sur une liste nulle. Le
         * champ ne portait aucune contrainte : un administrateur qui corrigeait
         * un libelle depuis un client ne renvoyant pas les postes EFFACAIT
         * definitivement la structure de cout du type — le {@code clear()}
         * supprime les lignes en base, et rien ne les remplacait.
         *
         * <p>La consequence n'etait pas un ecran vide mais un chiffre faux :
         * {@code coutDeLaPhase} parcourt une liste vide et rend 0, donc
         * {@code sujetsTenablesDansLeBudget} interroge une fonction de cout
         * nulle partout et sa dichotomie monte jusqu'a {@code SUJETS_MAX}. Le
         * simulateur aurait annonce a un eleveur qu'il peut acheter le maximum
         * de sujets pour 0 FCFA.
         *
         * <p>La contrainte vaut aussi a la CREATION, et c'est voulu : un type
         * sans aucun poste ne peut pas etre chiffre, donc il n'a pas sa place
         * dans le referentiel qui alimente le simulateur.
         *
         * <p>Les postes restent remplaces EN BLOC, inchange : l'ecran les
         * envoie tous. Ce qui est refuse, c'est une requete qui n'en apporte
         * aucun — elle ne peut plus effacer ceux qui existent.
         */
        @NotEmpty(message = "Un type d'élevage porte au moins un poste de coût : "
                + "sans eux le simulateur le chiffrerait a zero")
        List<PosteCoutRequete> postesCout) {
}
