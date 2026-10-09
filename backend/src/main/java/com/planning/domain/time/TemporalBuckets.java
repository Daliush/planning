package com.planning.domain.time;

import com.planning.domain.model.Period;
import com.planning.domain.model.Timeslot;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

/** Calculs calendaires purs ; aucune date ambiante n'entre dans le score. */
public final class TemporalBuckets {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);
    private TemporalBuckets() { }

    public static int bucketOf(Timeslot slot, Period period) {
        if (slot.date() == null) return CycleBuckets.bucketOf(slot, period);
        return bucketOf(slot.date(), period);
    }

    public static int bucketOf(LocalDate date, Period period) {
        return switch (period) {
            case DAY -> Math.toIntExact(date.toEpochDay());
            case WEEK -> weekIndex(date);
            case MONTH -> date.getYear() * 12 + date.getMonthValue() - 1;
            case CYCLE -> 0;
        };
    }

    public static String label(Period period, int index) {
        return switch (period) {
            case DAY -> "du " + LocalDate.ofEpochDay(index).format(DAY);
            case WEEK -> "de la semaine du " + LocalDate.ofEpochDay(index).format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH));
            case MONTH -> "du mois de " + LocalDate.of(index / 12, index % 12 + 1, 1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH));
            case CYCLE -> "de la période";
        };
    }

    public static LocalDate startDateOf(Timeslot slot) { return slot.startDateTime().toLocalDate(); }

    public static int weekIndex(LocalDate date) {
        return Math.toIntExact(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay());
    }

    public static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    public static boolean consecutiveDates(LocalDate previous, LocalDate next) {
        return previous.plusDays(1).equals(next);
    }

    public static int restMinutes(LocalDateTime end, LocalDateTime nextStart) {
        return Math.toIntExact(Math.max(0, Duration.between(end, nextStart).toMinutes()));
    }

    /** L'intervalle est semi-ouvert : une fin à minuit ne travaille pas le lendemain. */
    public static List<LocalDate> occupiedDates(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) return List.of();
        return start.toLocalDate().datesUntil(end.minusNanos(1).toLocalDate().plusDays(1)).toList();
    }

    /** Minutes hors d'une fenêtre quotidienne, y compris pour un service de nuit. */
    public static int outsideWindowMinutes(LocalDateTime start, LocalDateTime end,
                                           LocalTime from, LocalTime to) {
        if (!end.isAfter(start)) return 0;
        long inside = 0;
        for (LocalDate date : start.toLocalDate().minusDays(1)
                .datesUntil(end.toLocalDate().plusDays(1)).toList()) {
            LocalDateTime windowStart = date.atTime(from);
            LocalDateTime windowEnd = date.atTime(to);
            if (!windowEnd.isAfter(windowStart)) windowEnd = windowEnd.plusDays(1);
            LocalDateTime overlapStart = start.isAfter(windowStart) ? start : windowStart;
            LocalDateTime overlapEnd = end.isBefore(windowEnd) ? end : windowEnd;
            if (overlapEnd.isAfter(overlapStart)) inside += Duration.between(overlapStart, overlapEnd).toMinutes();
        }
        return Math.toIntExact(Duration.between(start, end).toMinutes() - inside);
    }
}
