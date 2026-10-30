package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Jeton d'enregistrement Firebase Cloud Messaging d'un appareil.
 *
 * <p>N'apparait sur aucun ecran, et n'est pourtant pas optionnel : sans lui,
 * aucune notification push ne peut atteindre un telephone. FCM n'adresse pas
 * des utilisateurs, il adresse des appareils.
 *
 * <p>Un utilisateur peut en avoir plusieurs — un telephone et une tablette —
 * d'ou la table plutot qu'une colonne sur {@code utilisateur}. Les jetons
 * expirent et sont renouveles par le client : {@code derniereUtilisation}
 * permet d'elaguer ceux qui ne servent plus.
 */
@Entity
@Table(name = "jeton_appareil")
public class JetonAppareil extends EntiteBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    /** Le jeton FCM. Long : jusqu'a 255 caracteres. */
    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, unique = true, length = 255)
    private String jeton;

    /** ANDROID, IOS, WEB. */
    @Size(max = 12)
    @Column(length = 12)
    private String plateforme;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "derniere_utilisation", nullable = false)
    private Instant derniereUtilisation = Instant.now();

    protected JetonAppareil() {
    }

    public JetonAppareil(Utilisateur utilisateur, String jeton, String plateforme) {
        this.utilisateur = utilisateur;
        this.jeton = jeton;
        this.plateforme = plateforme;
    }

    public void rafraichir() {
        this.derniereUtilisation = Instant.now();
        this.actif = true;
    }

    /** Jeton refuse par FCM : inutile de reessayer dessus. */
    /**
     * Rattache ce jeton a un autre utilisateur et le reactive.
     *
     * <p>Un telephone prete, reinstalle, ou simplement une deconnexion suivie
     * d'une autre connexion : Firebase rend le meme jeton. Le laisser sur
     * l'ancien compte enverrait a l'ancien proprietaire les alertes du
     * nouveau.
     */
    public void rattacherA(Utilisateur nouveau) {
        this.utilisateur = nouveau;
        this.actif = true;
        this.derniereUtilisation = java.time.Instant.now();
    }

    public void desactiver() {
        this.actif = false;
    }

    public Utilisateur getUtilisateur() { return utilisateur; }
    public String getJeton() { return jeton; }
    public String getPlateforme() { return plateforme; }
    public boolean isActif() { return actif; }
    public Instant getDerniereUtilisation() { return derniereUtilisation; }
}
