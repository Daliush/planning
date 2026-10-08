package com.planning.domain.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

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
        LocalTime end) {

    public int durationMinutes() {
        return (int) Duration.between(start, end).toMinutes();
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
