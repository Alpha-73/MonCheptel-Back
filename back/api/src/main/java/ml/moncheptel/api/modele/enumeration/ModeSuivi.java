package ml.moncheptel.api.modele.enumeration;

/**
 * Comment une filiere se suit.
 *
 * <p>C'est la distinction qui traverse toute la maquette : la volaille se
 * compte par bandes, les autres betes se suivent une par une. Elle n'est pas
 * codee en dur — elle tient dans cette enumeration, portee par {@link Filiere}.
 */
public enum ModeSuivi {

    /** Une bande de sujets, avec un effectif. Ecran « MesAnimaux - Volaille lots ». */
    LOT,

    /** Une bete a la fois, avec une race, un sexe, une date de naissance. */
    INDIVIDUEL
}
