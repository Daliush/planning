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
import com.planning.domain.model.Period;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.Violation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * « Pas plus de 4 h d'affilée sans coupure. »
 *
 * <p>PIÈGE §6.7 : la contiguïté se teste sur les <strong>heures réelles</strong>.
 * Deux ordinaux successifs peuvent être séparés par une récréation, et la version
 * naïve fondée sur {@code ordinal + 1} produirait des résultats faux uniquement
 * autour des pauses. {@code toleranceMinutes} définit ce qui rompt réellement la
 * série.
 */
public final class SequenceConsecutiveMaxConstraint implements ConstraintPlugin {

    public static final String TYPE = "sequence.consecutive_max";

    public record Params(int maxConsecutiveMinutes, int toleranceMinutes) {
    }

    // ═══ LOGIQUE MÉTIER — ÉCRITE UNE SEULE FOIS ═══

    private static Params params(ConstraintConfig rule) {
        return rule.paramsAs(Params.class);
    }

    private static int longestRun(List<ResourceBooking> sameDay, Params params) {
        return Bookings.longestRunMinutes(sameDay, params.toleranceMinutes());
    }

    private static int excess(List<ResourceBooking> sameDay, Params params) {
        return Math.max(0, longestRun(sameDay, params) - params.maxConsecutiveMinutes());
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
        return "Enchaînement maximal";
    }

    @Override
    public String description() {
        return "Limiter la durée travaillée d'affilée sans interruption réelle.";
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
                .add("maxConsecutiveMinutes", Schemas.minutes("Durée maximale d'affilée", 1))
                .add("toleranceMinutes", Schemas.minutes(
                        "Interruption en deçà de laquelle la série n'est pas rompue", 0))
                .required("maxConsecutiveMinutes", "toleranceMinutes");
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
                    "%s enchaîne %s sans coupure %s, au-delà des %s souhaitées."
                            .formatted(key.resource().label(),
                                    Bookings.hoursMinutes(longestRun(dayBookings, params)),
                                    solution.getHorizon().labelOf(Period.DAY,
                                            solution.getHorizon().bucketOf(dayBookings.getFirst().getStart(), Period.DAY)),
                                    Bookings.hoursMinutes(params.maxConsecutiveMinutes())),
                    List.of(key.resource().id()),
                    dayBookings.stream().map(b -> b.getActivity().getDemandId()).distinct().toList(),
                    Map.of("longestRunMinutes", longestRun(dayBookings, params),
                            "maxConsecutiveMinutes", params.maxConsecutiveMinutes(),
                            "dayInCycle", key.dayInCycle())));
        });
        return violations;
    }
}
