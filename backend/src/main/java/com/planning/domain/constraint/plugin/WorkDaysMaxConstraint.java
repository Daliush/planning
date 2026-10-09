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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Limite les séries de dates calendaires occupées, sans compter deux fois un jour. */
public final class WorkDaysMaxConstraint implements ConstraintPlugin {
    public static final String TYPE = "work.days_max";
    public record Params(int maxDays) { }
    private record Run(LocalDate from, LocalDate to, int days) { }

    private static Params params(ConstraintConfig rule) { return rule.paramsAs(Params.class); }

    private static List<Run> exceededRuns(List<ResourceBooking> bookings, Params params) {
        Set<LocalDate> dates = new TreeSet<>();
        bookings.forEach(booking -> dates.addAll(TemporalBuckets.occupiedDates(booking.startDateTime(), booking.endDateTime())));
        List<Run> exceeded = new ArrayList<>();
        LocalDate from = null;
        LocalDate previous = null;
        int days = 0;
        for (LocalDate date : dates) {
            if (previous != null && !TemporalBuckets.consecutiveDates(previous, date)) {
                if (days > params.maxDays()) {
                    exceeded.add(new Run(from, previous, days));
                }
                days = 0;
            }
            if (days == 0) {
                from = date;
            }
            days++;
            previous = date;
        }
        if (days > params.maxDays()) {
            exceeded.add(new Run(from, previous, days));
        }
        return exceeded;
    }

    private static int penalty(Run run, Params params, int ruleWeight) {
        return Math.multiplyExact(run.days() - params.maxDays(), ruleWeight);
    }

    private static int totalPenalty(List<ResourceBooking> bookings, Params params, int ruleWeight) {
        return exceededRuns(bookings, params).stream().mapToInt(run -> penalty(run, params, ruleWeight))
                .reduce(0, Math::addExact);
    }

    @Override public String type() { return TYPE; }
    @Override public String label() { return "Jours de travail consécutifs"; }
    @Override public String description() {
        return "Limiter le nombre de dates consécutives travaillées. Un poste de nuit compte chaque date occupée ; "
                + "plusieurs postes le même jour comptent une seule fois. Seules les dates de l'horizon sont connues.";
    }
    @Override public Level defaultLevel() { return Level.HARD; }
    @Override public Class<?> paramsType() { return Params.class; }
    @Override public Set<HorizonKind> horizons() { return Set.of(HorizonKind.DATED); }
    @Override public JsonNode paramSchema() {
        return Schemas.properties().add("maxDays", Schemas.integer("Maximum de dates de travail consécutives", 1))
                .required("maxDays");
    }
    @Override public void validate(ConstraintConfig rule, TimeHorizon horizon) {
        if (horizon.kind() != HorizonKind.DATED || params(rule).maxDays() < 1) {
            throw new IncompatibleConfigException("La limite de jours consécutifs exige un horizon daté et un maximum positif.");
        }
    }

    @Override public Constraint build(ConstraintFactory factory) {
        return Streams.scopedBookings(factory, TYPE)
                .filter((rule, booking) -> booking.getStart().date() != null)
                .groupBy((rule, booking) -> rule, (rule, booking) -> booking.getResource(),
                        ConstraintCollectors.toList((rule, booking) -> booking))
                .filter((rule, resource, bookings) -> totalPenalty(bookings, params(rule), rule.weight()) > 0)
                .penalize(HardSoftScore.ONE_HARD,
                        (rule, resource, bookings) -> totalPenalty(bookings, params(rule), rule.weight()))
                .asConstraint(TYPE);
    }

    @Override public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        Map<Resource, List<ResourceBooking>> members = new LinkedHashMap<>();
        Streams.placedBookings(solution.getBookings(), rule)
                .filter(booking -> booking.getStart().date() != null)
                .forEach(booking -> members.computeIfAbsent(booking.getResource(), key -> new ArrayList<>()).add(booking));
        List<Violation> violations = new ArrayList<>();
        members.forEach((resource, bookings) -> exceededRuns(bookings, params(rule)).forEach(run ->
                violations.add(new Violation(TYPE, rule.level(), penalty(run, params(rule), rule.weight()),
                        "%s travaille %d dates consécutives du %s au %s, au lieu de %d maximum."
                                .formatted(resource.label(), run.days(), run.from(), run.to(), params(rule).maxDays()),
                        List.of(resource.id()), bookings.stream()
                                .filter(booking -> TemporalBuckets.occupiedDates(booking.startDateTime(), booking.endDateTime()).stream()
                                        .anyMatch(date -> !date.isBefore(run.from()) && !date.isAfter(run.to())))
                                .map(booking -> booking.getActivity().getDemandId()).distinct().toList(),
                        Map.of("days", run.days(), "maxDays", params(rule).maxDays(),
                                "from", run.from().toString(), "to", run.to().toString())))));
        return violations;
    }
}
