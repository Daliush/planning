package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.constraint.ConstraintPlugin;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * « Le cours d'anglais tombe au même moment chaque semaine du cycle. »
 *
 * <p>Nécessaire dès que {@code cycle_days > 7} : un cours hebdomadaire produit
 * alors une séance par semaine du cycle, et rien ne garantit qu'elles tombent au
 * même moment. C'est le prix d'entrée du cycle long ; en contrepartie, les cours
 * en quinzaine ne portent pas cette règle dans leur scope et restent libres de
 * différer entre semaine A et semaine B — ce qui est précisément l'objectif d'un
 * cycle de 14 jours.
 *
 * <p>La séance de référence est la première dans l'ordre des créneaux : le tri
 * est déterministe et identique dans les deux traversées, sans quoi le rapport
 * pourrait désigner d'autres séances que celles comptées par le solveur.
 */
public final class WeeklyRegularityConstraint implements ConstraintPlugin {

    public static final String TYPE = "time.weekly_regularity";

    public record Params() {
    }

    // ═══ LOGIQUE MÉTIER — ÉCRITE UNE SEULE FOIS ═══

    private static final Comparator<ScheduledActivity> BY_START =
            Comparator.comparingInt(ScheduledActivity::startIndex);

    /** Nombre de séances décalées par rapport à la référence du cours. */
    private static int irregularCount(List<ScheduledActivity> sameDemand) {
        if (sameDemand.size() < 2) {
            return 0;
        }
        List<ScheduledActivity> sorted = new ArrayList<>(sameDemand);
        sorted.sort(BY_START);
        ScheduledActivity reference = sorted.getFirst();
        return (int) sorted.stream()
                .skip(1)
                .filter(activity -> !CycleBuckets.sameRecurrence(reference.getStart(), activity.getStart()))
                .count();
    }

    private static int penalty(int irregular, int ruleWeight) {
        return ruleWeight * irregular;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String label() {
        return "Régularité hebdomadaire";
    }

    @Override
    public String description() {
        return "Placer les séances d'une même demande au même jour et à la même heure chaque semaine du cycle.";
    }

    @Override
    public Level defaultLevel() {
        return Level.HARD;
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
        return Schemas.empty();
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
                        (rule, activity) -> activity.getDemandId(),
                        ConstraintCollectors.toList((rule, activity) -> activity))
                .filter((rule, demandId, activities) -> irregularCount(activities) > 0)
                .penalize(HardSoftScore.ONE_HARD,
                        (rule, demandId, activities) -> penalty(irregularCount(activities), rule.weight()))
                .asConstraint(TYPE);
    }

    // ═══ TRAVERSÉE B ═══

    @Override
    public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        Map<UUID, List<ScheduledActivity>> byDemand = new LinkedHashMap<>();
        Streams.scopedActivities(solution.getActivities(), rule)
                .forEach(activity -> byDemand
                        .computeIfAbsent(activity.getDemandId(), key -> new ArrayList<>())
                        .add(activity));

        List<Violation> violations = new ArrayList<>();
        byDemand.forEach((demandId, activities) -> {
            int irregular = irregularCount(activities);
            if (irregular <= 0) {
                return;
            }
            List<ScheduledActivity> sorted = new ArrayList<>(activities);
            sorted.sort(BY_START);
            ScheduledActivity reference = sorted.getFirst();
            violations.add(new Violation(
                    TYPE,
                    rule.level(),
                    penalty(irregular, rule.weight()),
                    "« %s » n'a pas le même horaire toutes les semaines : %d séance(s) s'écartent de la référence %s à %s."
                            .formatted(reference.getDemandLabel(), irregular,
                                    CycleBuckets.dayLabel(reference.dayInCycle()),
                                    reference.getStart().start()),
                    List.of(),
                    List.of(demandId),
                    Map.of("irregularCount", irregular,
                            "referenceDay", reference.dayInCycle(),
                            "referenceStart", reference.getStart().start().toString())));
        });
        return violations;
    }
}
