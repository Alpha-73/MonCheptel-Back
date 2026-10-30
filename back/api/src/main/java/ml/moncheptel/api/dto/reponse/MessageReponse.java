package ml.moncheptel.api.dto.reponse;

/**
 * Une phrase destinee a etre affichee telle quelle a l'utilisateur.
 *
 * <h2>Pourquoi un objet et non une chaine nue</h2>
 *
 * <p>Une route qui rend {@code "texte"} impose au client de traiter du JSON qui
 * n'est pas un objet — cas particulier dans toutes les bibliotheques HTTP, et
 * rupture avec les cinquante autres routes de l'API. Un objet a un champ coute
 * huit caracteres de plus et permet d'en ajouter un seul plus tard sans casser
 * les clients deja ecrits.
 *
 * <h2>Pourquoi le serveur redige ce texte</h2>
 *
 * <p>Par exception : d'habitude le serveur rend des donnees et le client choisit
 * les mots, parce que les mots dependent de la langue. Ici le message porte une
 * nuance juridique autant qu'ergonomique — « SI cette adresse correspond a un
 * compte » — et un client qui la reformulerait en « code envoye » annulerait la
 * protection contre l'enumeration des comptes. Le texte reste donc du cote du
 * serveur, qui est le seul a savoir pourquoi il est formule ainsi.
 *
 * <p>La traduction en bambara se fera le jour ou l'API portera les deux langues.
 * En attendant, {@code Langue} est deja sur l'utilisateur.
 */
public record MessageReponse(String message) {
}
