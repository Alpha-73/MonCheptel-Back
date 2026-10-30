package ml.moncheptel.api.repository;

import java.util.List;
import ml.moncheptel.api.modele.Eleveur;
import ml.moncheptel.api.modele.enumeration.Filiere;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EleveurRepository extends JpaRepository<Eleveur, Long> {

    /**
     * Cible d'une alerte sanitaire : les eleveurs actifs d'une zone qui
     * pratiquent une filiere TOUCHEE PAR LA MALADIE.
     *
     * <p>C'est cette requete qui produit le « 318 eleveurs de volaille a
     * Koutiala » affiche AVANT confirmation sur l'ecran de declenchement.
     *
     * <p>Une filiere nulle (« toutes ses filieres ») ne vise PAS toute la zone :
     * seulement les eleveurs dont les filieres croisent celles de la maladie.
     * Une alerte PPR (ovins, caprins) ne previent pas un eleveur de volaille ;
     * une notification envoyee ne se rappelle pas. Une filiere donnee qui
     * n'est pas une filiere de la maladie ne cible personne (le service la
     * refuse d'ailleurs en amont).
     */
    @Query("""
           select e from Eleveur e
           where e.actif = true
             and e.zone.id = :zoneId
             and exists (select 1 from Maladie m join m.filieres mf
                         where m.id = :maladieId
                           and mf member of e.filieresPratiquees
                           and (:filiere is null or mf = :filiere))
           """)
    List<Eleveur> chercherCible(@Param("zoneId") Long zoneId,
                                @Param("maladieId") Long maladieId,
                                @Param("filiere") Filiere filiere);

    /**
     * Meme criteres, mais on ne compte que. Sert a l'apercu en direct :
     * l'administrateur voit la portee changer quand il modifie la zone ou la
     * filiere, sans qu'on charge 318 objets pour afficher un nombre.
     */
    @Query("""
           select count(e) from Eleveur e
           where e.actif = true
             and e.zone.id = :zoneId
             and exists (select 1 from Maladie m join m.filieres mf
                         where m.id = :maladieId
                           and mf member of e.filieresPratiquees
                           and (:filiere is null or mf = :filiere))
           """)
    long compterCible(@Param("zoneId") Long zoneId,
                      @Param("maladieId") Long maladieId,
                      @Param("filiere") Filiere filiere);

    /**
     * Le tableau « Utilisateurs » de l'administration.
     *
     * <p>La maquette intitule cet ecran « Éleveurs inscrits sur la plateforme »
     * et propose trois filtres — Filière, Zone, Statut — plus une recherche
     * libre. Cette requete est exactement cela, pas une liste de tous les
     * roles : les veterinaires ont leur propre ecran.
     *
     * <h3>Les trois champs de la recherche libre</h3>
     *
     * <p>Nom, e-mail ET telephone. L'e-mail est devenu l'identifiant, mais un
     * eleveur qui appelle le support sait reciter son numero, pas toujours son
     * adresse exacte. Chercher sur le seul identifiant rendrait l'assistance
     * impossible precisement quand elle est demandee.
     *
     * <h3>Pourquoi des criteres optionnels dans une seule requete</h3>
     *
     * <p>Quatre criteres facultatifs font seize combinaisons. Seize methodes
     * derivees seraient seize occasions de divergence ; la regle est ecrite une
     * fois. Le cout est un plan d'execution moins fin, sans consequence a cette
     * echelle.
     *
     * <h3>Pourquoi AUCUN {@code join fetch} malgre la zone affichee</h3>
     *
     * <p>Un {@code join fetch} sur une requete paginee force Hibernate a ramener
     * TOUTES les lignes et a paginer en memoire — il le signale par un
     * avertissement que personne ne lit. Sur mille deux cents comptes, afficher
     * la premiere page en couterait mille deux cents.
     *
     * <p>La zone est donc chargee paresseusement, dans la transaction du
     * service, et {@code default_batch_fetch_size} regroupe ces chargements en
     * une requete. Voir le commentaire dans {@code application.yml}.
     *
     * <h3>{@code lower()} des deux cotes</h3>
     *
     * <p>La collation {@code utf8mb4_unicode_ci} ignore deja la casse. L'ecrire
     * explicitement garantit le meme resultat si la collation change un jour, et
     * rend la requete lisible sans connaitre le schema.
     */
    @Query("""
           select e from Eleveur e
           where (:recherche is null
                  or lower(e.nom)   like lower(concat('%', :recherche, '%'))
                  or lower(e.email) like lower(concat('%', :recherche, '%'))
                  or e.telephone    like concat('%', :recherche, '%'))
             and (:filiere is null or :filiere member of e.filieresPratiquees)
             and (:zoneId  is null or e.zone.id = :zoneId)
             and (:actif   is null or e.actif = :actif)
           order by e.nom asc
           """)
    Page<Eleveur> rechercher(@Param("recherche") String recherche,
                             @Param("filiere") Filiere filiere,
                             @Param("zoneId") Long zoneId,
                             @Param("actif") Boolean actif,
                             Pageable pagination);
}
