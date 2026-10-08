package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
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
 * Le patron de référence du plugin configurable (§11.4).
 *
 * <p>Le cumul des scopes est une <strong>intersection</strong> de règles, pas une
 * hiérarchie : Jean, enseignant vacataire, est soumis aux deux quotas
 * simultanément. Pour qu'une règle spécifique remplace la générale, il faut
 * l'exprimer dans le scope avec un {@code not} — c'est explicite, et préférable à
 * un mécanisme de priorité implicite que personne ne comprendrait en lisant la
 * configuration.
 */
public final class MaxLoadConstraint implements ConstraintPlugin {

    public static final String TYPE = "load.max";

    public record Params(int maxMinutes, Period period) {
    }

    // ═══ LOGIQUE MÉTIER — ÉCRITE UNE SEULE FOIS, APPELÉE PAR LES DEUX TRAVERSÉES ═══

    private static Params params(ConstraintConfig rule) {
        return rule.paramsAs(Params.class);
    }

    private static AggregationScope scopeOf(ResourceBooking booking, Params params) {
        return new AggregationScope(booking.getResource(), params.period(),
                CycleBuckets.bucketOf(booking.getStart(), params.period()));
    }

    private static boolean violates(int totalMinutes, Params params) {
        return totalMinutes > params.maxMinutes();
    }

    private static int penalty(int totalMinutes, Params params, int ruleWeight) {
        return ruleWeight * (totalMinutes - params.maxMinutes());
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String label() {
        return "Charge maximale";
    }

    @Override
    public String description() {
        return "Plafonner le volume horaire d'une ressource sur une période donnée.";
    }

    @Override
    public Level defaultLevel() {
        return Level.HARD;
    }

    @Override
    public Class<?> paramsType() {
        return Params.class;
    }

    @Override
    public JsonNode paramSchema() {
        return Schemas.properties()
                .add("maxMinutes", Schemas.minutes("Plafond sur la période", 1))
                .add("period", Schemas.period("Découpage du quota"))
                .required("maxMinutes", "period");
    }

    /**
     * La restriction porte sur le couple plugin-paramètre, pas sur le plugin :
     * {@code load.max} est universel, {@code load.max} avec {@code period = MONTH}
     * ne l'est pas (§14.5).
     */
    @Override
    public void validate(ConstraintConfig config, TimeHorizon horizon) {
        Period period = params(config).period();
        if (!horizon.supports(period)) {
            throw new IncompatibleConfigException(
                    "La période %s n'est pas disponible sur un horizon %s."
                            .formatted(period, horizon.kind()));
        }
    }

    // ═══ TRAVERSÉE A — incrémentale, pour le solveur ═══

    @Override
    public Constraint build(ConstraintFactory factory) {
        return Streams.scopedBookings(factory, TYPE)
                .groupBy((rule, booking) -> rule,
                        (rule, booking) -> scopeOf(booking, params(rule)),
                        ConstraintCollectors.sum((rule, booking) -> booking.durationMinutes()))
                .filter((rule, scope, total) -> violates(total, params(rule)))
                .penalize(HardSoftScore.ONE_HARD,
                        (rule, scope, total) -> penalty(total, params(rule), rule.weight()))
                .asConstraint(TYPE);
    }

    // ═══ TRAVERSÉE B — naïve, pour l'utilisateur ═══

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
                    "%s dépasse son quota %s : %s au lieu de %s maximum."
                            .formatted(scope.resource().label(),
                                    CycleBuckets.label(params.period(), scope.periodIndex()),
                                    Bookings.hoursMinutes(total),
                                    Bookings.hoursMinutes(params.maxMinutes())),
                    List.of(scope.resource().id()),
                    members.get(scope).stream().map(b -> b.getActivity().getDemandId()).distinct().toList(),
                    Map.of("totalMinutes", total,
                            "maxMinutes", params.maxMinutes(),
                            "period", params.period().name(),
                            "periodIndex", scope.periodIndex())));
        });
        return violations;
    }
}
