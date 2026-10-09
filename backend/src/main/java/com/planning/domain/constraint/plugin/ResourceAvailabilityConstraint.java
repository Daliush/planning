package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.NoParams;
import com.planning.domain.constraint.Schemas;
import com.planning.domain.constraint.Streams;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Level;
import com.planning.domain.model.Period;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.Violation;

import java.util.List;
import java.util.Map;

/**
 * La ressource est-elle là ? Lit {@code TimeProfile.allowed}, compilé depuis
 * {@code resource_availability}.
 *
 * <p>Jumeau de {@link ResourceDiscouragedSlotConstraint} — deux plugins, pas un
 * avec un {@code if}. Le découpage donne deux poids réglables séparément, deux
 * lignes distinctes dans l'explication du score, et la possibilité d'en désactiver
 * un sans toucher l'autre (§12.3).
 *
 * <p>Le poids ③ est essentiel ici : {@code forbiddenSlots} fait qu'une séance
 * débordant d'un seul créneau coûte moins qu'une séance entièrement en zone
 * fermée. Sans lui, le solveur n'a aucune raison de préférer le débordement
 * minimal quand aucune place propre ne se libère.
 */
public final class ResourceAvailabilityConstraint implements ConstraintPlugin {

    public static final String TYPE = "resource.availability";

    // ═══ LOGIQUE MÉTIER — ÉCRITE UNE SEULE FOIS ═══

    private static boolean violates(ResourceBooking booking) {
        return !booking.getResource().time().isAllowed(booking.startIndex(), booking.endIndex());
    }

    private static int penalty(ResourceBooking booking) {
        return booking.getResource().time().forbiddenSlots(booking.startIndex(), booking.endIndex());
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String label() {
        return "Disponibilité des ressources";
    }

    @Override
    public String description() {
        return "Une ressource ne peut pas être affectée sur un créneau où elle est indisponible.";
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

    // ═══ TRAVERSÉE A ═══

    @Override
    public Constraint build(ConstraintFactory factory) {
        return Streams.placedBookings(factory)
                .filter(ResourceAvailabilityConstraint::violates)
                .penalize(HardSoftScore.ONE_HARD, ResourceAvailabilityConstraint::penalty)
                .asConstraint(TYPE);
    }

    // ═══ TRAVERSÉE B ═══

    @Override
    public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        return Streams.placedBookings(solution.getBookings(), rule)
                .filter(ResourceAvailabilityConstraint::violates)
                .map(booking -> new Violation(
                        TYPE,
                        rule.level(),
                        penalty(booking),
                        "%s n'est pas disponible %s à %s, où « %s » lui est pourtant affecté."
                                .formatted(booking.getResource().label(),
                                        solution.getHorizon().labelOf(Period.DAY,
                                                solution.getHorizon().bucketOf(booking.getStart(), Period.DAY)),
                                        booking.startTime(),
                                        booking.getActivity().getDemandLabel()),
                        List.of(booking.getResource().id()),
                        List.of(booking.getActivity().getDemandId()),
                        Map.of("forbiddenSlots", penalty(booking),
                                "dayInCycle", booking.dayInCycle(),
                                "startTime", booking.startTime().toString(),
                                "role", booking.getRole())))
                .toList();
    }
}
