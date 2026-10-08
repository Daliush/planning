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
        if (model.horizonKind() != HorizonKind.CYCLIC) {
            throw new IncompatibleTimeModelException(
                    "Seul l'horizon CYCLIC est implémenté en V1. Le mode DATED est prévu au jalon 4.");
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

    private List<Timeslot> index(List<SlotRow> slotRows, int cycleDays) {
        List<SlotRow> ordered = new ArrayList<>(slotRows);
        ordered.sort(GRID_ORDER);

        List<Timeslot> slots = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            SlotRow row = ordered.get(index);
            if (row.dayInCycle() >= cycleDays) {
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
