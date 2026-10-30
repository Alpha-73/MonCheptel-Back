package ml.moncheptel.api.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import ml.moncheptel.api.exception.RegleMetierException;
import ml.moncheptel.api.modele.UniteElevage;

/**
 * Bornes des dates saisies sur un elevage (audit eleveur F11, F12).
 *
 * <p>Une sortie, un soin ou une mise en place ne peuvent ni etre dans le
 * futur, ni preceder l'entree de l'elevage. L'application borne deja ses
 * selecteurs de date ; le back ne se fie pas au client.
 */
final class ControleDates {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private ControleDates() {
    }

    static String jour(LocalDate date) {
        return date.format(FORMAT);
    }

    /** Refuse une date future. {@code quoi} : « de la sortie », « du soin »… */
    static void exigerPasFuture(LocalDate date, String quoi) {
        if (date != null && date.isAfter(LocalDate.now())) {
            throw new RegleMetierException(
                    "La date " + quoi + " ne peut pas être dans le futur.");
        }
    }

    /** Refuse une date future ou anterieure a l'entree de l'elevage. */
    static void exigerDansLaVieDe(UniteElevage unite, LocalDate date, String quoi) {
        exigerPasFuture(date, quoi);
        if (date != null && unite.getDateEntree() != null
                && date.isBefore(unite.getDateEntree())) {
            throw new RegleMetierException(
                    "La date " + quoi + " (" + jour(date) + ") précède l'entrée de "
                    + unite.getCode() + " (" + jour(unite.getDateEntree()) + ").");
        }
    }
}
