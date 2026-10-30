package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.Sexe;

/**
 * Correction d'un lot ou d'un animal deja enregistre.
 *
 * <p>Correspond a l'ecran « Modifier le lot » de la maquette, et a son
 * equivalent pour un animal.
 *
 * <h2>L'EFFECTIF N'EST PAS ICI, ET C'EST DELIBERE</h2>
 *
 * <p>La maquette affiche « Effectif · 180 » sur cet ecran, ce qui laisse
 * croire qu'on peut le corriger. <b>Il ne faut pas.</b>
 *
 * <p>L'effectif d'un lot n'est pas une donnee qu'on saisit : c'est le resultat
 * du JOURNAL. Une mise en place de 180 tetes, moins douze morts, moins
 * cinquante vendues, font 118. Le chiffre stocke et la somme des mouvements
 * doivent coincider — {@code LotRepository.chercherIncoherences} existe
 * precisement pour detecter le contraire.
 *
 * <p>Permettre de taper « 200 » dans ce champ ferait diverger les deux, sans
 * qu'aucune erreur ne soit levee. La rentabilite, le tableau de bord et les
 * rapports se mettraient a raconter trois histoires differentes.
 *
 * <p><b>La bonne facon de corriger un effectif</b> est d'enregistrer le
 * mouvement qui manque : une entree si on avait sous-compte, une sortie sinon.
 * Le journal garde alors la trace de la correction, avec sa date et son auteur.
 *
 * <p>Sur l'ecran, ce champ doit donc etre en lecture seule, avec une mention
 * du genre « corrige par une entree ou une sortie ».
 *
 * <h2>Changer le type d'elevage recalcule le cycle</h2>
 *
 * <p>La maquette le dit : « Le modifier recalcule la periode en cours ». C'est
 * exact, et c'est fait — {@code Lot.recalculerCloture()}. Passer d'un poulet
 * de chair de 45 jours a une pondeuse d'un an change la date de cloture prevue
 * et donc tous les rappels a venir.
 *
 * <p>Les rappels du protocole suivent aussi la <b>date d'entree</b> : la
 * corriger de quelques jours decale leurs echeances d'autant. Seuls les
 * rappels engendres par le protocole et encore ouverts bougent ; un rappel
 * fait, ignore ou ajoute a la main reste tel quel. Jusqu'au 5 octobre, ce
 * paragraphe promettait les rappels sans que le code les touche. Voir
 * {@code SanitaireService.reprogrammerRappels}.
 *
 * @param race          ignores pour un lot : ces trois champs n'existent que
 * @param sexe          sur un animal suivi individuellement
 * @param dateNaissance
 */
public record UniteRequete(

        @Size(max = 20) String code,

        @NotNull(message = "Le type d'élevage est obligatoire")
        Long typeElevageId,

        @NotNull(message = "La date d'entrée est obligatoire")
        LocalDate dateEntree,

        @Size(max = 500) String note,

        @Size(max = 60) String race,

        Sexe sexe,

        LocalDate dateNaissance) {
}
