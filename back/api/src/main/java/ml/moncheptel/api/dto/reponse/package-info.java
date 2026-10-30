/**
 * Les reponses de l'API — la couche « Vue » de ce MVC.
 *
 * <p>Une API REST n'a pas de vue au sens Thymeleaf : rien n'est rendu ici en
 * HTML. Ce sont ces objets, serialises en JSON, que Flutter et Angular
 * affichent. C'est donc ici, et nulle part ailleurs, que se decide ce qui sort
 * du serveur.
 *
 * <h2>Deux regles tenues partout</h2>
 *
 * <p><b>Aucune entite n'est renvoyee directement.</b> Serialiser un
 * {@code Utilisateur} exposerait son empreinte de mot de passe, son compteur
 * de tentatives et la date jusqu'a laquelle il est verrouille. Un
 * {@code Lot} entrainerait son proprietaire, qui entrainerait ses lots :
 * Jackson tourne en rond jusqu'a la pile pleine. Chaque ecran a sa reponse.
 *
 * <p><b>Les enumerations sortent en CODE, pas en libelle.</b>
 * {@code "gravite": "ELEVE"}, pas {@code "Élevé"}. Le libelle est de
 * l'affichage : il depend de la langue de l'utilisateur, et l'application le
 * connait. Un libelle fige dans l'API obligerait a redeployer le back pour
 * corriger un accent. Les libelles du modele servent aux textes que le serveur
 * redige lui-meme — corps des notifications, courriels.
 */
package ml.moncheptel.api.dto.reponse;
