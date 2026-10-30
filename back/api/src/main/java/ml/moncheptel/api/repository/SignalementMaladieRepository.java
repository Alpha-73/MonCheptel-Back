package ml.moncheptel.api.repository;

import java.time.Instant;
import java.util.List;
import ml.moncheptel.api.modele.SignalementMaladie;
import ml.moncheptel.api.modele.enumeration.StatutSignalement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Signalements de maladie.
 *
 * <h2>Pourquoi le tri par gravite s'ecrit en {@code case when}</h2>
 *
 * <p>{@code gravite} est persistee en clair ({@code EnumType.STRING}) : la
 * colonne contient FAIBLE, MOYEN, ELEVE. Un {@code order by s.gravite desc}
 * trie donc ces chaines par ordre alphabetique inverse et rend MOYEN, FAIBLE,
 * ELEVE — les cas les plus graves arrivent EN DERNIER dans la file du
 * veterinaire, sans aucun message d'erreur. Le {@code case when} retablit
 * l'ordre metier, celui de {@code NiveauGravite.getNiveau()}.
 */
@Repository
public interface SignalementMaladieRepository extends JpaRepository<SignalementMaladie, Long> {

    /** Historique de l'eleveur — « Mes signalements ». */
    List<SignalementMaladie> findByEleveurIdOrderByDateSignalementDesc(Long eleveurId);

    /**
     * File d'attente du veterinaire — ecran « Veto - Signalements ».
     *
     * <p>Les nouveaux d'abord, les traites ensuite : un veterinaire ouvre
     * cet ecran pour savoir ce qui l'attend, pas pour relire ce qu'il a fait.
     */
    @Query("""
           select s from SignalementMaladie s
           join fetch s.eleveur e
           left join fetch s.maladieProbable
           where s.veterinaire.id = :veterinaireId
             and (:statut is null or s.statut = :statut)
           order by case when s.statut = ml.moncheptel.api.modele.enumeration.StatutSignalement.ORIENTE
                         then 0 else 1 end asc,
                    case when s.gravite = ml.moncheptel.api.modele.enumeration.NiveauGravite.ELEVE then 3
                         when s.gravite = ml.moncheptel.api.modele.enumeration.NiveauGravite.MOYEN then 2
                         else 1 end desc,
                    s.dateSignalement asc
           """)
    Page<SignalementMaladie> chercherPourVeterinaire(@Param("veterinaireId") Long veterinaireId,
                                                     @Param("statut") StatutSignalement statut,
                                                     Pageable pageable);

    /**
     * Comptage epidemiologique : combien de signalements de cette maladie,
     * dans cette zone, depuis {@code depuis}.
     *
     * <p>C'est ce nombre que l'administration regarde avant de declencher une
     * alerte « Newcastle — Koutiala ». Le seuil, lui, n'est pas ici : c'est
     * une decision, pas une requete.
     *
     * <p>La zone lue est celle de l'ELEVEUR, pas celle de l'unite : une unite
     * n'a pas de zone propre, et un eleveur declare ou il se trouve.
     */
    @Query("""
           select count(s.id) from SignalementMaladie s
           where s.maladieProbable.id = :maladieId
             and s.eleveur.zone.id = :zoneId
             and s.dateSignalement >= :depuis
           """)
    long compterDansZone(@Param("maladieId") Long maladieId,
                         @Param("zoneId") Long zoneId,
                         @Param("depuis") Instant depuis);

    /** Signalements non pris en charge — supervision de l'administration. */
    @Query("""
           select s from SignalementMaladie s
           join fetch s.eleveur
           where s.statut = ml.moncheptel.api.modele.enumeration.StatutSignalement.NOUVEAU
             and s.dateSignalement < :limite
           order by case when s.gravite = ml.moncheptel.api.modele.enumeration.NiveauGravite.ELEVE then 3
                         when s.gravite = ml.moncheptel.api.modele.enumeration.NiveauGravite.MOYEN then 2
                         else 1 end desc, s.dateSignalement asc
           """)
    List<SignalementMaladie> chercherSansPriseEnCharge(@Param("limite") Instant limite);

    /**
     * Les cas qui peuvent appeler une relance (V14) : confies sans reponse,
     * ou urgents restes sans veterinaire, et pas encore relances cote
     * eleveur. Le delai, lui, est verifie par OrientationService.relancer.
     */
    @Query("""
           select s from SignalementMaladie s
           left join s.maladieProbable m
           where s.dateRelanceEleveur is null
             and (s.statut = ml.moncheptel.api.modele.enumeration.StatutSignalement.ORIENTE
                  or (s.statut = ml.moncheptel.api.modele.enumeration.StatutSignalement.NOUVEAU
                      and (s.gravite = ml.moncheptel.api.modele.enumeration.NiveauGravite.ELEVE
                           or m.orienteVersVeterinaire = true)))
           """)
    List<SignalementMaladie> chercherARelancer();

    long countByVeterinaireIdAndStatut(Long veterinaireId, StatutSignalement statut);

    List<SignalementMaladie> findByVeterinaireIdAndStatutIn(Long veterinaireId,
                                                            java.util.Collection<StatutSignalement> statuts);

    /** Accueil veterinaire : comptages en base au lieu de charger la file. */
    long countByVeterinaireIdAndStatutIn(Long veterinaireId,
                                         java.util.Collection<StatutSignalement> statuts);

    long countByVeterinaireIdAndStatutInAndGravite(
            Long veterinaireId, java.util.Collection<StatutSignalement> statuts,
            ml.moncheptel.api.modele.enumeration.NiveauGravite gravite);

