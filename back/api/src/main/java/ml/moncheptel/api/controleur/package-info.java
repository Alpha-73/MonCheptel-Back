/**
 * Controleurs REST — la couche « Controleur » de ce MVC.
 *
 * <h2>Ce qu'un controleur fait, et rien de plus</h2>
 *
 * <p>Il traduit HTTP en appel de service, et le retour en code de statut.
 * Aucune regle metier n'est ecrite ici. Un controleur qui verifierait un
 * effectif ou un droit dupliquerait une regle que le service porte deja, et
 * les deux copies divergeraient a la premiere evolution.
 *
 * <p>Aucun {@code try/catch} non plus : les exceptions metier remontent au
 * {@code GestionnaireExceptions}, qui les traduit en une seule forme de
 * reponse. Attraper localement produirait autant de formats d'erreur que de
 * controleurs, et le client devrait tous les connaitre.
 *
 * <h2>Conventions tenues partout</h2>
 *
 * <ul>
 *   <li>Racine {@code /api} — un prefixe qui laisse la place a une eventuelle
 *       version {@code /api/v2} sans tout deplacer.</li>
 *   <li>{@code 201 Created} sur une creation, {@code 204 No Content} sur une
 *       suppression, {@code 200} ailleurs.</li>
 *   <li>Les droits sont declares par {@code @PreAuthorize} au-dessus de la
 *       methode : ils se lisent dans le meme ecran que la route qu'ils
 *       protegent, et non dans un fichier de configuration a part.</li>
 *   <li>Le proprietaire d'une donnee est verifie dans le SERVICE, pas ici.
 *       {@code @PreAuthorize("hasRole('ELEVEUR')")} dit « c'est un eleveur »,
 *       pas « c'est SON lot ». Confondre les deux est la faille la plus
 *       banale des API REST.</li>
 * </ul>
 */
package ml.moncheptel.api.controleur;
