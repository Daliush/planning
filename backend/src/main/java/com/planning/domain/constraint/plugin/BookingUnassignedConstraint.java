package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.NoParams;
import com.planning.domain.constraint.Schemas;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Level;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.Violation;

import java.util.List;
import java.util.Map;

/**
 * Nécessaire dès lors que {@code allowsUnassigned = true} : sans cette pénalité,
 * laisser tous les postes vides serait la meilleure solution du solveur.
 *
 * <p>Le bénéfice est un planning partiel explicite — « 5 postes sur 6 » — plutôt
 * qu'un échec d'initialisation. Les postes impossibles à pourvoir remontent alors
 * comme des violations nommées, ce qui est nettement plus exploitable (§16.2).
 */
public final class BookingUnassignedConstraint implements ConstraintPlugin {

    public static final String TYPE = "booking.unassigned";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String label() {
        return "Postes non pourvus";
    }

    @Override
    public String description() {
        return "Chaque poste d'une séance doit recevoir une ressource.";
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

    /**
     * PIÈGE — {@code forEach} exclut les entités dont la variable de planification
     * est nulle, y compris quand {@code allowsUnassigned = true}. Écrite avec
     * {@code forEach}, cette contrainte ne matcherait jamais rien : laisser tous
     * les postes vides deviendrait gratuit, et c'est exactement ce que la
     * recherche locale ferait — le solveur rendrait un planning entièrement vide
     * avec un score parfait.
     *
     * <p>{@code forEachIncludingUnassigned} est donc obligatoire ici, et
     * uniquement ici : c'est le seul plugin qui s'intéresse aux postes non
     * pourvus.
     */
    @Override
    public Constraint build(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ResourceBooking.class)
                .filter(booking -> booking.getResource() == null)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint(TYPE);
    }

    // ═══ TRAVERSÉE B ═══

    @Override
    public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        return solution.getBookings().stream()
                .filter(booking -> booking.getResource() == null)
                .map(booking -> new Violation(
                        TYPE,
                        rule.level(),
                        1,
                        "Aucune ressource n'a pu être affectée au poste « %s » de « %s »."
                                .formatted(booking.getRole(), booking.getActivity().getDemandLabel()),
                        List.of(),
                        List.of(booking.getActivity().getDemandId()),
                        Map.of("role", booking.getRole(),
                                "occurrence", booking.getActivity().getOccurrence(),
                                "eligibleCount", booking.getEligible().size())))
                .toList();
    }
}
