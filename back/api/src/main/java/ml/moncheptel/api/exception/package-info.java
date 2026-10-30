/**
 * Exceptions metier de l'application.
 *
 * <p>Quatre seulement, et chacune correspond a un code HTTP. C'est
 * volontairement peu : une exception par cas d'erreur produirait quarante
 * classes vides et un gestionnaire illisible. Le message porte le detail, la
 * classe porte le code.
 *
 * <table border="1">
 *   <caption>Correspondance avec les codes HTTP</caption>
 *   <tr><th>Exception</th><th>Code</th><th>Quand</th></tr>
 *   <tr><td>{@link ml.moncheptel.api.exception.RessourceIntrouvableException}</td>
 *       <td>404</td><td>L'identifiant ne designe rien</td></tr>
 *   <tr><td>{@link ml.moncheptel.api.exception.RegleMetierException}</td>
 *       <td>422</td><td>La demande est bien formee mais interdite</td></tr>
 *   <tr><td>{@link ml.moncheptel.api.exception.ConflitException}</td>
 *       <td>409</td><td>Un doublon, ou un etat deja atteint</td></tr>
 *   <tr><td>{@link ml.moncheptel.api.exception.AccesRefuseException}</td>
 *       <td>403</td><td>Authentifie, mais pas sur ses propres donnees</td></tr>
 * </table>
 *
 * <p><b>Le message de ces exceptions est affiche a l'utilisateur.</b> Il se
 * redige donc en francais lisible — « L'unite LOT-014 ne compte que 180
 * tete(s) » — et ne contient jamais de nom de classe, de table ni de trace.
 * C'est ce contrat qui permet au gestionnaire global de recopier le message
 * tel quel dans la reponse.
 */
package ml.moncheptel.api.exception;
