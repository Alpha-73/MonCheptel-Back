package ml.moncheptel.api.service;

import ml.moncheptel.api.modele.CodeReinitialisation;
import ml.moncheptel.api.repository.CodeReinitialisationRepository;
import ml.moncheptel.api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enregistre les tentatives ratees — dans une transaction A PART.
 *
 * <h2>LE DEFAUT QUE CETTE CLASSE CORRIGE</h2>
 *
 * <p>Le code etait ecrit ainsi, et il etait faux :
 *
 * <pre>
 *   &#64;Transactional
 *   public JetonReponse connecter(...) {
 *       if (!encodeur.matches(...)) {
 *           utilisateur.signalerEchecConnexion(seuil, duree);  // on incremente
 *           throw identifiantsInvalides();                     // puis on leve
 *       }
 *   }
 * </pre>
 *
 * <p>{@code RegleMetierException} herite de {@code RuntimeException}, et Spring
 * annule la transaction sur toute exception non verifiee. <b>L'increment etait
 * donc systematiquement annule avec elle.</b> {@code tentatives_echouees}
 * restait a zero pour l'eternite, {@code estVerrouille()} etait toujours faux,
 * et le verrouillage apres cinq essais — exige par le cahier des charges —
 * n'existait pas. La route de connexion acceptait un nombre illimite de
 * tentatives de mot de passe.
 *
 * <p>Le meme defaut, au meme endroit, rendait le code de reinitialisation a six
 * chiffres <b>forcable</b> : son plafond de cinq essais ne montait jamais. Un
 * million d'appels sur deux routes publiques suffisait a prendre un compte sans
 * jamais acceder a sa boite.
 *
 * <p>Rien n'echouait, rien ne se compilait mal, et les commentaires decrivaient
 * une protection qui ne s'executait pas.
 *
 * <h2>Pourquoi une classe separee, et non une methode privee</h2>
 *
 * <p>{@code REQUIRES_NEW} ouvre une transaction independante, qui valide meme
 * si l'appelante est annulee. Mais Spring l'applique par un PROXY : un appel
 * {@code this.methode()} a l'interieur du meme objet ne passe pas par le proxy
 * et l'annotation serait sans effet — un piege classique, et exactement celui
 * qui ferait croire le probleme regle alors qu'il ne le serait pas.
 *
 * <p>D'ou un bean distinct, injecte. L'appel traverse alors le proxy.
 *
 * <h2>Ce qui reste imparfait, et que j'assume</h2>
 *
 * <p>On relit l'entite puis on l'incremente. Deux echecs <b>simultanes</b> sur
 * le meme compte peuvent lire la meme valeur et ecrire la meme : un essai
 * gratuit. Un {@code UPDATE ... SET tentatives = tentatives + 1} atomique le
 * fermerait completement.
 *
 * <p>Je ne l'ai pas fait parce que la regle de verrouillage vit dans
 * {@link ml.moncheptel.api.modele.Utilisateur#signalerEchecConnexion} — seuil
 * et duree ensemble — et qu'une requete UPDATE la dupliquerait en SQL. Deux
 * formulations du meme invariant finissent toujours par diverger. Le gain d'un
 * essai pour un attaquant qui sait paralleliser ne vaut pas cette dette.
 */
@Service
public class CompteurEchecs {

    private final UtilisateurRepository utilisateurRepository;
    private final CodeReinitialisationRepository codeRepository;

    public CompteurEchecs(UtilisateurRepository utilisateurRepository,
                          CodeReinitialisationRepository codeRepository) {
        this.utilisateurRepository = utilisateurRepository;
        this.codeRepository = codeRepository;
    }

    /**
     * Un mot de passe faux de plus sur ce compte.
     *
     * <p>{@code ifPresent} et non {@code orElseThrow} : si le compte a disparu
     * entre-temps, il n'y a rien a compter. Lever ici masquerait l'exception
     * d'authentification que l'appelant s'apprete a lever, et le client
     * recevrait 404 au lieu de « mot de passe incorrect ».
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrerEchecConnexion(Long utilisateurId, int seuil, int dureeMinutes) {
        utilisateurRepository.findById(utilisateurId)
                .ifPresent(u -> u.signalerEchecConnexion(seuil, dureeMinutes));
    }

    /** Un code de reinitialisation faux de plus. Au cinquieme, il est grille. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrerEchecCode(Long codeId) {
        codeRepository.findById(codeId).ifPresent(CodeReinitialisation::signalerEchec);
    }
}
