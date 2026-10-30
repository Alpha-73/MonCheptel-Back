/**
 * Traduction entre les entites et les objets de transport.
 *
 * <h2>Pourquoi ces mappers sont ecrits a la main</h2>
 *
 * <p>MapStruct ferait le meme travail en moins de lignes. Il a ete ecarte pour
 * trois raisons concretes :
 *
 * <ol>
 *   <li>C'est un processeur d'annotations. Le code reellement execute est
 *       genere a la compilation et ne figure pas dans le depot : a la
 *       soutenance, personne ne peut montrer la ligne qui masque le
 *       traitement d'une maladie.</li>
 *   <li>Les regles ci-dessous ne sont pas des copies de champ. Masquer le
 *       traitement d'une maladie orientee, aplatir un {@code Lot} et un
 *       {@code Animal} dans une seule reponse, calculer des jours restants :
 *       ecrites en configuration MapStruct, elles seraient plus longues et
 *       moins lisibles qu'en Java.</li>
 *   <li>Une dependance de moins a faire fonctionner dans un delai court.</li>
 * </ol>
 *
 * <p>Chaque mapper est un composant Spring, injecte dans les services. Chaque
 * methode tolere l'entree {@code null} et rend {@code null} : un lot sans
 * veterinaire, une operation sans unite, un signalement sans diagnostic
 * existent tous, et les tester chez l'appelant remplirait les services de
 * conditions.
 */
package ml.moncheptel.api.mapper;
