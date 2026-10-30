package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.TypeMouvement;

/**
 * Entree ou sortie de tetes du cheptel.
 *
 * <p>C'est le journal que l'ecran « Historique - LOT-014 » affiche :
 * <pre>
 *   Lot cree    3 sept. 2026   185 tetes mises en place
 *   Mort        8 sept. 2026     5 tetes — il reste 180
 * </pre>
 *
 * <p>Ces lignes sont <b>immuables</b> : on ne corrige pas une mort, on
 * enregistre un mouvement inverse. C'est ce qui rend l'effectif auditable et
 * ce qui permet de repondre au jury quand il demande d'ou viennent les 180.
 *
 * <p>Separe de {@link EvenementSanitaire} malgre la liste deroulante unique de
 * la maquette : les deux formulaires n'ont pas les memes champs — une sortie
 * saisit un nombre de tetes, une vaccination saisit un produit — et seule la
 * sortie touche a l'effectif. L'ecran les reunit dans un carnet, un DTO s'en
 * charge.
 */
@Entity
@Table(name = "mouvement_cheptel")
public class MouvementCheptel extends EntiteBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unite_id", nullable = false)
    private UniteElevage unite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TypeMouvement type;

    @Column(name = "date_mouvement", nullable = false)
    private LocalDate dateMouvement;

    @Positive
    @Column(name = "nombre_tetes", nullable = false)
    private int nombreTetes;

    /** « Ex : vendu au marche de Koutiala ». */
    @Size(max = 500)
    @Column(length = 500)
    private String observation;

    /**
     * Montant encaisse, pour une vente. Nul sinon.
     * Sert a creer l'operation de revenu correspondante : une saisie, deux
     * effets, et la rentabilite par lot devient juste sans double saisie.
     */
    @PositiveOrZero
    @Column
    private Long montant;

    /** Qui a saisi. Alimente le journal d'activite de la fiche utilisateur. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id")
    private Utilisateur auteur;

    protected MouvementCheptel() {
    }

    public MouvementCheptel(UniteElevage unite, TypeMouvement type, LocalDate dateMouvement,
                            int nombreTetes, String observation, Long montant, Utilisateur auteur) {
        this.unite = unite;
        this.type = type;
        this.dateMouvement = dateMouvement;
        this.nombreTetes = nombreTetes;
        this.observation = observation;
        this.montant = montant;
        this.auteur = auteur;
    }

    /** Mouvement d'entree engendre par la creation d'un lot ou d'un animal. */
    public static MouvementCheptel entree(UniteElevage unite, TypeMouvement type,
                                          int nombreTetes, Utilisateur auteur) {
        if (type.estSortie()) {
            throw new IllegalArgumentException("Type de sortie fourni a entree() : " + type);
        }
        return new MouvementCheptel(unite, type, unite.getDateEntree(),
                nombreTetes, null, null, auteur);
    }

    public boolean estSortie() {
        return type.estSortie();
    }

    /** Effet signe sur l'effectif : negatif pour une sortie. */
    public int effetSurEffectif() {
        return estSortie() ? -nombreTetes : nombreTetes;
    }

    public UniteElevage getUnite() { return unite; }

    public TypeMouvement getType() { return type; }

    public LocalDate getDateMouvement() { return dateMouvement; }

    /**
     * Seul usage : le mouvement d'ENTREE suit la date d'entree corrigee de
     * son unite (audit F12). Une sortie ne se re-date pas, elle s'annule.
     */
    public void setDateMouvement(LocalDate date) {
        if (estSortie()) {
            throw new IllegalStateException("Une sortie ne se re-date pas : annulez-la et ressaisissez-la.");
        }
        this.dateMouvement = date;
    }

    public int getNombreTetes() { return nombreTetes; }

    public String getObservation() { return observation; }

    public Long getMontant() { return montant; }

    public Utilisateur getAuteur() { return auteur; }
}
