package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.Objects;

/**
 * Socle commun a toutes les entites : identifiant, horodatage, egalite.
 *
 * <p><b>Pourquoi un equals/hashCode ecrit a la main.</b> L'implementation par
 * defaut compare les references : deux chargements de la meme ligne dans deux
 * transactions donnent alors deux objets « differents », et un {@code Set}
 * contient deux fois le meme lot. Comparer les identifiants regle ca, a une
 * condition : {@code hashCode} doit rester constant meme quand l'entite passe
 * de transitoire (id nul) a persistante (id attribue). D'ou la constante.
 */
@MappedSuperclass
public abstract class EntiteBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant dateCreation;

    @Column(name = "date_modification")
    private Instant dateModification;

    @PrePersist
    void avantCreation() {
        this.dateCreation = Instant.now();
        this.dateModification = this.dateCreation;
    }

    @PreUpdate
    void avantModification() {
        this.dateModification = Instant.now();
    }

    public Long getId() {
        return id;
    }

    protected void setId(Long id) {
        this.id = id;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public Instant getDateModification() {
        return dateModification;
    }

    /** Vrai tant que l'entite n'a pas ete enregistree. */
    public boolean estNouvelle() {
        return id == null;
    }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof EntiteBase e)) return false;
        // Deux entites non enregistrees ne sont egales que si c'est le meme objet.
        return id != null && Objects.equals(id, e.getId())
                && getClass().equals(autre.getClass());
    }

    @Override
    public int hashCode() {
        // Constante volontaire : l'identifiant change a l'enregistrement, et un
        // hashCode qui change pendant qu'on est dans un HashSet casse le Set.
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "#" + id;
    }
}
