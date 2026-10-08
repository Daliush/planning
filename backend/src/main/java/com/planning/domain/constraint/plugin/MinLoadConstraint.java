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
import com.planning.domain.model.Resource;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.Violation;
import com.planning.domain.time.CycleBuckets;
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
 * part des <em>ressources</em> et retient celles qu'aucune affectation ne
 * mentionne. Les deux partagent le poids ①, la formule de pénalité et
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

    private static AggregationScope scopeOf(ResourceBooking booking, Params params) {
        return new AggregationScope(booking.getResource(), params.period(),
                CycleBuckets.bucketOf(booking.getStart(), params.period()));
    }

    private static boolean violates(int totalMinutes, Params params) {
        return totalMinutes < params.minMinutes();
    }

    private static int penalty(int totalMinutes, Params params, int ruleWeight) {
        return ruleWeight * (params.minMinutes() - totalMinutes);
    }

    /** Une ressource jamais utilisée manque l'intégralité de son plancher. */
    private static int emptyPenalty(Params params, int ruleWeight) {
        return penalty(0, params, ruleWeight);
    }

    private static boolean isUsedBy(ResourceBooking booking, Resource resource) {
        // Même sémantique que `ifNotExists` du pipeline auxiliaire, qui ignore les
        // postes non pourvus : un poste vide n'est pas du travail.
        return booking.getResource() != null && booking.getResource().equals(resource);
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
        return "Garantir un volume horaire plancher à une ressource déjà sollicitée. "
                + "Une ressource sans aucune affectation n'est pas détectée par cette règle.";
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
                .groupBy((rule, booking) -> rule,
                        (rule, booking) -> scopeOf(booking, params(rule)),
                        ConstraintCollectors.sum((rule, booking) -> booking.durationMinutes()))
                .filter((rule, scope, total) -> violates(total, params(rule)))
                .penalize(HardSoftScore.ONE_SOFT,
                        (rule, scope, total) -> penalty(total, params(rule), rule.weight()))
                .asConstraint(TYPE);
    }

    /** Le pipeline de l'absence : les ressources qu'aucune affectation ne mentionne. */
    private Constraint buildNeverUsed(ConstraintFactory factory) {
        return factory.forEach(ConstraintConfig.class)
                .filter(rule -> rule.type().equals(TYPE))
                .join(Resource.class,
                        Joiners.filtering((rule, resource) -> rule.scope().matches(resource)))
                .ifNotExists(ResourceBooking.class,
                        Joiners.equal((rule, resource) -> resource, ResourceBooking::getResource))
                .penalize(HardSoftScore.ONE_SOFT,
                        (rule, resource) -> emptyPenalty(params(rule), rule.weight()))
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
            AggregationScope scope = scopeOf(booking, params);
            totals.merge(scope, booking.durationMinutes(), Integer::sum);
            members.computeIfAbsent(scope, key -> new ArrayList<>()).add(booking);
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
                                    CycleBuckets.label(params.period(), scope.periodIndex()),
                                    Bookings.hoursMinutes(total),
                                    Bookings.hoursMinutes(params.minMinutes())),
                    List.of(scope.resource().id()),
                    members.get(scope).stream().map(b -> b.getActivity().getDemandId()).distinct().toList(),
                    Map.of("totalMinutes", total,
                            "minMinutes", params.minMinutes(),
                            "period", params.period().name(),
                            "periodIndex", scope.periodIndex())));
        });

        // Jumeau de `buildNeverUsed` : les ressources qu'aucune affectation ne
        // mentionne. Elles ne sont dans aucun panier ci-dessus, il n'y a donc pas
        // de double comptage.
        for (Resource resource : solution.getResources()) {
            if (!rule.scope().matches(resource)) {
                continue;
            }
            boolean used = solution.getBookings().stream()
                    .anyMatch(booking -> isUsedBy(booking, resource));
            if (used) {
                continue;
            }
            violations.add(new Violation(
                    TYPE,
                    rule.level(),
                    emptyPenalty(params, rule.weight()),
                    "%s n'a aucune affectation : sa charge minimale de %s n'est jamais atteinte."
                            .formatted(resource.label(), Bookings.hoursMinutes(params.minMinutes())),
                    List.of(resource.id()),
                    List.of(),
                    Map.of("totalMinutes", 0,
                            "minMinutes", params.minMinutes(),
                            "period", params.period().name(),
                            "neverUsed", true)));
        }
        return violations;
    }
}
