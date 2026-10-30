package ml.moncheptel.api.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import ml.moncheptel.api.modele.CodeReinitialisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CodeReinitialisationRepository
        extends JpaRepository<CodeReinitialisation, Long> {

    /**
     * Le code presente, retrouve par son empreinte ET son proprietaire.
     *
     * <p>Les deux conditions, pas seulement l'empreinte : sans le
     * rapprochement avec l'utilisateur, un code valide pour le compte A
     * ouvrirait le compte B, puisque l'appelant choisit l'adresse a laquelle
     * il l'applique. Six chiffres se devinent — un code valide en circulation
     * ne doit servir que pour le compte auquel il a ete envoye.
     */
    /**
     * Le dernier code encore ouvert pour ce compte, s'il en reste un.
     *
     * <h3>Pourquoi on ne cherche PAS par empreinte</h3>
     *
     * <p>La version precedente de cette methode prenait l'empreinte du code
     * saisi et cherchait la ligne correspondante. Un code faux ne correspondait
     * alors a aucune ligne — donc <b>aucun compteur de tentatives ne montait</b>.
     * Le plafond de cinq essais ne protegeait que contre la reutilisation d'un
     * code deja connu, et pas du tout contre ce qu'il doit arreter : une boucle
     * qui essaie les codes a six chiffres les uns apres les autres.
     *
     * <p>On recupere donc la ligne par le COMPTE, puis on compare les
     * empreintes en memoire. Un echec incremente alors la ligne qui existe, et
     * le cinquieme grille le code.
     *
     * <p>{@code order by c.id desc} et pas {@code dateCreation} : deux demandes
     * faites dans la meme milliseconde porteraient la meme date. L'identifiant
     * auto-incremente, lui, ordonne toujours.
     *
     * <h3>Pourquoi une liste et non un {@code Optional}</h3>
     *
     * <p>Parce qu'un {@code Optional} leve {@code NonUniqueResultException} des
     * qu'il y a deux lignes. {@link #invaliderTous} garantit normalement qu'il
     * n'y en a qu'une, mais deux demandes simultanees sur le meme compte
     * peuvent en creer deux : la plateforme planterait alors sur un cas
     * parfaitement banal — un bouton presse deux fois sur un reseau lent.
     *
     * <p>Appeler cette methode via {@link #dernierActif} : elle prend la
     * premiere ligne et ignore le reste.
     */
    @Query("""
           select c from CodeReinitialisation c
           join fetch c.utilisateur
           where c.utilisateur.id = :utilisateurId
             and c.utilise = false
           order by c.id desc
           """)
    List<CodeReinitialisation> chercherActifs(@Param("utilisateurId") Long utilisateurId);

    /** Le plus recent des codes ouverts, ou rien. */
    default Optional<CodeReinitialisation> dernierActif(Long utilisateurId) {
        List<CodeReinitialisation> actifs = chercherActifs(utilisateurId);
        return actifs.isEmpty() ? Optional.empty() : Optional.of(actifs.get(0));
    }

    /**
     * Invalide les codes encore vivants d'un utilisateur.
     *
     * <p>Appele avant d'en emettre un nouveau, et apres un changement de mot
     * de passe. Sans cela, un eleveur qui demande deux fois un code — ce qui
     * arrive des que le premier courriel tarde — laisserait deux codes
     * valables, dont un qu'il a peut-etre lu a voix haute.
     */
    @Modifying
    @Query("""
           update CodeReinitialisation c set c.utilise = true
           where c.utilisateur.id = :utilisateurId and c.utilise = false
           """)
    int invaliderTous(@Param("utilisateurId") Long utilisateurId);

    /** Menage : les codes expires ne servent plus a rien. */
    @Modifying
    @Query("delete from CodeReinitialisation c where c.expireLe < :limite")
    int supprimerExpires(@Param("limite") Instant limite);

    /**
     * Combien de codes cet utilisateur a demandes depuis {@code depuis}.
     *
     * <p>Plafonne les demandes : sans ce compteur, une boucle sur la route
     * publique remplirait la boite de reception d'un eleveur — et ferait
     * classer nos courriels en indesirables, ce qui casserait la
     * fonctionnalite pour tout le monde.
     */
    @Query("""
           select count(c.id) from CodeReinitialisation c
           where c.utilisateur.id = :utilisateurId and c.dateCreation >= :depuis
           """)
    long compterDemandesDepuis(@Param("utilisateurId") Long utilisateurId,
                               @Param("depuis") Instant depuis);
}
