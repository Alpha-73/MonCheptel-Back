package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.Langue;

/**
 * Ce qu'un utilisateur peut changer sur son propre compte.
 *
 * <p>Correspond a l'ecran « Mes informations » et a l'ecran « Langue ». Les
 * deux ecrans ecrivent dans la meme ressource : le profil. Deux routes
 * separees obligeraient a dupliquer le controle d'unicite de l'adresse et les
 * regles de filiere.
 *
 * <h2>Ce qui n'y figure PAS, et pourquoi</h2>
 *
 * <ul>
 *   <li><b>Le mot de passe</b> : il a sa route, avec l'ancien exige.
 *       L'autoriser ici permettrait de le changer sans le connaitre, a partir
 *       d'une session volee.</li>
 *   <li><b>Le role</b> : on ne se promeut pas administrateur.</li>
 *   <li><b>{@code actif}</b> : on ne se reactive pas soi-meme apres une
 *       desactivation par l'administration.</li>
 *   <li><b>Le titre, la specialite, la zone d'intervention</b> du veterinaire :
 *       ils sont poses par l'administration, qui cree le compte. Un
 *       veterinaire qui se declarerait « Professeur » sans controle viderait
 *       l'annuaire de son sens.</li>
 * </ul>
 *
 * <h2>Le risque que porte le changement d'adresse</h2>
 *
 * <p>L'adresse est devenue l'IDENTIFIANT. La changer, c'est changer la serrure.
 * Quelqu'un qui disposerait d'une session ouverte sur un telephone laisse sans
 * surveillance pourrait y mettre la sienne et prendre le compte.
 *
 * <p>La parade habituelle est d'exiger le mot de passe actuel. <b>La maquette
 * n'a pas ce champ</b> sur « Mes informations », et elle fait reference.
 * J'ai donc retenu l'autre parade standard : un courriel part a l'ANCIENNE
 * adresse des que la nouvelle est posee. Le titulaire l'apprend donc
 * immediatement et peut appeler l'administration.
 *
 * <p><b>Ferme le 10 octobre (audit) :</b> le risque residuel — compte perdu
 * entre le changement et la lecture du courriel — est ferme par
 * {@code motDePasseActuel}. Il n'est exige que lorsque l'adresse CHANGE, et
 * seulement sur la route de l'utilisateur lui-meme ({@code PUT /api/auth/moi}).
 * Corriger son nom ou sa zone ne le demande pas ; la route d'administration
 * non plus (l'administrateur ne connait pas ce mot de passe, et l'ancienne
 * boite est prevenue).
 *
 * @param filieres ignore pour un veterinaire ou un administrateur : la notion
 *                 n'existe que pour l'eleveur
 */
public record ProfilRequete(

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 120)
        String nom,

        @NotBlank(message = "L'adresse e-mail est obligatoire")
        @Email(message = "Adresse e-mail invalide")
        @Size(max = 160)
        String email,

        // Facultatif : contact, plus identifiant. Une chaine vide efface.
        @Size(max = 20)
        String telephone,

        @NotNull(message = "La zone est obligatoire")
        Long zoneId,

        @NotNull(message = "La langue est obligatoire")
        Langue langue,

        Set<Filiere> filieres,

        // Exige seulement si l'adresse change (voir plus haut). Jamais
        // journalise, jamais renvoye.
        @Size(max = 100)
        String motDePasseActuel) {
}
