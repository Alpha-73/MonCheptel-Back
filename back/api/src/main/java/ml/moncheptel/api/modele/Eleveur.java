package ml.moncheptel.api.modele;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.Role;

/**
 * Eleveur — l'utilisateur principal de la plateforme.
 *
 * <p>Son seul attribut propre suffit a justifier la sous-classe : les
 * <b>filieres pratiquees</b>, saisies a l'inscription
 * (« CreerCompte - Etape 2 »), modifiables ensuite
 * (« Mes informations »). Elles decident quels onglets il voit et quelles
 * alertes le ciblent.
 */
@Entity
@Table(name = "eleveur")
@DiscriminatorValue("ELEVEUR")
@PrimaryKeyJoinColumn(name = "utilisateur_id")
public class Eleveur extends Utilisateur {

    /**
     * {@code EnumSet} plutot qu'une table de filieres : elles sont quatre,
     * fixes, et connues a la compilation.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "eleveur_filiere",
            joinColumns = @JoinColumn(name = "eleveur_id"))
    @Column(name = "filiere", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Set<Filiere> filieresPratiquees = EnumSet.noneOf(Filiere.class);

    protected Eleveur() {
    }

    public Eleveur(String nom, String email, String motDePasse, Zone zone,
                   Set<Filiere> filieres) {
        super(nom, email, motDePasse, zone);
        this.filieresPratiquees = filieres == null || filieres.isEmpty()
                ? EnumSet.noneOf(Filiere.class)
                : EnumSet.copyOf(filieres);
    }

    @Override
    public Role getRole() {
        return Role.ELEVEUR;
    }

    public Set<Filiere> getFilieresPratiquees() {
        return filieresPratiquees;
    }

    public void setFilieresPratiquees(Set<Filiere> filieres) {
        this.filieresPratiquees = filieres == null || filieres.isEmpty()
                ? EnumSet.noneOf(Filiere.class)
                : EnumSet.copyOf(filieres);
    }

    /**
     * Les filieres pratiquees, dans l'ordre de declaration de
     * {@link Filiere} : {@code VOLAILLE, BOVINS, OVINS, CAPRINS}.
     *
     * <h3>Pourquoi cette methode existe</h3>
     *
     * <p>Le champ est initialise avec un {@code EnumSet}, qui itere dans
     * l'ordre de declaration. <b>Mais ce n'est vrai que pour une entite qu'on
     * vient de construire.</b> Des qu'Hibernate charge l'eleveur depuis la
     * base, il remplace la collection par sa propre implementation, adossee a
     * un {@code HashSet}. {@code getFilieresPratiquees()} rend alors un
     * ensemble sans ordre utile.
     *
     * <p>Et l'ordre d'un {@code HashSet} d'enumerations n'est pas seulement
     * arbitraire : <b>il change a chaque demarrage de la JVM</b>, parce que le
     * {@code hashCode} d'une constante d'enumeration est celui de l'objet, lie
     * a son adresse en memoire. Constate le 29 septembre sur le meme eleveur,
     * a dix minutes d'intervalle, avec un redemarrage entre les deux :
     *
     * <pre>
     *   01h51  →  CAPRINS, VOLAILLE, BOVINS, OVINS
     *   02h01  →  VOLAILLE, CAPRINS, OVINS, BOVINS
     * </pre>
     *
     * <p>Cette liste alimente la barre d'onglets de l'application mobile. Sans
     * ordre stable, <b>les onglets changeraient de place d'un redemarrage du
     * serveur a l'autre</b> — sans qu'aucune erreur ne soit levee, et sans
     * qu'on puisse reproduire le probleme a volonte.
     *
     * <p>Un {@code EnumSet} reconstruit ici retablit l'ordre de declaration,
     * qui est stable, lisible, et place la volaille en tete comme le fait la
     * maquette.
     *
     * <p>{@code EnumSet.copyOf} n'est pas utilise : il leve
     * {@code IllegalArgumentException} sur une collection vide, ce qui est le
     * cas d'un compte dont le profil n'est pas encore complete.
     */
    public Set<Filiere> filieresOrdonnees() {
        EnumSet<Filiere> ordonnees = EnumSet.noneOf(Filiere.class);
        ordonnees.addAll(filieresPratiquees);
        return ordonnees;
    }

    /** Sert au ciblage des alertes : « 318 eleveurs de volaille a Koutiala ». */
    public boolean pratique(Filiere filiere) {
        return filieresPratiquees.contains(filiere);
    }
}
