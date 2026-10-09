package com.planning.domain.builder;

import com.planning.domain.exception.IncompatibleTimeModelException;
import com.planning.domain.model.HorizonKind;
import com.planning.domain.model.Timeslot;
import com.planning.domain.port.Rows.SlotRow;
import com.planning.domain.port.Rows.TimeModelRow;
import com.planning.domain.time.GapPolicy;
import com.planning.domain.time.TimeGrid;
import com.planning.domain.time.TimeHorizon;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Construit l'horizon à partir du modèle temporel et de la grille stockée.
 *
 * <p>C'est ici que naît l'{@code index} global des créneaux — la clé qui indexe
 * tous les {@code BitSet}. Il n'existe qu'en mémoire : la base ne stocke que des
 * heures réelles (INVARIANT §5.3).
 */
public class HorizonFactory {

    private static final Comparator<SlotRow> GRID_ORDER =
            Comparator.comparingInt(SlotRow::dayInCycle)
                    .thenComparing(SlotRow::startTime)
                    .thenComparingInt(SlotRow::ordinal);

    public TimeHorizon create(TimeModelRow model, List<SlotRow> slotRows) {
        if (model.maxGapMinutes() < 0) {
            throw new IncompatibleTimeModelException("La pause maximale ne peut pas être négative.");
        }
        if (model.horizonKind() == HorizonKind.DATED) {
            return dated(model, slotRows);
        }
        int cycleDays = model.cycleDays() == null ? 0 : model.cycleDays();
        if (cycleDays <= 0) {
            throw new IncompatibleTimeModelException(
                    "Un modèle temporel cyclique doit déclarer un nombre de jours de cycle.");
        }

        List<Timeslot> slots = index(slotRows, cycleDays);
        TimeGrid grid = TimeGrid.from(slots);
        return new TimeHorizon.Cyclic(cycleDays, slots, grid, new GapPolicy(model.maxGapMinutes()));
    }

    private TimeHorizon dated(TimeModelRow model, List<SlotRow> rows) {
        LocalDate from = model.horizonFrom();
        LocalDate to = model.horizonTo();
        if (from == null || to == null || to.isBefore(from)
                || ChronoUnit.DAYS.between(from, to) >= 366) {
            throw new IncompatibleTimeModelException("La période datée doit contenir de 1 à 366 jours.");
        }
        if (rows.stream().anyMatch(row -> row.dayInCycle() < 0 || row.dayInCycle() > 6)) {
            throw new IncompatibleTimeModelException("Une grille datée utilise les jours de semaine de 0 à 6.");
        }
        List<SlotRow> ordered = rows.stream().sorted(GRID_ORDER).toList();
        List<Timeslot> slots = new ArrayList<>();
        for (LocalDate date : from.datesUntil(to.plusDays(1)).toList()) {
            int weekday = date.getDayOfWeek().getValue() - 1;
            int day = Math.toIntExact(ChronoUnit.DAYS.between(from, date));
            for (SlotRow row : ordered) {
                if (row.dayInCycle() == weekday) {
                    slots.add(new Timeslot(slots.size(), day, row.ordinal(), date.getDayOfWeek(),
                            row.startTime(), row.endTime(), date));
                }
            }
        }
        List<Timeslot> immutable = List.copyOf(slots);
        return new TimeHorizon.Dated(from, to, immutable, TimeGrid.from(immutable),
                new GapPolicy(model.maxGapMinutes()));
    }

    private List<Timeslot> index(List<SlotRow> slotRows, int cycleDays) {
        List<SlotRow> ordered = new ArrayList<>(slotRows);
        ordered.sort(GRID_ORDER);

        List<Timeslot> slots = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            SlotRow row = ordered.get(index);
            if (row.dayInCycle() < 0 || row.dayInCycle() >= cycleDays
                    || !row.endTime().isAfter(row.startTime())) {
                throw new IncompatibleTimeModelException(
                        "Le créneau du jour %d dépasse le cycle de %d jours."
                                .formatted(row.dayInCycle(), cycleDays));
            }
            slots.add(new Timeslot(
                    index,
                    row.dayInCycle(),
                    row.ordinal(),
                    // Confort d'affichage uniquement : aucune contrainte ne doit
                    // dépendre du jour de la semaine (§7.1).
                    DayOfWeek.of(row.dayInCycle() % 7 + 1),
                    row.startTime(),
                    row.endTime()));
        }
        return List.copyOf(slots);
    }
}
