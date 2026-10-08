package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.constraint.ConsistencyKey;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.Schemas;
import com.planning.domain.constraint.Streams;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Level;
import com.planning.domain.model.Resource;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.Violation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * « Le même enseignant sur toutes les séances d'un cours. »
 *
 * <p><strong>Sans ce plugin, les premiers plannings sembleront aberrants</strong>
 * alors que le moteur aura parfaitement fait son travail : chaque
 * {@code ResourceBooking} est indépendant, rien n'empêche Jean le lundi et Marie
 * le jeudi pour le même cours.
 *
 * <p>La clé raisonne sur le <em>rôle</em>, pas sur le type. Et le collecteur est
 * {@code countDistinct}, non {@code forEachUniquePair} : sur 4 occurrences la
 * différence est invisible, sur 36 en horizon daté la version par paires devient
 * quadratique et injouable.
 */
public final class ResourceConsistencyConstraint implements ConstraintPlugin {

    public static final String TYPE = "resource.consistency";

    public record Params() {
    }

    // ═══ LOGIQUE MÉTIER — ÉCRITE UNE SEULE FOIS ═══

    private static ConsistencyKey keyOf(ResourceBooking booking) {
        return new ConsistencyKey(booking.getActivity().getDemandId(), booking.getRole());
    }

    private static boolean violates(int distinctResources) {
        return distinctResources > 1;
    }

    private static int penalty(int distinctResources, int ruleWeight) {
        return ruleWeight * (distinctResources - 1);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String label() {
        return "Continuité des ressources";
    }

    @Override
    public String description() {
        return "Toutes les séances d'une même demande doivent recevoir la même ressource pour un rôle donné.";
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
        return Schemas.empty();
    }

    // ═══ TRAVERSÉE A ═══

    @Override
    public Constraint build(ConstraintFactory factory) {
        return Streams.scopedBookings(factory, TYPE)
                .groupBy((rule, booking) -> rule,
                        (rule, booking) -> keyOf(booking),
                        ConstraintCollectors.countDistinct((rule, booking) -> booking.getResource()))
                .filter((rule, key, distinct) -> violates(distinct))
                .penalize(HardSoftScore.ONE_HARD,
                        (rule, key, distinct) -> penalty(distinct, rule.weight()))
                .asConstraint(TYPE);
    }

    // ═══ TRAVERSÉE B ═══

    @Override
    public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        Map<ConsistencyKey, Set<Resource>> distinct = new LinkedHashMap<>();
        Map<ConsistencyKey, String> labels = new LinkedHashMap<>();

        Streams.placedBookings(solution.getBookings(), rule).forEach(booking -> {
            ConsistencyKey key = keyOf(booking);
            distinct.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(booking.getResource());
            labels.putIfAbsent(key, booking.getActivity().getDemandLabel());
        });

        List<Violation> violations = new ArrayList<>();
        distinct.forEach((key, resources) -> {
            if (!violates(resources.size())) {
                return;
            }
            violations.add(new Violation(
                    TYPE,
                    rule.level(),
                    penalty(resources.size(), rule.weight()),
                    "« %s » change de %s d'une séance à l'autre : %s."
                            .formatted(labels.get(key), key.role(),
                                    resources.stream().map(Resource::label).toList()),
                    resources.stream().map(Resource::id).toList(),
                    List.of(key.demandId()),
                    Map.of("role", key.role(), "distinctResources", resources.size())));
        });
        return violations;
    }
}
