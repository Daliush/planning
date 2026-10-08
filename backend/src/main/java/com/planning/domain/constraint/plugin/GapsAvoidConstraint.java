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
 * Un seul plugin sert les trous des élèves <strong>et</strong> ceux des
 * professeurs. La différence d'importance passe par le poids ② : « les trous des
 * élèves comptent quatre fois plus » s'écrit avec deux lignes de configuration de
 * poids 4 et 1, pas avec deux plugins (§13.4).
 *
 * <p>{@code toleranceMinutes} absorbe la récréation : sans lui, chaque journée
 * normale d'un collège compterait des dizaines de minutes de « trou ».
 */
public final class GapsAvoidConstraint implements ConstraintPlugin {

    public static final String TYPE = "gaps.avoid";

    public record Params(int toleranceMinutes) {
    }

    // ═══ LOGIQUE MÉTIER — ÉCRITE UNE SEULE FOIS ═══

    private static Params params(ConstraintConfig rule) {
        return rule.paramsAs(Params.class);
    }

    private static int gapMinutes(List<ResourceBooking> sameDay, Params params) {
        return Bookings.gapMinutes(sameDay, params.toleranceMinutes());
    }

    private static int penalty(int gapMinutes, int ruleWeight) {
        return ruleWeight * gapMinutes;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String label() {
        return "Éviter les trous";
    }

    @Override
    public String description() {
        return "Pénaliser les interruptions dans la journée d'une ressource, au-delà d'une tolérance.";
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
                .add("toleranceMinutes", Schemas.minutes(
                        "Interruption tolérée sans pénalité, typiquement la récréation", 0))
                .required("toleranceMinutes");
    }

    // ═══ TRAVERSÉE A ═══

    @Override
    public Constraint build(ConstraintFactory factory) {
        return Streams.scopedBookings(factory, TYPE)
                .groupBy((rule, booking) -> rule,
                        (rule, booking) -> new ResourceDayKey(booking.getResource(), booking.dayInCycle()),
                        ConstraintCollectors.toList((rule, booking) -> booking))
                .filter((rule, key, dayBookings) -> gapMinutes(dayBookings, params(rule)) > 0)
                .penalize(HardSoftScore.ONE_SOFT,
                        (rule, key, dayBookings) ->
                                penalty(gapMinutes(dayBookings, params(rule)), rule.weight()))
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
            int gap = gapMinutes(dayBookings, params);
            if (gap <= 0) {
                return;
            }
            violations.add(new Violation(
                    TYPE,
                    rule.level(),
                    penalty(gap, rule.weight()),
                    "%s a %s de trou dans sa journée %s."
                            .formatted(key.resource().label(), Bookings.hoursMinutes(gap),
                                    CycleBuckets.dayLabel(key.dayInCycle())),
                    List.of(key.resource().id()),
                    dayBookings.stream().map(b -> b.getActivity().getDemandId()).distinct().toList(),
                    Map.of("gapMinutes", gap,
                            "dayInCycle", key.dayInCycle(),
                            "toleranceMinutes", params.toleranceMinutes())));
        });
        return violations;
    }
}
