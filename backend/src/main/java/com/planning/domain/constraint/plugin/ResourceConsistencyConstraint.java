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
import java.util.UUID;

/**
 * « Le même enseignant sur toutes les séances d'un cours. »
 *
 * <p><strong>Sans ce plugin, les premiers plannings sembleront aberrants</strong>
 * alors que le moteur aura parfaitement fait son travail : chaque
 * {@code ResourceBooking} est indépendant, rien n'empêche Jean le lundi et Marie
 * le jeudi pour le même cours.
 *
 * <p>La clé raisonne sur le <em>rôle</em>, pas sur le type. Chaque occurrence est
 * projetée en équipe : deux postes du même rôle peuvent conserver les mêmes
 * personnes dans un ordre différent. L'agrégation évite de comparer toutes les
 * paires d'occurrences.
 */
public final class ResourceConsistencyConstraint implements ConstraintPlugin {

    public static final String TYPE = "resource.consistency";

    public record Params() {
    }

    // ═══ LOGIQUE MÉTIER — ÉCRITE UNE SEULE FOIS ═══

    private static ConsistencyKey keyOf(ResourceBooking booking) {
        return new ConsistencyKey(booking.getActivity().getDemandId(), booking.getRole());
    }

    private static int distinctTeams(List<ResourceBooking> bookings) {
        Map<UUID, Set<UUID>> teams = new LinkedHashMap<>();
        for (ResourceBooking booking : bookings) {
            teams.computeIfAbsent(booking.getActivity().getId(), key -> new LinkedHashSet<>())
                    .add(booking.getResource().id());
        }
        return new LinkedHashSet<>(teams.values()).size();
    }

    private static boolean violates(List<ResourceBooking> bookings) {
        return distinctTeams(bookings) > 1;
    }

    private static int penalty(List<ResourceBooking> bookings, int ruleWeight) {
        return Math.multiplyExact(ruleWeight, distinctTeams(bookings) - 1);
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
        return "Toutes les séances d'une même demande doivent recevoir la même équipe de ressources pour un rôle donné.";
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
                        ConstraintCollectors.toList((rule, booking) -> booking))
                .filter((rule, key, bookings) -> violates(bookings))
                .penalize(HardSoftScore.ONE_HARD,
                        (rule, key, bookings) -> penalty(bookings, rule.weight()))
                .asConstraint(TYPE);
    }

    // ═══ TRAVERSÉE B ═══

    @Override
    public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        Map<ConsistencyKey, List<ResourceBooking>> members = new LinkedHashMap<>();
        Map<ConsistencyKey, String> labels = new LinkedHashMap<>();

        Streams.placedBookings(solution.getBookings(), rule).forEach(booking -> {
            ConsistencyKey key = keyOf(booking);
            members.computeIfAbsent(key, k -> new ArrayList<>()).add(booking);
            labels.putIfAbsent(key, booking.getActivity().getDemandLabel());
        });

        List<Violation> violations = new ArrayList<>();
        members.forEach((key, bookings) -> {
            if (!violates(bookings)) {
                return;
            }
            List<Resource> resources = bookings.stream().map(ResourceBooking::getResource).distinct().toList();
            violations.add(new Violation(
                    TYPE,
                    rule.level(),
                    penalty(bookings, rule.weight()),
                    "« %s » change de %s d'une séance à l'autre : %s."
                            .formatted(labels.get(key), key.role(),
                                    resources.stream().map(Resource::label).toList()),
                    resources.stream().map(Resource::id).toList(),
                    List.of(key.demandId()),
                    Map.of("role", key.role(), "distinctResources", resources.size(),
                            "distinctTeams", distinctTeams(bookings))));
        });
        return violations;
    }
}
