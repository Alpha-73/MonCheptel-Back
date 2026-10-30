package ml.moncheptel.api.modele.enumeration;

/**
 * Comment se calcule la rentabilite d'un type d'elevage.
 *
 * <p>Les deux onglets de l'ecran « Rentabilite », et la note de
 * « ModifierAnimal - Rempli » : « Le type d'elevage determine la frequence
 * d'evaluation de la rentabilite. Le modifier recalcule la periode en cours. »
 *
 * <p>La distinction est metier, pas technique, et se lit sur les noms :
 * <ul>
 *   <li>{@code PAR_PERIODE} : l'elevage se juge sur une PERIODE bornee, son
 *       cycle — un lot de poulets de chair sur ses 45 jours, un belier
 *       d'embouche sur 90, un veau d'embouche sur 180. Le type porte une
 *       duree de cycle ;</li>
 *   <li>{@code PAR_ENTREE} : pas de cycle a clore ; l'animal se juge de son
 *       ENTREE a sa sortie (la chevre sahelienne de V3). Le type n'a pas de
 *       duree de cycle.</li>
 * </ul>
 *
 * <p>Une version precedente de ce commentaire decrivait l'inverse (« par
 * bande » pour l'un, « par periode, sur un an » pour l'autre) ; le code et le
 * referentiel V3 ont toujours suivi la regle ci-dessus. Voir
 * ReferentielService.exigerCoherenceDuCycle.
 */
public enum ModeEvaluation {

    /** Sans duree de cycle : l'animal se juge sur son entree et sa sortie. */
    PAR_ENTREE("Par entrée"),

    /** Avec une duree de cycle : « Cycle en cours — LOT-014, jour 16 sur 45 ». */
    PAR_PERIODE("Par période");

    private final String libelle;

    ModeEvaluation(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
