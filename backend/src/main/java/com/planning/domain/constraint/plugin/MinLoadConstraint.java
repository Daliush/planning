package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.constraint.AggregationScope;
import com.planning.domain.constraint.Bookings;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.Schemas;
import com.planning.domain.constraint.Streams;
import com.planning.domain.exception.IncompatibleConfigException;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Level;
import com.planning.domain.model.Period;
import com.planning.domain.model.PeriodBucket;
import com.planning.domain.model.Resource;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.Violation;
import com.planning.domain.time.TimeHorizon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plancher de charge : « un titulaire doit assurer au moins 6 h dans le cycle ».
 *
 * <p><strong>Deux pipelines, une seule règle.</strong> Une ressource sans aucune
 * affectation n'apparaît dans aucun panier d'agrégation : le pipeline principal,
 * qui part des affectations, ne peut structurellement pas la voir. Or c'est
 * précisément le cas qui compte — un enseignant à zéro heure est le symptôme que
 * cette règle existe pour attraper.
 *
 * <p>Les Constraint Streams n'offrant pas de jointure externe, un second pipeline
 * part des <em>ressources et des périodes</em> et retient chaque panier qu'aucune
 * affectation placée ne mentionne. Les deux partagent le poids ①, la formule de pénalité et
 * l'identifiant rapporté : ce qui se dédouble est la traversée, jamais la règle.
 * C'est aussi ce qui rend cette liste de ressources indispensable sur la solution
 * (§8.3) — sans elle, une charge nulle serait invisible.
 */
public final class MinLoadConstraint implements ConstraintPlugin {

    public static final String TYPE = "load.min";

    /** Nom Timefold du pipeline auxiliaire. Il partage le poids ① de {@link #TYPE}. */
    private static final String NEVER_USED = "load.min.never_used";

    public record Params(int minMinutes, Period period) {
    }

    // ═══ LOGIQUE MÉTIER — ÉCRITE UNE SEULE FOIS ═══

    private static Params params(ConstraintConfig rule) {
        return rule.paramsAs(Params.class);
    }

    private static AggregationScope scopeOf(ResourceBooking booking, PeriodBucket bucket) {
        return new AggregationScope(booking.getResource(), bucket.period(), bucket.index());
    }

    private static boolean violates(int totalMinutes, Params params) {
        return totalMinutes < params.minMinutes();
    }

    private static int penalty(int totalMinutes, Params params, int ruleWeight) {
        return ruleWeight * (params.minMinutes() - totalMinutes);
    }

    /** Un panier vide manque l'intégralité du plancher. */
    private static int emptyPenalty(Params params, int ruleWeight) {
        return penalty(0, params, ruleWeight);
    }

