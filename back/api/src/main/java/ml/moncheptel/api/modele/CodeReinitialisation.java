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
 * Code a six chiffres envoye par courriel pour reinitialiser un mot de passe.
 *
 * <h2>Le code n'est pas stocke</h2>
 *
 * <p>Seule son empreinte SHA-256 l'est, comme pour les jetons de
 * rafraichissement ({@link Session#getEmpreinteJeton()}). Un code en clair en
 * base donnerait a quiconque lit la table — un administrateur, une sauvegarde
 * qui fuit, une injection SQL — le moyen de prendre n'importe quel compte sans
 * jamais toucher a la boite de reception de son proprietaire.
 *
 * <p>BCrypt n'est pas employe ici, a la difference du mot de passe : un code
 * de six chiffres vit quinze minutes et ne peut etre presente que cinq fois.
 * SHA-256 protege la table contre la lecture, et c'est tout ce qu'on lui
 * demande. BCrypt couterait 100 ms par tentative sans rien ajouter — la
 * defense contre la force brute est la duree de vie et le compteur, pas le
 * cout du hachage.
 *
 * <h2>Usage unique</h2>
 *
 * <p>{@link #consommer()} pose {@code utilise}. Sans cela, un code intercepte
 * dans une boite restee ouverte servirait autant de fois qu'on veut pendant
 * son quart d'heure — et rien n'empecherait de reprendre le compte juste apres
 * que son proprietaire a fini.
 */
@Entity
@Table(name = "code_reinitialisation")
public class CodeReinitialisation extends EntiteBase {

    /** Nombre de verifications ratees tolerees pour un meme code. */
    public static final int TENTATIVES_MAXIMALES = 5;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    /** SHA-256 du code, base64 URL sans remplissage : 43 caracteres. */
    @NotBlank
    @Size(max = 64)
    @Column(name = "empreinte_code", nullable = false, length = 64)
    private String empreinteCode;

    @Column(name = "expire_le", nullable = false)
    private Instant expireLe;

    @Column(nullable = false)
    private boolean utilise = false;

    /**
     * Compteur d'echecs propre au code.
     *
     * <p>Distinct du compteur de l'utilisateur : celui-ci verrouille le compte
     * entier, ce qui reviendrait a laisser un attaquant bloquer n'importe qui
     * en demandant un code puis en le tapant faux cinq fois. Ici, seul le code
     * est brule ; le compte reste accessible a qui connait son mot de passe.
     */
    @Column(name = "tentatives", nullable = false)
    private int tentatives = 0;

    protected CodeReinitialisation() {
    }

    public CodeReinitialisation(Utilisateur utilisateur, String empreinteCode,
                                Instant expireLe) {
        this.utilisateur = utilisateur;
        this.empreinteCode = empreinteCode;
        this.expireLe = expireLe;
    }

    /** Vrai si le code peut encore etre presente. */
    public boolean estUtilisable() {
        return !utilise
                && tentatives < TENTATIVES_MAXIMALES
                && expireLe.isAfter(Instant.now());
    }

    public void consommer() {
        this.utilise = true;
    }

    public void signalerEchec() {
        this.tentatives++;
    }

    public Utilisateur getUtilisateur() { return utilisateur; }
    public String getEmpreinteCode() { return empreinteCode; }
    public Instant getExpireLe() { return expireLe; }
    public boolean isUtilise() { return utilise; }
    public int getTentatives() { return tentatives; }
}
