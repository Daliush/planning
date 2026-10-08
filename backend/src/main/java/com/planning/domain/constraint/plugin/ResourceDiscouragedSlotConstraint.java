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
import com.planning.domain.time.CycleBuckets;

import java.util.List;
import java.util.Map;

/**
 * Le créneau est permis mais pas souhaité. Lit {@code TimeProfile.discouraged}.
 *
 * <p><strong>C'est le mécanisme de bascule dur ↔ souple par ressource</strong>
 * (§12.3). Passer les lignes de Jean de {@code UNAVAILABLE} à {@code DISCOURAGED}
 * les fait migrer d'un {@code BitSet} vers l'autre, donc d'une contrainte vers
 * l'autre. Aucun code modifié, aucun poids touché, et le comportement des autres
 * ressources reste intact.
 */
public final class ResourceDiscouragedSlotConstraint implements ConstraintPlugin {

    public static final String TYPE = "resource.discouraged_slot";

    // ═══ LOGIQUE MÉTIER — ÉCRITE UNE SEULE FOIS ═══

    private static boolean violates(ResourceBooking booking) {
        return booking.getResource().time().isDiscouraged(booking.startIndex(), booking.endIndex());
    }

    private static int penalty(ResourceBooking booking) {
        return booking.getResource().time().discouragedSlots(booking.startIndex(), booking.endIndex());
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String label() {
        return "Créneaux déconseillés";
    }

    @Override
    public String description() {
        return "Éviter les créneaux marqués « déconseillé » dans les disponibilités d'une ressource.";
    }

    @Override
    public Level defaultLevel() {
        return Level.SOFT;
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
                .filter(ResourceDiscouragedSlotConstraint::violates)
                .penalize(HardSoftScore.ONE_SOFT, ResourceDiscouragedSlotConstraint::penalty)
                .asConstraint(TYPE);
    }

    // ═══ TRAVERSÉE B ═══

    @Override
    public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        return Streams.placedBookings(solution.getBookings(), rule)
                .filter(ResourceDiscouragedSlotConstraint::violates)
                .map(booking -> new Violation(
                        TYPE,
                        rule.level(),
                        penalty(booking),
                        "%s préfère éviter le créneau %s à %s, occupé par « %s »."
                                .formatted(booking.getResource().label(),
                                        CycleBuckets.label(Period.DAY, booking.dayInCycle()),
                                        booking.startTime(),
                                        booking.getActivity().getDemandLabel()),
                        List.of(booking.getResource().id()),
                        List.of(booking.getActivity().getDemandId()),
                        Map.of("discouragedSlots", penalty(booking),
                                "dayInCycle", booking.dayInCycle(),
                                "startTime", booking.startTime().toString())))
                .toList();
    }
}
