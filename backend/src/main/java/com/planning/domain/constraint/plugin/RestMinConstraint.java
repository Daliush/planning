package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.constraint.Bookings;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Repos entre journées de début de poste ; une nuit garde sa vraie heure de fin. */
public final class RestMinConstraint implements ConstraintPlugin {
    public static final String TYPE = "rest.min";
    public record Params(int minMinutes) { }
    private record Workday(LocalDateTime start, LocalDateTime end, List<UUID> demands) { }
    private record RestGap(Workday previous, Workday next, int restMinutes, int missingMinutes) { }

    private static Params params(ConstraintConfig rule) {
        return rule.paramsAs(Params.class);
    }

    private static List<RestGap> missingRests(List<ResourceBooking> bookings, Params params) {
        Map<LocalDate, List<ResourceBooking>> days = new TreeMap<>();
        for (ResourceBooking booking : bookings) {
            days.computeIfAbsent(TemporalBuckets.startDateOf(booking.getStart()), key -> new ArrayList<>())
                    .add(booking);
        }
        List<RestGap> gaps = new ArrayList<>();
        Workday previous = null;
        for (List<ResourceBooking> dailyBookings : days.values()) {
            Workday current = new Workday(
                    dailyBookings.stream().map(ResourceBooking::startDateTime).min(LocalDateTime::compareTo).orElseThrow(),
                    dailyBookings.stream().map(ResourceBooking::endDateTime).max(LocalDateTime::compareTo).orElseThrow(),
                    dailyBookings.stream().map(booking -> booking.getActivity().getDemandId()).distinct().toList());
            if (previous != null) {
                int restMinutes = TemporalBuckets.restMinutes(previous.end(), current.start());
                int missing = Math.max(0, params.minMinutes() - restMinutes);
                if (missing > 0) {
                    gaps.add(new RestGap(previous, current, restMinutes, missing));
                }
                // Une longue occupation peut couvrir plusieurs dates de début.
                if (previous.end().isAfter(current.end())) {
                    List<UUID> demands = new ArrayList<>(previous.demands());
                    demands.addAll(current.demands());
                    current = new Workday(current.start(), previous.end(), demands.stream().distinct().toList());
                }
            }
            previous = current;
        }
        return gaps;
    }

    private static int penalty(RestGap gap, int ruleWeight) {
        return Math.multiplyExact(gap.missingMinutes(), ruleWeight);
    }

    private static int totalPenalty(List<ResourceBooking> bookings, Params params, int ruleWeight) {
        return missingRests(bookings, params).stream().mapToInt(gap -> penalty(gap, ruleWeight))
                .reduce(0, Math::addExact);
    }

    @Override public String type() { return TYPE; }
    @Override public String label() { return "Repos minimal entre journées"; }
    @Override public String description() {
        return "Préserver un repos minimal entre la fin d'une journée de travail et le début de la suivante, "
                + "y compris lorsque le travail finit après minuit. Les postes démarrant le même jour forment une journée.";
    }
    @Override public Level defaultLevel() { return Level.HARD; }
    @Override public Class<?> paramsType() { return Params.class; }
    @Override public Set<HorizonKind> horizons() { return Set.of(HorizonKind.DATED); }
    @Override public JsonNode paramSchema() {
        return Schemas.properties().add("minMinutes", Schemas.minutes("Repos minimal entre deux journées, zéro désactive la borne", 0))
                .required("minMinutes");
    }
    @Override public void validate(ConstraintConfig rule, TimeHorizon horizon) {
        if (horizon.kind() != HorizonKind.DATED || params(rule).minMinutes() < 0) {
            throw new IncompatibleConfigException("Le repos minimal exige un horizon daté et une durée positive ou nulle.");
        }
    }

    @Override public Constraint build(ConstraintFactory factory) {
        return Streams.scopedBookings(factory, TYPE)
                .filter((rule, booking) -> booking.getStart().date() != null)
                .groupBy((rule, booking) -> rule,
                        (rule, booking) -> booking.getResource(),
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
        members.forEach((resource, bookings) -> missingRests(bookings, params(rule)).forEach(gap -> {
            List<UUID> demands = new ArrayList<>(gap.previous().demands());
            demands.addAll(gap.next().demands());
            violations.add(new Violation(TYPE, rule.level(), penalty(gap, rule.weight()),
                    "%s ne dispose que de %s de repos entre le %s à %s et le %s à %s, au lieu de %s."
                            .formatted(resource.label(), Bookings.hoursMinutes(gap.restMinutes()),
                                    gap.previous().end().toLocalDate(), gap.previous().end().toLocalTime(),
                                    gap.next().start().toLocalDate(), gap.next().start().toLocalTime(),
                                    Bookings.hoursMinutes(params(rule).minMinutes())),
                    List.of(resource.id()), demands.stream().distinct().toList(),
                    Map.of("restMinutes", gap.restMinutes(), "minMinutes", params(rule).minMinutes(),
                            "previousEnd", gap.previous().end().toString(), "nextStart", gap.next().start().toString())));
        }));
        return violations;
    }
}
