package com.planning.domain.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un créneau de la grille. Fait du problème, strictement immuable (§8.4).
 *
 * <p>{@code index} est la clé de tout le système : c'est lui qui indexe les
 * {@link TimeProfile}. {@code ordinal} est relatif à la journée — le premier
 * cours du lundi et celui du jeudi ont tous deux {@code ordinal = 0} alors que
 * leurs index diffèrent.
 *
 * <p>PIÈGE §6.7 : deux ordinaux successifs ne sont pas nécessairement contigus
 * dans le temps. Toute règle parlant de séances consécutives doit tester
 * {@code a.end().equals(b.start())}, jamais {@code ordinal + 1}.
 */
public record Timeslot(
        int index,
        int dayInCycle,
        int ordinal,
        DayOfWeek weekday,
        LocalTime start,
        LocalTime end,
        LocalDate date) {

    public Timeslot(int index, int dayInCycle, int ordinal, DayOfWeek weekday,
                    LocalTime start, LocalTime end) {
        this(index, dayInCycle, ordinal, weekday, start, end, null);
    }

    public LocalDateTime startDateTime() {
        LocalDate calendarDay = date == null ? LocalDate.of(2000, 1, 3).plusDays(dayInCycle) : date;
        return calendarDay.atTime(start);
    }

    public LocalDateTime endDateTime() {
        LocalDateTime finish = startDateTime().toLocalDate().atTime(end);
        return end.equals(LocalTime.MIDNIGHT) && start.isAfter(end) ? finish.plusDays(1) : finish;
    }

    public int durationMinutes() {
        return Math.toIntExact(Duration.between(startDateTime(), endDateTime()).toMinutes());
    }

    /**
     * Identité sur l'index seul : le créneau est un fait partagé par référence
     * entre tous les clones, et sert de valeur de planification comparée en
     * boucle chaude.
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof Timeslot other && other.index == index;
    }

    @Override
    public int hashCode() {
        return index;
    }

    @Override
    public String toString() {
        return "J%d %s-%s".formatted(dayInCycle, start, end);
    }
}
