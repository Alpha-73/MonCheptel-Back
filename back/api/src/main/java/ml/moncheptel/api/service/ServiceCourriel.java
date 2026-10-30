package ml.moncheptel.api.service;

import ml.moncheptel.api.modele.Utilisateur;
import ml.moncheptel.api.modele.Veterinaire;

/**
 * Envoi de courriel.
 *
 * <p>Une interface pour la meme raison que {@link PasserelleNotification} :
 * les tests n'ont pas de serveur SMTP, et creer un veterinaire dans un test ne
 * doit pas dependre d'un reseau.
 */
public interface ServiceCourriel {

    /**
     * Transmet au veterinaire son mot de passe provisoire.
     *
     * <p>Ce mot de passe ne doit etre journalise nulle part — ni dans les logs
     * applicatifs, ni dans une trace d'erreur. Il ne transite qu'ici.
     */
    void envoyerMotDePasseProvisoire(Veterinaire veterinaire, String motDePasseProvisoire);

    /**
     * Transmet le code a six chiffres qui autorise un changement de mot de
     * passe sans connaitre l'ancien.
     *
     * <p>Comme le mot de passe provisoire : ce code ne doit apparaitre dans
     * aucun journal. Il ne transite qu'ici.
     *
     * <p>{@code validiteMinutes} est passe en parametre plutot que code en dur
     * dans le texte du message : la duree est un reglage
     * ({@code moncheptel.securite}), et un courriel qui annonce quinze minutes
     * quand le serveur en accorde cinq fait accuser l'application a tort.
     *
     * @param utilisateur    le destinataire ; son e-mail EST son identifiant
     * @param code           le code en clair, tel qu'il doit etre recopie
     * @param validiteMinutes duree de validite reellement appliquee
     */
    void envoyerCodeReinitialisation(Utilisateur utilisateur, String code,
                                     int validiteMinutes);

    /**
     * Informe un utilisateur que l'administration a remis son mot de passe a
     * zero, et lui transmet le mot de passe provisoire.
     *
     * <p>Distinct de {@link #envoyerMotDePasseProvisoire} : le texte n'annonce
     * pas la creation d'un compte, mais une remise a zero demandee. Un
     * utilisateur qui recoit « un compte a ete cree pour vous » alors qu'il en
     * a un depuis six mois croit a une tentative d'hameconnage.
     */
    void envoyerMotDePasseReinitialiseParAdmin(Utilisateur utilisateur,
                                               String motDePasseProvisoire);

    /**
     * Previent a l'ANCIENNE adresse qu'elle n'ouvre plus le compte.
     *
     * <p>C'est le seul message de cette interface qui ne part pas a l'adresse
     * courante du compte, et c'est tout son interet : si le changement n'a pas
     * ete fait par le titulaire, la nouvelle adresse est celle de quelqu'un
     * d'autre. Lui envoyer l'avertissement reviendrait a prevenir l'intrus.
     *
     * @param utilisateur    le compte, qui porte deja la NOUVELLE adresse
     * @param ancienneAdresse celle a laquelle ce message doit partir
     */
    void avertirChangementAdresse(Utilisateur utilisateur, String ancienneAdresse);

    /**
     * Faux quand aucun moyen d'envoi n'est configure (ni SMTP, ni profil
     * dev) : les operations qui reposent sur un courriel refusent alors en
     * 422 au lieu d'echouer plus loin (audit global G14).
     */
    default boolean estConfigure() {
        return true;
    }
}
