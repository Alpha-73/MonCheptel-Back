package ml.moncheptel.api.dto.reponse;

import java.time.LocalDate;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.Role;

/**
 * Une ligne du tableau « Utilisateurs » de l'administration.
 *
 * <h2>L'e-mail avant le telephone</h2>
 *
 * <p>Les deux sont rendus, dans cet ordre, parce que l'e-mail est desormais
 * l'IDENTIFIANT du compte et le telephone un simple moyen de contact. La
 * maquette affichait une colonne « TÉLÉPHONE » ; elle devient « E-MAIL ».
 *
 * <p>Le telephone reste utile a l'administration : un eleveur qui appelle sait
 * dire son numero, rarement son adresse exacte. La recherche porte donc sur les
 * trois — nom, e-mail, telephone.
 *
 * <h2>Pourquoi {@code verrouille} figure ici</h2>
 *
 * <p>Un compte verrouille apres cinq echecs n'est pas desactive : il redeviendra
 * utilisable dans un quart d'heure. Sans cette distinction a l'ecran,
 * l'administrateur voit « Actif », entend l'eleveur dire qu'il n'arrive pas a
 * entrer, et conclut a une panne. C'est le genre d'ecart qui fait perdre une
 * demi-heure a deux personnes.
 *
 * @param filieres vide pour un veterinaire ou un administrateur : la notion
 *                 n'existe que pour l'eleveur
 */
public record UtilisateurResumeReponse(
        Long id,
        String nom,
        String email,
        String telephone,
        Role role,
        String zone,
        Set<Filiere> filieres,
        LocalDate dateInscription,
        boolean actif,
        boolean verrouille,
        boolean doitChangerMotDePasse) {
}
