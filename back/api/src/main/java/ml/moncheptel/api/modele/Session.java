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
 * Session ouverte par un utilisateur, porteuse du jeton de rafraichissement.
 *
 * <p>Sans elle, l'authentification JWT serait purement apatride et une
 * deconnexion ne revoquerait rien : le jeton resterait valable jusqu'a son
 * expiration. La table permet de couper l'acces immediatement — et de le
 * couper sur TOUS les appareils quand un mot de passe change.
 *
 * <p>Le jeton n'est pas stocke en clair : on garde son empreinte. Une fuite de
 * la base ne doit pas donner les sessions en cours.
 */
@Entity
@Table(name = "session")
public class Session extends EntiteBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    /** Empreinte SHA-256 du jeton de rafraichissement, jamais le jeton lui-meme. */
    @NotBlank
    @Size(max = 64)
    @Column(name = "empreinte_jeton", nullable = false, unique = true, length = 64)
    private String empreinteJeton;

    @Column(name = "expire_le", nullable = false)
    private Instant expireLe;

    @Column(nullable = false)
    private boolean revoquee = false;

    /** Pour que l'utilisateur reconnaisse ses sessions : « Android 14 ». */
    @Size(max = 200)
    @Column(name = "appareil", length = 200)
    private String appareil;

    @Size(max = 45)
    @Column(name = "adresse_ip", length = 45)
    private String adresseIp;

    protected Session() {
    }

    public Session(Utilisateur utilisateur, String empreinteJeton,
                   Instant expireLe, String appareil, String adresseIp) {
        this.utilisateur = utilisateur;
        this.empreinteJeton = empreinteJeton;
        this.expireLe = expireLe;
        this.appareil = appareil;
        this.adresseIp = adresseIp;
    }

    /** Valide : ni revoquee, ni expiree. */
    public boolean estValide() {
        return !revoquee && expireLe.isAfter(Instant.now());
    }

    public void revoquer() {
        this.revoquee = true;
    }

    public Utilisateur getUtilisateur() { return utilisateur; }
    public String getEmpreinteJeton() { return empreinteJeton; }
    public Instant getExpireLe() { return expireLe; }
    public boolean isRevoquee() { return revoquee; }
    public String getAppareil() { return appareil; }
    public String getAdresseIp() { return adresseIp; }
}
