package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.CategorieOperation;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.TypeOperation;

/**
 * Depense ou revenu d'un eleveur.
 *
 * <p>Deux choix de conception, tous deux imposes par la maquette :
 *
 * <p><b>La filiere est obligatoire, l'unite ne l'est pas.</b> L'ecran
 * « Operation » impose « Filiere concernee » et laisse « Lot ou animal
 * concerne » facultatif, avec l'option « Non rattache — depense partagee ».
 * Le sac d'aliment achete en gros et la main-d'oeuvre ne se rattachent a aucun
 * lot ; forcer un rattachement fausserait la rentabilite par lot dans les deux
 * sens. Sans filiere en revanche, aucun rapport par filiere n'est possible.
 *
 * <p><b>Le montant est toujours positif.</b> Le sens vient du type, jamais du
 * signe : un montant negatif en base serait une erreur de saisie, pas une
 * depense. C'est {@link TypeOperation#getSigne()} qui fait le calcul.
 */
@Entity
@Table(name = "operation_financiere")
public class OperationFinanciere extends EntiteBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eleveur_id", nullable = false)
    private Eleveur eleveur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private TypeOperation type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CategorieOperation categorie;

    /** Obligatoire : sans elle, aucun rapport par filiere. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Filiere filiere;

    /** Facultatif : nul = depense partagee, cas prevu par la maquette. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unite_id")
    private UniteElevage unite;

    @Positive
    @Column(nullable = false)
    private long montant;

    @Column(name = "date_operation", nullable = false)
    private LocalDate dateOperation;

    @Size(max = 500)
    @Column(length = 500)
    private String note;

    /**
     * Mouvement qui a engendre cette operation, s'il y en a un.
     * Une vente saisie une fois cree le mouvement ET le revenu : sans ce lien,
     * un eleveur consciencieux saisirait les deux et compterait double.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mouvement_id", unique = true)
    private MouvementCheptel mouvement;

    protected OperationFinanciere() {
    }

    public OperationFinanciere(Eleveur eleveur, TypeOperation type, CategorieOperation categorie,
                               Filiere filiere, UniteElevage unite, long montant,
                               LocalDate dateOperation, String note) {
        if (categorie.getType() != type) {
            throw new IllegalArgumentException(
                    "La catégorie « " + categorie.getLibelle() + " » ne va pas avec « "
                    + type.getLibelle() + " ».");
        }
        this.eleveur = eleveur;
        this.type = type;
        this.categorie = categorie;
        this.filiere = filiere;
        this.unite = unite;
        this.montant = montant;
        this.dateOperation = dateOperation;
        this.note = note;
    }

    /**
     * Revenu engendre par la vente d'une unite. Le lien avec le mouvement
     * empeche le double comptage.
     */
    public static OperationFinanciere revenuDeVente(MouvementCheptel vente, String note) {
        UniteElevage unite = vente.getUnite();
        OperationFinanciere op = new OperationFinanciere(
                unite.getProprietaire(),
                TypeOperation.REVENU,
                CategorieOperation.VENTE_ANIMAUX,
                unite.getFiliere(),
                unite,
                vente.getMontant(),
                vente.getDateMouvement(),
                note);
        op.mouvement = vente;
        return op;
    }

    /** Montant signe : negatif pour une depense. C'est ce qui fait les soldes. */
    public long montantSigne() {
        return (long) type.getSigne() * montant;
    }

    /** Vrai pour une depense partagee, non imputable a un lot. */
    public boolean estPartagee() {
        return unite == null;
    }

    public Eleveur getEleveur() { return eleveur; }

    public TypeOperation getType() { return type; }

    public CategorieOperation getCategorie() { return categorie; }

    /**
     * Change le sens et la categorie ENSEMBLE.
     *
     * <h3>Pourquoi pas deux accesseurs separes</h3>
     *
     * <p>Le constructeur garantit que {@code categorie.getType() == type} —
     * « Vente d'animaux » ne se classe pas en depense. Un
     * {@code setCategorie} seul, qui existait ici, permettait de casser cet
     * invariant sans rien lever : il suffisait de poser VENTE_ANIMAUX sur une
     * DEPENSE. Le solde du mois comptait alors une vente en negatif.
     *
     * <p>Rien ne l'appelait encore, l'ecran « Modifier l'operation » n'ayant
     * pas de route. Il allait en avoir une — d'ou cette correction avant,
     * plutot qu'un bogue a chercher apres.
     *
     * <p>La verification est la MEME que celle du constructeur, et c'est
     * voulu : un invariant verifie a deux endroits avec deux formulations
     * finit toujours par diverger.
     */
    public void requalifier(TypeOperation nouveauType, CategorieOperation nouvelleCategorie) {
        if (nouvelleCategorie.getType() != nouveauType) {
            throw new IllegalArgumentException(
                    "La catégorie « " + nouvelleCategorie.getLibelle()
                    + " » ne va pas avec « " + nouveauType.getLibelle() + " ».");
        }
        this.type = nouveauType;
        this.categorie = nouvelleCategorie;
    }

    public Filiere getFiliere() { return filiere; }
    public void setFiliere(Filiere f) { this.filiere = f; }

    public UniteElevage getUnite() { return unite; }
    public void setUnite(UniteElevage u) { this.unite = u; }

    public long getMontant() { return montant; }
    public void setMontant(long m) { this.montant = m; }

    public LocalDate getDateOperation() { return dateOperation; }
    public void setDateOperation(LocalDate d) { this.dateOperation = d; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public MouvementCheptel getMouvement() { return mouvement; }
}
