package ml.moncheptel.api.mapper;

import java.util.List;
import ml.moncheptel.api.dto.reponse.DiagnosticReponse;
import ml.moncheptel.api.dto.reponse.SignalementReponse;
import ml.moncheptel.api.dto.reponse.SymptomeReponse;
import ml.moncheptel.api.modele.SignalementMaladie;
import org.springframework.stereotype.Component;

/**
 * Signalements de maladie.
 *
 * <p>Deux vues, jamais interchangeables :
 *
 * <ul>
 *   <li>{@code versReponseEleveur} masque le traitement des maladies orientees
 *       (c'est le propre du signalement : on oriente parce que l'eleveur ne
 *       doit pas traiter seul) et n'expose pas son propre telephone.</li>
 *   <li>{@code versReponseVeterinaire} rend le traitement, et ajoute le
 *       telephone et la zone de l'eleveur — un veterinaire qui prend un cas en
 *       charge doit pouvoir appeler et se deplacer.</li>
 * </ul>
 *
 * <p>Les symptomes sont passes en parametre plutot que charges ici : le
 * signalement ne stocke que des identifiants, et un mapper qui ferait une
 * requete par signalement produirait un N+1 sur la file du veterinaire.
 */
@Component
public class SignalementMapper {

    private final MaladieMapper maladieMapper;
    private final UtilisateurMapper utilisateurMapper;

    public SignalementMapper(MaladieMapper maladieMapper, UtilisateurMapper utilisateurMapper) {
        this.maladieMapper = maladieMapper;
        this.utilisateurMapper = utilisateurMapper;
    }

    public SignalementReponse versReponseEleveur(SignalementMaladie s,
                                                 List<SymptomeReponse> symptomes) {
        return construire(s, symptomes, false);
    }

    public SignalementReponse versReponseVeterinaire(SignalementMaladie s,
                                                     List<SymptomeReponse> symptomes) {
        return construire(s, symptomes, true);
    }

    private SignalementReponse construire(SignalementMaladie s,
                                          List<SymptomeReponse> symptomes,
                                          boolean vuePraticien) {
        if (s == null) {
            return null;
        }
        return new SignalementReponse(
                s.getId(),
                s.getUnite() == null ? null : s.getUnite().getId(),
                s.getUnite() == null ? null : s.getUnite().getCode(),
                s.getEleveur() == null ? null : s.getEleveur().getNom(),
                vuePraticien && s.getEleveur() != null ? s.getEleveur().getTelephone() : null,
                vuePraticien && s.getEleveur() != null && s.getEleveur().getZone() != null
                        ? s.getEleveur().getZone().getNom() : null,
                symptomes,
                s.getTrancheEffectif(),
                s.getPrecision(),
                s.getDateSignalement(),
                versDiagnostic(s, vuePraticien),
                s.getStatut(),
                s.getDateTraitement(),
                s.getCompteRendu(),
                s.getUnite() == null || s.getUnite().getTypeElevage() == null
                        ? null : s.getUnite().getTypeElevage().getLibelle(),
                s.getUnite() == null ? null : s.getUnite().getEffectifRestant(),
                s.getMaladieConfirmee() == null ? null : s.getMaladieConfirmee().getNom(),
                s.getUnite() == null || s.getUnite().getTypeElevage() == null
                        ? null : s.getUnite().getFiliere(),
                utilisateurMapper.versResume(s.getVeterinaire()),
                s.getDateOrientation(),
                s.getDatePriseEnCharge(),
                java.util.List.of());
    }

    /** Historique des refus (V14), pour le detail d'un cas. */
    public java.util.List<ml.moncheptel.api.dto.reponse.RefusReponse> versRefus(
            java.util.List<ml.moncheptel.api.modele.RefusSignalement> refus) {
        return refus.stream()
                .map(r -> new ml.moncheptel.api.dto.reponse.RefusReponse(
                        r.getVeterinaireNom(), r.getMotif(), r.getDateRefus()))
                .toList();
    }

    /**
     * Le diagnostic tel qu'il a ete FIGE sur le signalement.
     *
     * <p>Il n'est pas recalcule a la lecture. Si la ponderation du referentiel
     * change demain, un signalement traite hier doit continuer a montrer ce
     * que le veterinaire a vu quand il a decide : un dossier medical ne se
     * reecrit pas retroactivement.
     */
    public DiagnosticReponse versDiagnostic(SignalementMaladie s, boolean vuePraticien) {
        if (s == null || s.getMaladieProbable() == null) {
            return null;
        }
        return new DiagnosticReponse(
                vuePraticien ? maladieMapper.versReponseAdmin(s.getMaladieProbable())
                             : maladieMapper.versReponseEleveur(s.getMaladieProbable()),
                vuePraticien ? maladieMapper.versReponseAdmin(s.getMaladieAlternative())
                             : maladieMapper.versReponseEleveur(s.getMaladieAlternative()),
                s.getGravite(),
                s.doitEtreOriente(),
                utilisateurMapper.versResume(s.getVeterinaire()));
    }
}
