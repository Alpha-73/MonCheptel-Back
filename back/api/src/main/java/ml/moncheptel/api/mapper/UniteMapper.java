package ml.moncheptel.api.mapper;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import ml.moncheptel.api.dto.reponse.EvenementReponse;
import ml.moncheptel.api.dto.reponse.MouvementReponse;
import ml.moncheptel.api.dto.reponse.RappelReponse;
import ml.moncheptel.api.dto.reponse.SortieReponse;
import ml.moncheptel.api.dto.reponse.UniteResumeReponse;
import ml.moncheptel.api.modele.Animal;
import ml.moncheptel.api.modele.EvenementSanitaire;
import ml.moncheptel.api.modele.Lot;
import ml.moncheptel.api.modele.MouvementCheptel;
import ml.moncheptel.api.modele.RappelSanitaire;
import ml.moncheptel.api.modele.UniteElevage;
import org.springframework.stereotype.Component;

/**
 * Lots et animaux, aplatis dans une seule forme de reponse.
 *
 * <p>C'est ici, et seulement ici, que l'heritage {@code Lot} / {@code Animal}
 * disparait. Le modele a besoin de deux classes — un lot se retire par
 * tranches, un animal sort d'un coup — mais la liste « Mes animaux » les
 * melange, et deux reponses distinctes obligeraient le client a deux appels
 * et deux rendus pour une seule liste.
 */
@Component
public class UniteMapper {

    public UniteResumeReponse versResume(UniteElevage u) {
        return versResume(u, LocalDate.now());
    }

    /**
     * @param aujourdHui injecte plutot que lu de l'horloge : sans cela,
     *                   {@code joursRestants} serait intestable.
     */
    public UniteResumeReponse versResume(UniteElevage u, LocalDate aujourdHui) {
        if (u == null) {
            return null;
        }
        LocalDate cloture = (u instanceof Lot lot) ? lot.getDateCloturePrevue() : null;
        Animal animal = (u instanceof Animal a) ? a : null;
        Integer joursRestants = cloture == null
                ? null
                : (int) ChronoUnit.DAYS.between(aujourdHui, cloture);

        return new UniteResumeReponse(
                u.getId(),
                u.getCode(),
                u.getFiliere().getModeSuivi(),
                u.getFiliere(),
                u.getTypeElevage() == null ? null : u.getTypeElevage().getLibelle(),
                u.getStatut(),
                u.getDateEntree(),
                u.getEffectifInitial(),
                u.getEffectifRestant(),
                cloture,
                joursRestants,
                u.getTypeElevage() == null ? null : u.getTypeElevage().getId(),
                animal == null ? null : animal.getRace(),
                animal == null ? null : animal.getSexe(),
                animal == null ? null : animal.getDateNaissance(),
                u.getNote());
    }

    public List<UniteResumeReponse> versResumes(List<? extends UniteElevage> unites) {
        LocalDate aujourdHui = LocalDate.now();
        return unites.stream().map(u -> versResume(u, aujourdHui)).toList();
    }

    public MouvementReponse versReponse(MouvementCheptel m) {
        if (m == null) {
            return null;
        }
        return new MouvementReponse(
                m.getId(), m.getType(), m.getNombreTetes(),
                m.getDateMouvement(), m.getObservation());
    }

    public SortieReponse versSortie(MouvementCheptel m) {
        if (m == null) {
            return null;
        }
        return new SortieReponse(m.getId(), m.getDateMouvement(), m.getType(),
                m.getNombreTetes(), m.getMontant(), m.getObservation());
    }

    public EvenementReponse versReponse(EvenementSanitaire e) {
        if (e == null) {
            return null;
        }
        return new EvenementReponse(
                e.getId(),
                e.getType(),
                e.getDateEvenement(),
                e.getProduitObservation(),
                e.getAuteur() == null ? null : e.getAuteur().getNom(),
                e.getVeterinaire() == null ? null : e.getVeterinaire().getNom());
    }

    public RappelReponse versReponse(RappelSanitaire r, LocalDate aujourdHui) {
        if (r == null) {
            return null;
        }
        return new RappelReponse(
                r.getId(),
                r.getUnite() == null ? null : r.getUnite().getId(),
                r.getUnite() == null ? null : r.getUnite().getCode(),
                r.getLibelle(),
                r.getDateEcheance(),
                (int) ChronoUnit.DAYS.between(aujourdHui, r.getDateEcheance()),
                r.getStatut());
    }

    public List<RappelReponse> versRappels(List<RappelSanitaire> rappels) {
        LocalDate aujourdHui = LocalDate.now();
        return rappels.stream().map(r -> versReponse(r, aujourdHui)).toList();
    }
}