    private static boolean belongsTo(ResourceBooking booking, PeriodBucket bucket) {
        return booking.isPlaced() && Bookings.minutesIn(booking, bucket) > 0;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String label() {
        return "Charge minimale";
    }

    @Override
    public String description() {
        return "Garantir un volume horaire plancher à chaque ressource visée, "
                + "sur chaque période de l'horizon, y compris celles sans aucune affectation.";
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
                .add("minMinutes", Schemas.minutes("Plancher sur la période", 1))
                .add("period", Schemas.period("Découpage du quota"))
                .required("minMinutes", "period");
    }

    @Override
    public void validate(ConstraintConfig config, TimeHorizon horizon) {
        Period period = params(config).period();
        if (!horizon.supports(period)) {
            throw new IncompatibleConfigException(
                    "La période %s n'est pas disponible sur un horizon %s."
                            .formatted(period, horizon.kind()));
        }
    }

    // ═══ TRAVERSÉE A ═══

    @Override
    public Constraint build(ConstraintFactory factory) {
        return Streams.scopedBookings(factory, TYPE)
                .join(PeriodBucket.class,
                        Joiners.equal((rule, booking) -> params(rule).period(), PeriodBucket::period),
                        Joiners.filtering((rule, booking, bucket) -> belongsTo(booking, bucket)))
                .groupBy((rule, booking, bucket) -> rule,
                        (rule, booking, bucket) -> scopeOf(booking, bucket),
                        ConstraintCollectors.sum((rule, booking, bucket) -> Bookings.minutesIn(booking, bucket)))
                .filter((rule, scope, total) -> violates(total, params(rule)))
                .penalize(HardSoftScore.ONE_SOFT,
                        (rule, scope, total) -> penalty(total, params(rule), rule.weight()))
                .asConstraint(TYPE);
    }

    /** Chaque période vide est un manque, même si la ressource travaille ailleurs. */
    private Constraint buildNeverUsed(ConstraintFactory factory) {
        return factory.forEach(ConstraintConfig.class)
                .filter(rule -> rule.type().equals(TYPE))
                .join(Resource.class,
                        Joiners.filtering((rule, resource) -> rule.scope().matches(resource)))
                .join(PeriodBucket.class,
                        Joiners.equal((rule, resource) -> params(rule).period(), PeriodBucket::period))
                .ifNotExists(ResourceBooking.class,
                        Joiners.equal((rule, resource, bucket) -> resource, ResourceBooking::getResource),
                        Joiners.filtering((rule, resource, bucket, booking) -> belongsTo(booking, bucket)))
                .penalize(HardSoftScore.ONE_SOFT,
                        (rule, resource, bucket) -> emptyPenalty(params(rule), rule.weight()))
                .asConstraint(NEVER_USED);
    }

    @Override
    public List<Constraint> buildAll(ConstraintFactory factory) {
        return List.of(build(factory), buildNeverUsed(factory));
    }

    @Override
    public List<String> constraintNames() {
        return List.of(TYPE, NEVER_USED);
    }

    // ═══ TRAVERSÉE B ═══

    @Override
    public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        Params params = params(rule);
        Map<AggregationScope, Integer> totals = new HashMap<>();
        Map<AggregationScope, List<ResourceBooking>> members = new HashMap<>();

        Streams.placedBookings(solution.getBookings(), rule).forEach(booking -> {
            for (PeriodBucket bucket : solution.getPeriodBuckets()) {
                if (bucket.period() != params.period() || !belongsTo(booking, bucket)) {
                    continue;
                }
                AggregationScope scope = scopeOf(booking, bucket);
                totals.merge(scope, Bookings.minutesIn(booking, bucket), Math::addExact);
                members.computeIfAbsent(scope, key -> new ArrayList<>()).add(booking);
            }
        });

        List<Violation> violations = new ArrayList<>();
        totals.forEach((scope, total) -> {
            if (!violates(total, params)) {
                return;
            }
            violations.add(new Violation(
                    TYPE,
                    rule.level(),
                    penalty(total, params, rule.weight()),
                    "%s n'atteint pas sa charge minimale %s : %s au lieu de %s attendues."
                            .formatted(scope.resource().label(),
                                    solution.getHorizon().labelOf(params.period(), scope.periodIndex()),
                                    Bookings.hoursMinutes(total),
                                    Bookings.hoursMinutes(params.minMinutes())),
                    List.of(scope.resource().id()),
                    members.get(scope).stream().map(b -> b.getActivity().getDemandId()).distinct().toList(),
                    Map.of("totalMinutes", total,
                            "minMinutes", params.minMinutes(),
                            "period", params.period().name(),
                            "periodIndex", scope.periodIndex())));
        });

        // Jumeau de `buildNeverUsed` : un panier par ressource et période.
        for (Resource resource : solution.getResources()) {
            if (!rule.scope().matches(resource)) {
                continue;
            }
            boolean neverUsed = Streams.placedBookings(solution.getBookings(), rule)
                    .noneMatch(booking -> booking.getResource().equals(resource));
            for (PeriodBucket bucket : solution.getPeriodBuckets()) {
                if (bucket.period() != params.period()) {
                    continue;
                }
                AggregationScope scope = new AggregationScope(resource, bucket.period(), bucket.index());
                if (totals.containsKey(scope)) {
                    continue;
                }
                violations.add(new Violation(
                        TYPE,
                        rule.level(),
                        emptyPenalty(params, rule.weight()),
                        "%s n'a aucune affectation %s : sa charge minimale de %s n'est pas atteinte."
                                .formatted(resource.label(), bucket.label(),
                                        Bookings.hoursMinutes(params.minMinutes())),
                        List.of(resource.id()),
                        List.of(),
                        Map.of("totalMinutes", 0,
                                "minMinutes", params.minMinutes(),
                                "period", params.period().name(),
                                "periodIndex", bucket.index(),
                                "neverUsed", neverUsed)));
            }
        }
        return violations;
    }
}
