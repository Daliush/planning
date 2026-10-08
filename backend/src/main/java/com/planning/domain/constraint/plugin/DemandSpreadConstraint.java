package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.DemandDayKey;
import com.planning.domain.constraint.Schemas;
import com.planning.domain.constraint.ScopeTarget;
import com.planning.domain.constraint.Streams;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.HorizonKind;
import com.planning.domain.model.Level;
import com.planning.domain.model.ScheduledActivity;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.Violation;
import com.planning.domain.time.CycleBuckets;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * « Étaler les 4 séances d'un cours sur des jours différents. »
 * <strong>Sans ce plugin, rien n'empêche les 4 séances de tomber le même jour.</strong>
 *
 * <p>Cyclique uniquement : en horizon daté, {@code dayInCycle} deviendrait le jour
 * de l'année, deux séances du même cours en mars et en octobre ne seraient jamais
 * pénalisées, et la contrainte deviendrait <em>muette</em> — elle ne planterait
 * pas, elle cesserait simplement de détecter quoi que ce soit (§14.6).
 *
 * <p>Le comptage se fait par {@code groupBy} plutôt que par
 * {@code forEachUniquePair}, qui deviendrait quadratique sur un horizon long.
 */
public final class DemandSpreadConstraint implements ConstraintPlugin {

    public static final String TYPE = "demand.spread";

    public record Params(int maxPerDay) {
    }

    // ═══ LOGIQUE MÉTIER — ÉCRITE UNE SEULE FOIS ═══

    private static Params params(ConstraintConfig rule) {
        return rule.paramsAs(Params.class);
    }

    private static DemandDayKey keyOf(ScheduledActivity activity) {
        return new DemandDayKey(activity.getDemandId(), activity.dayInCycle());
    }

    private static boolean violates(int sameDayCount, Params params) {
        return sameDayCount > params.maxPerDay();
    }

    private static int penalty(int sameDayCount, Params params, int ruleWeight) {
        return ruleWeight * (sameDayCount - params.maxPerDay());
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String label() {
        return "Étalement des séances";
    }

    @Override
    public String description() {
        return "Limiter le nombre de séances d'une même demande tombant le même jour du cycle.";
    }

    @Override
    public Level defaultLevel() {
        return Level.SOFT;
    }

    @Override
    public ScopeTarget scopeTarget() {
        // Le scope parle de la demande — matière, niveau — pas de la ressource.
        return ScopeTarget.DEMAND;
    }

    @Override
    public Class<?> paramsType() {
        return Params.class;
    }

    @Override
    public JsonNode paramSchema() {
        return Schemas.properties()
                .add("maxPerDay", Schemas.integer("Séances tolérées le même jour du cycle", 1))
                .required("maxPerDay");
    }

    @Override
    public Set<HorizonKind> horizons() {
        return EnumSet.of(HorizonKind.CYCLIC);
    }

    // ═══ TRAVERSÉE A ═══

    @Override
    public Constraint build(ConstraintFactory factory) {
        return Streams.scopedActivities(factory, TYPE)
                .groupBy((rule, activity) -> rule,
                        (rule, activity) -> keyOf(activity),
                        ConstraintCollectors.countBi())
                .filter((rule, key, count) -> violates(count, params(rule)))
                .penalize(HardSoftScore.ONE_SOFT,
                        (rule, key, count) -> penalty(count, params(rule), rule.weight()))
                .asConstraint(TYPE);
    }

    // ═══ TRAVERSÉE B ═══

    @Override
    public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        Params params = params(rule);
        Map<DemandDayKey, List<ScheduledActivity>> byDay = new LinkedHashMap<>();
        Streams.scopedActivities(solution.getActivities(), rule)
                .forEach(activity -> byDay.computeIfAbsent(keyOf(activity), key -> new ArrayList<>())
                        .add(activity));

        List<Violation> violations = new ArrayList<>();
        byDay.forEach((key, activities) -> {
            if (!violates(activities.size(), params)) {
                return;
            }
            violations.add(new Violation(
                    TYPE,
                    rule.level(),
                    penalty(activities.size(), params, rule.weight()),
                    "« %s » a %d séances le même jour (%s) alors que %d au maximum sont souhaitées."
                            .formatted(activities.getFirst().getDemandLabel(), activities.size(),
                                    CycleBuckets.dayLabel(key.dayInCycle()), params.maxPerDay()),
                    List.of(),
                    List.of(key.demandId()),
                    Map.of("sameDayCount", activities.size(),
                            "maxPerDay", params.maxPerDay(),
                            "dayInCycle", key.dayInCycle())));
        });
        return violations;
    }
}
