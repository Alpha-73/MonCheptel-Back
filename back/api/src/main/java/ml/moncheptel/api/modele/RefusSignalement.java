package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Un veterinaire a refuse un cas qui lui etait confie (V14).
 *
 * <p>Deux usages :
 * <ul>
 *   <li>ce veterinaire n'est plus propose a l'eleveur pour CE cas : sans
 *       cette trace, l'eleveur pouvait lui renvoyer le cas qu'il venait de
 *       rendre ;</li>
 *   <li>l'administration voit qui a refuse, quand et pourquoi.</li>
 * </ul>
 *
 * <p>Identifiants simples et non relations : la ligne n'est jamais chargee
 * avec le signalement, et le nom copie survit a la suppression du compte
 * (la base remet alors {@code veterinaire_id} a NULL).
 */
@Entity
@Table(name = "signalement_refus")
public class RefusSignalement extends EntiteBase {

    @Column(name = "signalement_id", nullable = false)
    private Long signalementId;

    @Column(name = "veterinaire_id")
    private Long veterinaireId;

    @Size(max = 120)
    @Column(name = "veterinaire_nom", nullable = false, length = 120)
    private String veterinaireNom;

    @Size(max = 500)
    @Column(length = 500)
    private String motif;

    @Column(name = "date_refus", nullable = false)
    private Instant dateRefus = Instant.now();

    protected RefusSignalement() {
    }

    public RefusSignalement(Long signalementId, Long veterinaireId,
                            String veterinaireNom, String motif) {
        this.signalementId = signalementId;
        this.veterinaireId = veterinaireId;
        this.veterinaireNom = veterinaireNom == null ? "" : veterinaireNom;
        this.motif = motif == null || motif.isBlank() ? null : motif.strip();
    }

    public Long getSignalementId() { return signalementId; }
    public Long getVeterinaireId() { return veterinaireId; }
    public String getVeterinaireNom() { return veterinaireNom; }
    public String getMotif() { return motif; }
    public Instant getDateRefus() { return dateRefus; }
}
