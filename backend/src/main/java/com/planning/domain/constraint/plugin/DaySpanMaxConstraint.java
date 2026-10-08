package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.constraint.Bookings;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.ResourceDayKey;
import com.planning.domain.constraint.Schemas;
import com.planning.domain.constraint.Streams;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Level;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.Violation;
import com.planning.domain.time.CycleBuckets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * L'amplitude de la journée : de la première minute travaillée à la dernière,
 * pauses comprises.
 *
 * <p>Complément de {@code gaps.avoid} et non doublon : une journée sans trou peut
 * être trop longue, une journée courte peut être trouée. Les deux règles se
 * pondèrent séparément.
 */
public final class DaySpanMaxConstraint implements ConstraintPlugin {

    public static final String TYPE = "day.span_max";

    public record Params(int maxSpanMinutes) {
    }

    // ═══ LOGIQUE MÉTIER — ÉCRITE UNE SEULE FOIS ═══

    private static Params params(ConstraintConfig rule) {
        return rule.paramsAs(Params.class);
    }

    private static int excess(List<ResourceBooking> sameDay, Params params) {
        return Math.max(0, Bookings.spanMinutes(sameDay) - params.maxSpanMinutes());
    }

    private static int penalty(int excessMinutes, int ruleWeight) {
        return ruleWeight * excessMinutes;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String label() {
        return "Amplitude journalière";
    }

    @Override
    public String description() {
        return "Plafonner l'écart entre la première et la dernière séance d'une journée.";
    }

    @Override
    public Level defaultLevel() {
        return Level.SOFT;
    }

    @Override
    public Class<?> paramsType() {
        return Params.class;
    }

    @Override
    public JsonNode paramSchema() {
        return Schemas.properties()
                .add("maxSpanMinutes", Schemas.minutes("Amplitude maximale d'une journée", 1))
                .required("maxSpanMinutes");
    }

    // ═══ TRAVERSÉE A ═══

    @Override
    public Constraint build(ConstraintFactory factory) {
        return Streams.scopedBookings(factory, TYPE)
                .groupBy((rule, booking) -> rule,
                        (rule, booking) -> new ResourceDayKey(booking.getResource(), booking.dayInCycle()),
                        ConstraintCollectors.toList((rule, booking) -> booking))
                .filter((rule, key, dayBookings) -> excess(dayBookings, params(rule)) > 0)
                .penalize(HardSoftScore.ONE_SOFT,
                        (rule, key, dayBookings) -> penalty(excess(dayBookings, params(rule)), rule.weight()))
                .asConstraint(TYPE);
    }

    // ═══ TRAVERSÉE B ═══

    @Override
    public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        Params params = params(rule);
        Map<ResourceDayKey, List<ResourceBooking>> byDay = new LinkedHashMap<>();
        Streams.placedBookings(solution.getBookings(), rule).forEach(booking -> byDay
                .computeIfAbsent(new ResourceDayKey(booking.getResource(), booking.dayInCycle()),
                        key -> new ArrayList<>())
                .add(booking));

        List<Violation> violations = new ArrayList<>();
        byDay.forEach((key, dayBookings) -> {
            int over = excess(dayBookings, params);
            if (over <= 0) {
                return;
            }
            violations.add(new Violation(
                    TYPE,
                    rule.level(),
                    penalty(over, rule.weight()),
                    "La journée %s de %s s'étend sur %s, soit %s de plus que l'amplitude souhaitée."
                            .formatted(CycleBuckets.dayLabel(key.dayInCycle()), key.resource().label(),
                                    Bookings.hoursMinutes(Bookings.spanMinutes(dayBookings)),
                                    Bookings.hoursMinutes(over)),
                    List.of(key.resource().id()),
                    dayBookings.stream().map(b -> b.getActivity().getDemandId()).distinct().toList(),
                    Map.of("spanMinutes", Bookings.spanMinutes(dayBookings),
                            "maxSpanMinutes", params.maxSpanMinutes(),
                            "dayInCycle", key.dayInCycle())));
        });
        return violations;
    }
}
