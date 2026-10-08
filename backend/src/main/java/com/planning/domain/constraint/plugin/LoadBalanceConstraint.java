package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.constraint.Bookings;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * « Équilibrer la charge entre les professeurs de mathématiques. »
 *
 * <p><strong>Pourquoi la somme des carrés.</strong> Le volume total d'heures est
 * fixe, imposé par la demande. Or à somme constante, la somme des carrés est
 * minimale quand toutes les valeurs sont égales. Pénaliser {@code charge²}
 * équilibre donc automatiquement, sans jamais calculer de moyenne : passer de
 * (14 h, 12 h) à (13 h, 13 h) fait tomber la pénalité, et le solveur y va seul.
 *
 * <p>C'est plus robuste qu'une formulation en écart, qui supposerait une cible
 * connue d'avance. Le diviseur évite un débordement d'entier sur de grandes
 * charges.
 */
public final class LoadBalanceConstraint implements ConstraintPlugin {

    public static final String TYPE = "load.balance";

    private static final int SCALE = 1000;

    public record Params() {
    }

    // ═══ LOGIQUE MÉTIER — ÉCRITE UNE SEULE FOIS ═══

    private static int penalty(int loadMinutes, int ruleWeight) {
        return ruleWeight * loadMinutes * loadMinutes / SCALE;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String label() {
        return "Équilibre des charges";
    }

    @Override
    public String description() {
        return "Répartir également le volume horaire entre les ressources visées par le scope.";
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
        return Schemas.empty();
    }

    // ═══ TRAVERSÉE A ═══

    @Override
    public Constraint build(ConstraintFactory factory) {
        return Streams.scopedBookings(factory, TYPE)
                .groupBy((rule, booking) -> rule,
                        (rule, booking) -> booking.getResource(),
                        ConstraintCollectors.sum((rule, booking) -> booking.durationMinutes()))
                .penalize(HardSoftScore.ONE_SOFT,
                        (rule, resource, load) -> penalty(load, rule.weight()))
                .asConstraint(TYPE);
    }

    // ═══ TRAVERSÉE B ═══

    @Override
    public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        Map<Resource, Integer> loads = new HashMap<>();
        Map<Resource, List<ResourceBooking>> members = new HashMap<>();

        Streams.placedBookings(solution.getBookings(), rule).forEach(booking -> {
            loads.merge(booking.getResource(), booking.durationMinutes(), Integer::sum);
            members.computeIfAbsent(booking.getResource(), key -> new ArrayList<>()).add(booking);
        });

        // Chaque ressource chargée contribue au score, même sans déséquilibre :
        // c'est la somme des carrés qui porte l'incitation. Le message situe donc
        // la charge par rapport à la moyenne du groupe plutôt que par rapport à un
        // seuil, qui n'existe pas ici.
        int average = loads.isEmpty() ? 0
                : loads.values().stream().mapToInt(Integer::intValue).sum() / loads.size();

        List<Violation> violations = new ArrayList<>();
        loads.forEach((resource, load) -> violations.add(new Violation(
                TYPE,
                rule.level(),
                penalty(load, rule.weight()),
                "%s porte %s sur le cycle, pour une moyenne de %s dans son groupe."
                        .formatted(resource.label(), Bookings.hoursMinutes(load),
                                Bookings.hoursMinutes(average)),
                List.of(resource.id()),
                members.get(resource).stream().map(b -> b.getActivity().getDemandId()).distinct().toList(),
                Map.of("loadMinutes", load, "averageMinutes", average))));
        return violations;
    }
}
