package com.planning.domain.time;

import com.planning.domain.model.HorizonKind;
import com.planning.domain.model.Period;
import com.planning.domain.model.PeriodBucket;
import com.planning.domain.model.Timeslot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;

/**
 * Le point de bascule entre modes d'horizon (§14.1).
 *
 * <p>DÉCISION : l'interface existe dès le premier jour avec une seule
 * implémentation. Le coût est nul aujourd'hui ; l'ajouter plus tard imposerait de
 * reprendre la compilation, les contraintes et les données de production.
 */
public sealed interface TimeHorizon permits TimeHorizon.Cyclic, TimeHorizon.Dated {

    HorizonKind kind();

    List<Timeslot> slots();

    TimeGrid grid();

    GapPolicy gapPolicy();

    int bucketOf(Timeslot slot, Period period);

    boolean sameRecurrence(Timeslot a, Timeslot b);

    boolean supports(Period period);

    /** Date réelle du créneau — vide en mode cyclique. */
    Optional<LocalDate> dateOf(int slotIndex);

    default String labelOf(Period period, int index) {
        return kind() == HorizonKind.DATED ? TemporalBuckets.label(period, index) : CycleBuckets.label(period, index);
    }

    default List<PeriodBucket> periodBuckets() {
        return Arrays.stream(Period.values()).filter(this::supports)
                .flatMap(period -> slots().stream().map(slot -> bucketOf(slot, period)).distinct()
                        .map(index -> new PeriodBucket(period, index, labelOf(period, index))))
                .toList();
    }

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

    record Dated(LocalDate from, LocalDate to, List<Timeslot> slots, TimeGrid grid, GapPolicy gapPolicy)
            implements TimeHorizon {
        public Dated { slots = List.copyOf(slots); }
        @Override public HorizonKind kind() { return HorizonKind.DATED; }
        @Override public int bucketOf(Timeslot slot, Period period) { return TemporalBuckets.bucketOf(slot, period); }
        @Override public boolean sameRecurrence(Timeslot a, Timeslot b) {
            return a.weekday() == b.weekday() && a.start().equals(b.start());
        }
        @Override public boolean supports(Period period) { return true; }
        @Override public Optional<LocalDate> dateOf(int slotIndex) { return Optional.of(slotAt(slotIndex).date()); }
    }
}