    long countByVeterinaireIdAndStatutAndDateTraitementGreaterThanEqual(
            Long veterinaireId, StatutSignalement statut, Instant depuis);

    /**
     * Nombre de cas par veterinaire dans ces statuts, en UNE requete :
     * lignes {@code [veterinaireId, nombre]}. Sert a l'orientation pour
     * repartir la charge (audit veterinaire M11).
     */
    @Query("""
           select s.veterinaire.id, count(s) from SignalementMaladie s
           where s.veterinaire.id in :ids and s.statut in :statuts
           group by s.veterinaire.id
           """)
    List<Object[]> compterParVeterinaire(@Param("ids") java.util.Collection<Long> ids,
                                         @Param("statuts") java.util.Collection<StatutSignalement> statuts);

    /**
     * Les cas qui DEVRAIENT etre suivis par un veterinaire et ne le sont pas :
     * gravite elevee ou maladie qui oriente, restes NOUVEAU faute de
     * veterinaire. Le tableau de bord de l'administration les compte.
     */
    @Query("""
           select s from SignalementMaladie s
           left join s.maladieProbable m
           where s.statut = ml.moncheptel.api.modele.enumeration.StatutSignalement.NOUVEAU
             and (s.gravite = ml.moncheptel.api.modele.enumeration.NiveauGravite.ELEVE
                  or m.orienteVersVeterinaire = true)
           order by s.dateSignalement asc
           """)
    List<SignalementMaladie> chercherSansVeterinaire();

    /** Meme filtre que {@link #chercherSansVeterinaire()}, compte en base (audit global G17). */
    @Query("""
           select count(s) from SignalementMaladie s
           left join s.maladieProbable m
           where s.statut = ml.moncheptel.api.modele.enumeration.StatutSignalement.NOUVEAU
             and (s.gravite = ml.moncheptel.api.modele.enumeration.NiveauGravite.ELEVE
                  or m.orienteVersVeterinaire = true)
           """)
    long compterSansVeterinaire();

    /**
     * Les derniers signalements de la plateforme, tous eleveurs confondus.
     *
     * <p>Alimente le tableau « Derniers signalements » de l'administration.
     * Les jointures sont explicites : l'ecran affiche le nom de l'eleveur, sa
     * zone et la maladie, et {@code open-in-view} est a faux — un chargement
     * paresseux depuis le mappage leverait une exception.
     *
     * <p>{@code Pageable} plutot qu'un {@code limit} : c'est le mecanisme
     * standard de Spring Data, et il traduit en LIMIT cote base. Le
     * {@code join fetch} ne pose pas de probleme de pagination en memoire ici
     * — ce sont des associations vers-un, pas des collections.
     */
    @Query("""
           select s from SignalementMaladie s
           join fetch s.eleveur e
           left join fetch e.zone
           left join fetch s.maladieProbable
           order by s.dateSignalement desc
           """)
    List<SignalementMaladie> derniers(Pageable pageable);

    /**
     * Ce veterinaire a-t-il ete oriente sur un signalement de cette unite ?
     *
     * <p>Sert au controle d'ecriture du carnet sanitaire : c'est l'un des deux
     * liens qui autorisent un veterinaire a inscrire un acte chez un eleveur
     * qui n'est pas lui.
     */
    boolean existsByUniteIdAndVeterinaireId(Long uniteId, Long veterinaireId);

    /**
     * Ce veterinaire a-t-il un cas OUVERT (statut donne) sur cette unite ?
     * Seul lien qui l'autorise a ecrire au carnet (audit global G3).
     */
    boolean existsByUniteIdAndVeterinaireIdAndStatutIn(Long uniteId, Long veterinaireId,
                                                       java.util.Collection<StatutSignalement> statuts);

    /**
     * Ecran « Signalements » de l'administration. {@code aAttribuer} ne garde
     * que les cas qui devraient etre suivis par un veterinaire (gravite
     * elevee ou maladie qui oriente) ; {@code tous} ignore les statuts.
     */
    @Query(value = """
           select s from SignalementMaladie s
           join fetch s.eleveur e
           left join fetch e.zone
           join fetch s.unite
           left join fetch s.maladieProbable m
           left join fetch s.maladieConfirmee
           left join fetch s.veterinaire
           where (:tous = true or s.statut in :statuts)
             and (:aAttribuer = false
                  or s.gravite = ml.moncheptel.api.modele.enumeration.NiveauGravite.ELEVE
                  or m.orienteVersVeterinaire = true)
           order by s.dateSignalement desc
           """,
           countQuery = """
           select count(s) from SignalementMaladie s
           left join s.maladieProbable m
           where (:tous = true or s.statut in :statuts)
             and (:aAttribuer = false
                  or s.gravite = ml.moncheptel.api.modele.enumeration.NiveauGravite.ELEVE
                  or m.orienteVersVeterinaire = true)
           """)
    Page<SignalementMaladie> chercherPourAdministration(
            @Param("tous") boolean tous,
            @Param("statuts") java.util.Collection<StatutSignalement> statuts,
            @Param("aAttribuer") boolean aAttribuer,
            Pageable pageable);

    /**
     * Cas ouverts par veterinaire, en une requete : [veterinaireId, nombre].
     * Sert a la fenetre « Attribuer » de l'administration.
     */
    @Query("""
           select s.veterinaire.id, count(s) from SignalementMaladie s
           where s.veterinaire is not null and s.statut in :statuts
           group by s.veterinaire.id
           """)
    List<Object[]> compterOuvertsParVeterinaire(
            @Param("statuts") java.util.Collection<StatutSignalement> statuts);
}
