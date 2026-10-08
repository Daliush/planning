package com.planning.domain.time;

import com.planning.domain.model.HorizonKind;
import com.planning.domain.model.Period;
import com.planning.domain.model.Timeslot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Le point de bascule entre modes d'horizon (§14.1).
 *
 * <p>DÉCISION : l'interface existe dès le premier jour avec une seule
 * implémentation. Le coût est nul aujourd'hui ; l'ajouter plus tard imposerait de
 * reprendre la compilation, les contraintes et les données de production.
 */
public sealed interface TimeHorizon permits TimeHorizon.Cyclic {

    HorizonKind kind();

    List<Timeslot> slots();

    TimeGrid grid();

    GapPolicy gapPolicy();

    int bucketOf(Timeslot slot, Period period);

    boolean sameRecurrence(Timeslot a, Timeslot b);

    boolean supports(Period period);

    /** Date réelle du créneau — vide en mode cyclique. */
    Optional<LocalDate> dateOf(int slotIndex);

    default Timeslot slotAt(int index) {
        return slots().get(index);
    }

    default int size() {
        return slots().size();
    }

    record Cyclic(int cycleDays, List<Timeslot> slots, TimeGrid grid, GapPolicy gapPolicy)
            implements TimeHorizon {

        public Cyclic {
            slots = List.copyOf(slots);
        }

        @Override
        public HorizonKind kind() {
            return HorizonKind.CYCLIC;
        }

        @Override
        public int bucketOf(Timeslot slot, Period period) {
            return CycleBuckets.bucketOf(slot, period);
        }

        @Override
        public boolean sameRecurrence(Timeslot a, Timeslot b) {
            return CycleBuckets.sameRecurrence(a, b);
        }

        @Override
        public boolean supports(Period period) {
            return CycleBuckets.supports(period);
        }

        @Override
        public Optional<LocalDate> dateOf(int slotIndex) {
            return Optional.empty();
        }
    }

    // V2 — record Dated(LocalDate from, LocalDate to, …) implements TimeHorizon { … }
}
