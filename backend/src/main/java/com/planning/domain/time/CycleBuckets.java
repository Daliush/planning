package com.planning.domain.time;

import com.planning.domain.exception.IncompatibleConfigException;
import com.planning.domain.model.Period;
import com.planning.domain.model.Timeslot;

/**
 * Le module temporel du mode cyclique — <strong>seul endroit du code où une
 * notion de cycle, de semaine ou de récurrence est manipulée</strong>
 * (INVARIANT §14.2, n° 10).
 *
 * <p>Ses fonctions sont pures et ne dépendent que du {@link Timeslot}, ce qui
 * permet aux plugins de les appeler dans la boucle chaude sans porter d'instance
 * de {@link TimeHorizon}. {@code TimeHorizon.Cyclic} y délègue : la logique
 * n'existe qu'à un seul endroit, et l'ajout du mode daté consistera à ajouter la
 * classe jumelle plutôt qu'à disséminer des {@code if}.
 */
public final class CycleBuckets {

    private CycleBuckets() {
    }

    /**
     * Index du panier d'agrégation. Sa composition, combinée à la ressource et à
     * la règle, définit la sémantique de la contrainte (§11.6).
     */
    public static int bucketOf(Timeslot slot, Period period) {
        if (slot.date() != null) return TemporalBuckets.bucketOf(slot, period);
        return switch (period) {
            case DAY -> slot.dayInCycle();
            case WEEK -> slot.dayInCycle() / 7;
            case CYCLE -> 0;
            case MONTH -> throw new IncompatibleConfigException(
                    "La période MONTH n'est pas disponible sur un horizon CYCLIC : "
                            + "un mois n'est ni aligné sur le cycle ni de longueur fixe.");
        };
    }

    /**
     * « Même position récurrente » : en mode cyclique, même jour de semaine et
     * même heure. Deux séances du lundi de la semaine A et du lundi de la
     * semaine B d'un cycle de 14 jours sont à la même récurrence.
     */
    public static boolean sameRecurrence(Timeslot a, Timeslot b) {
        if (a.date() != null && b.date() != null) return a.weekday() == b.weekday() && a.start().equals(b.start());
        return a.dayInCycle() % 7 == b.dayInCycle() % 7 && a.start().equals(b.start());
    }

    public static boolean supports(Period period) {
        return period != Period.MONTH;
    }

    /** Libellé lisible d'un panier, pour les messages de violation. */
    public static String label(Period period, int bucket) {
        return switch (period) {
            case DAY -> "du " + dayName(bucket);
            case WEEK -> "de la semaine " + (bucket + 1);
            case CYCLE -> "du cycle";
            case MONTH -> "du mois";
        };
    }

    public static String dayLabel(int dayInCycle) {
        return dayName(dayInCycle);
    }

    private static String dayName(int dayInCycle) {
        String[] names = {"lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"};
        String name = names[dayInCycle % 7];
        return dayInCycle < 7 ? name : "%s (jour %d du cycle)".formatted(name, dayInCycle + 1);
    }
}
