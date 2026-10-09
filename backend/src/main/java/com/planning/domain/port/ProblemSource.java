package com.planning.domain.port;

import com.planning.domain.port.Rows.AvailabilityRow;
import com.planning.domain.port.Rows.ConstraintConfigRow;
import com.planning.domain.port.Rows.ConstraintWeightRow;
import com.planning.domain.port.Rows.DemandRow;
import com.planning.domain.port.Rows.ResourceRow;
import com.planning.domain.port.Rows.SlotRow;
import com.planning.domain.port.Rows.TimeModelRow;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce dont le builder a besoin pour fabriquer un problème.
 *
 * <p>Le domaine définit l'interface, la couche {@code repository} l'implémente
 * (§4.3). C'est ce qui rend le {@code ProblemBuilder} testable sans base de
 * données — et ce qui garantit qu'aucune entité JPA ne franchit la frontière : les
 * types échangés sont des {@code record} immuables déclarés dans {@link Rows}.
 */
public interface ProblemSource {

    default List<Rows.AbsenceRow> absences(UUID scenarioId) { return List.of(); }

    TimeModelRow timeModel(UUID scenarioId);

    List<SlotRow> slots(UUID timeModelId);

    List<ResourceRow> resources(UUID scenarioId);

    Map<UUID, List<AvailabilityRow>> availabilityByResource(UUID scenarioId);

    List<DemandRow> demands(UUID scenarioId);

    /** Seules les lignes actives : {@code enabled} ne dépend d'aucune variable de planification. */
    List<ConstraintConfigRow> enabledConstraintConfigs(UUID scenarioId);

    List<ConstraintWeightRow> constraintWeights(UUID scenarioId);
}
