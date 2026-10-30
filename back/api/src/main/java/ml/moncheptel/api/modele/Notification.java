package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import ml.moncheptel.api.modele.enumeration.StatutEnvoi;
import ml.moncheptel.api.modele.enumeration.TypeNotification;

/**
 * Notification destinee a un utilisateur.
 *
 * <p>Conservee en base meme apres l'envoi push : l'ecran « Notifications -
 * Liste » doit pouvoir les relire, et un telephone eteint au moment d'une
 * alerte epidemique doit la retrouver au rallumage. Une alerte qui n'arrive
 * pas ne sert a rien.
 *
 * <p><b>La cible est un pointeur souple</b> — un type et un identifiant — et
 * non dix cles etrangeres nullables. Une notification ne mérite pas
 * d'integrite referentielle : si la cible disparait, elle cesse simplement
 * d'etre cliquable.
 */
@Entity
@Table(name = "notification")
public class Notification extends EntiteBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destinataire_id", nullable = false)
    private Utilisateur destinataire;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private TypeNotification type;

    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String titre;

    @Size(max = 500)
    @Column(length = 500)
    private String corps;

    @Column(nullable = false)
    private boolean lue = false;

    // ── cible ───────────────────────────────────────────────────────────────

    @Size(max = 20)
    @Column(name = "reference_type", length = 20)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    // ── envoi push (Firebase Cloud Messaging) ───────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_envoi", nullable = false, length = 12)
    private StatutEnvoi statutEnvoi = StatutEnvoi.EN_ATTENTE;

    @Column(name = "date_envoi")
    private Instant dateEnvoi;

    @Column(name = "tentatives_envoi", nullable = false)
    private int tentativesEnvoi = 0;

    @Size(max = 300)
    @Column(name = "erreur_envoi", length = 300)
    private String erreurEnvoi;

    protected Notification() {
    }

    public Notification(Utilisateur destinataire, TypeNotification type,
                        String titre, String corps, String referenceType, Long referenceId) {
        this.destinataire = destinataire;
        this.type = type;
        this.titre = titre;
        this.corps = corps;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }

    public void marquerLue() {
        this.lue = true;
    }

    public void marquerEnvoyee() {
        this.statutEnvoi = StatutEnvoi.ENVOYE;
        this.dateEnvoi = Instant.now();
        this.erreurEnvoi = null;
    }

    /**
     * Enregistre un echec d'envoi. Au-dela de trois tentatives on abandonne :
     * s'acharner sur un jeton mort coute du quota et ne notifie personne.
     */
    public void marquerEchec(String erreur, int tentativesMax) {
        this.tentativesEnvoi++;
        this.erreurEnvoi = erreur == null ? null
                : erreur.substring(0, Math.min(erreur.length(), 300));
        this.statutEnvoi = tentativesEnvoi >= tentativesMax
                ? StatutEnvoi.ABANDONNE
                : StatutEnvoi.ECHEC;
    }

    public Utilisateur getDestinataire() { return destinataire; }
    public TypeNotification getType() { return type; }
    public String getTitre() { return titre; }
    public String getCorps() { return corps; }
    public boolean isLue() { return lue; }
    public String getReferenceType() { return referenceType; }
    public Long getReferenceId() { return referenceId; }
    public StatutEnvoi getStatutEnvoi() { return statutEnvoi; }
    public Instant getDateEnvoi() { return dateEnvoi; }
    public int getTentativesEnvoi() { return tentativesEnvoi; }
    public String getErreurEnvoi() { return erreurEnvoi; }
}
