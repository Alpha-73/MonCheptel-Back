package ml.moncheptel.api.dto.reponse;

import ml.moncheptel.api.modele.enumeration.NiveauGravite;

/**
 * Ecran « Signalement - Resultat ».
 *
 * <h2>Pourquoi aucune probabilite ne figure ici</h2>
 *
 * <p>Le moteur calcule un score et classe les candidates ; ce score est
 * conserve sur le signalement. Il ne sort PAS de l'API.
 *
 * <p>Les 115 ecrans de la maquette ont ete relus sur ce point : aucun
 * n'affiche de pourcentage de diagnostic. L'ecran de resultat dit « MALADIE
 * PROBABLE — Maladie de Newcastle », « Autre hypothese : bronchite
 * infectieuse », et « Ce resultat n'est pas un diagnostic ». Le « 30% du lot »
 * de l'ecran veterinaire est la proportion de tetes touchees, pas une
 * confiance.
 *
 * <p>C'est la bonne decision, et pas seulement parce que la maquette le dit :
 * afficher « 65 % contre 28 % » demanderait a un eleveur d'arbitrer entre deux
 * nombres qu'il n'a aucun moyen de juger, issus d'une ponderation qu'aucun
 * veterinaire n'a encore validee. Deux noms, une gravite et une conduite a
 * tenir sont une orientation ; deux pourcentages sont une illusion de mesure.
 *
 * <p>Le score reste en base : il classe les candidates, et il permettra un
 * jour de mesurer si le moteur vaut quelque chose, en confrontant ce qu'il
 * proposait a ce que le veterinaire a conclu.
 */
public record DiagnosticReponse(
        MaladieReponse probable,
        MaladieReponse alternative,
        /** Croisement de la gravite de la maladie et de l'ampleur declaree. */
        NiveauGravite gravite,
        /** Vraie si le cas doit partir chez un veterinaire. */
        boolean orientationRequise,
        /** Renseigne quand l'orientation a abouti ; nul sinon. */
        VeterinaireResumeReponse veterinaire) {
}
