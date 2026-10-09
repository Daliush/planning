package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.Schemas;
import com.planning.domain.constraint.Streams;
import com.planning.domain.exception.IncompatibleConfigException;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.HorizonKind;
import com.planning.domain.model.Level;
import com.planning.domain.model.Resource;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.Violation;
import com.planning.domain.time.TemporalBuckets;
import com.planning.domain.time.TimeHorizon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Équilibre les week-ends touchés ; samedi et dimanche de la même semaine comptent une fois. */
public final class WeekendFairnessConstraint implements ConstraintPlugin {
    public static final String TYPE = "fairness.weekend";
    public record Params() { }

    private static Set<Integer> weekends(ResourceBooking booking) {
        Set<Integer> weeks = new HashSet<>();
        TemporalBuckets.occupiedDates(booking.startDateTime(), booking.endDateTime()).stream()
                .filter(TemporalBuckets::isWeekend).forEach(date -> weeks.add(TemporalBuckets.weekIndex(date)));
        return weeks;
    }

    private static int weekendCount(List<ResourceBooking> bookings) {
        Set<Integer> weeks = new HashSet<>();
        bookings.forEach(booking -> weeks.addAll(weekends(booking)));
        return weeks.size();
    }

    private static int penalty(List<ResourceBooking> bookings, int ruleWeight) {
        long count = weekendCount(bookings);
        return Math.toIntExact(Math.multiplyExact(Math.multiplyExact(count, count), ruleWeight));
    }

    @Override public String type() { return TYPE; }
    @Override public String label() { return "Équité des week-ends"; }
    @Override public String description() {
        return "Répartir les week-ends travaillés entre les ressources visées. Samedi et dimanche d'une même semaine "
                + "comptent un seul week-end, y compris pour un poste de nuit. Chaque ressource contribue au carré de son compte.";
    }
    @Override public Level defaultLevel() { return Level.SOFT; }
    @Override public Class<?> paramsType() { return Params.class; }
    @Override public JsonNode paramSchema() { return Schemas.empty(); }
    @Override public Set<HorizonKind> horizons() { return Set.of(HorizonKind.DATED); }
    @Override public void validate(ConstraintConfig rule, TimeHorizon horizon) {
        if (horizon.kind() != HorizonKind.DATED) {
            throw new IncompatibleConfigException("L'équité des week-ends exige un horizon daté.");
        }
    }

    @Override public Constraint build(ConstraintFactory factory) {
        return Streams.scopedBookings(factory, TYPE)
                .filter((rule, booking) -> booking.getStart().date() != null && !weekends(booking).isEmpty())
                .groupBy((rule, booking) -> rule, (rule, booking) -> booking.getResource(),
                        ConstraintCollectors.toList((rule, booking) -> booking))
                .penalize(HardSoftScore.ONE_SOFT,
                        (rule, resource, bookings) -> penalty(bookings, rule.weight()))
                .asConstraint(TYPE);
    }

    @Override public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        Map<Resource, List<ResourceBooking>> members = new LinkedHashMap<>();
        Streams.placedBookings(solution.getBookings(), rule)
                .filter(booking -> booking.getStart().date() != null && !weekends(booking).isEmpty())
                .forEach(booking -> members.computeIfAbsent(booking.getResource(), key -> new ArrayList<>()).add(booking));
        List<Violation> violations = new ArrayList<>();
        members.forEach((resource, bookings) -> violations.add(new Violation(TYPE, rule.level(), penalty(bookings, rule.weight()),
                "%s travaille pendant %d week-end(s) sur l'horizon."
                        .formatted(resource.label(), weekendCount(bookings)),
                List.of(resource.id()), bookings.stream().map(booking -> booking.getActivity().getDemandId()).distinct().toList(),
                Map.of("weekends", weekendCount(bookings)))));
        return violations;
    }
}
