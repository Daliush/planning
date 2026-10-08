package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.constraint.Bookings;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.NoParams;
import com.planning.domain.constraint.Schemas;
import com.planning.domain.constraint.Streams;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Level;
import com.planning.domain.model.Resource;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.Violation;
import com.planning.domain.time.CycleBuckets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <strong>Cette unique contrainte garantit la cohérence de tous les emplois du
 * temps</strong> — professeurs, classes, salles — simultanément. Elle ne connaît
 * ni les professeurs ni les classes : elle dit qu'une ressource ne peut pas être
 * à deux endroits à la fois.
 *
 * <p>C'est aussi ce qui rend le problème difficile, et pourquoi on résout tout en
 * même temps plutôt que classe par classe (§17.1).
 */
public final class ResourceConflictConstraint implements ConstraintPlugin {

    public static final String TYPE = "resource.conflict";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String label() {
        return "Pas de double affectation";
    }

    @Override
    public String description() {
        return "Une ressource ne peut pas être affectée à deux séances qui se chevauchent.";
    }

    @Override
    public Level defaultLevel() {
        return Level.HARD;
    }

    @Override
    public Class<?> paramsType() {
        return NoParams.class;
    }

    @Override
    public JsonNode paramSchema() {
        return Schemas.empty();
    }

    @Override
    public boolean configDriven() {
        return false;
    }

    // ═══ TRAVERSÉE A — incrémentale ═══

    @Override
    public Constraint build(ConstraintFactory factory) {
        return factory.forEachUniquePair(ResourceBooking.class,
                        Joiners.equal(ResourceBooking::getResource),
                        Joiners.overlapping(ResourceBooking::startIndex, ResourceBooking::endIndex))
                .penalize(HardSoftScore.ONE_HARD, Bookings::overlapSlots)
                .asConstraint(TYPE);
    }

    // ═══ TRAVERSÉE B — naïve, explicative ═══

    @Override
    public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        Map<Resource, List<ResourceBooking>> byResource = Streams
                .placedBookings(solution.getBookings(), rule)
                .collect(Collectors.groupingBy(ResourceBooking::getResource,
                        LinkedHashMap::new, Collectors.toList()));

        List<Violation> violations = new ArrayList<>();
        byResource.forEach((resource, bookings) -> {
            List<ResourceBooking> sorted = Bookings.sorted(bookings);
            for (int i = 0; i < sorted.size(); i++) {
                for (int j = i + 1; j < sorted.size(); j++) {
                    ResourceBooking a = sorted.get(i);
                    ResourceBooking b = sorted.get(j);
                    int overlap = Bookings.overlapSlots(a, b);
                    if (overlap <= 0) {
                        // La liste est triée : plus aucun voisin ne peut chevaucher a.
                        break;
                    }
                    violations.add(new Violation(
                            TYPE,
                            rule.level(),
                            overlap,
                            "%s est affectée à deux séances simultanées %s à %s : « %s » et « %s »."
                                    .formatted(resource.label(),
                                            CycleBuckets.label(com.planning.domain.model.Period.DAY, a.dayInCycle()),
                                            a.startTime(),
                                            a.getActivity().getDemandLabel(),
                                            b.getActivity().getDemandLabel()),
                            List.of(resource.id()),
                            List.of(a.getActivity().getDemandId(), b.getActivity().getDemandId()),
                            Map.of("overlapSlots", overlap,
                                    "dayInCycle", a.dayInCycle(),
                                    "startTime", a.startTime().toString())));
                }
            }
        });
        return violations;
    }
}
