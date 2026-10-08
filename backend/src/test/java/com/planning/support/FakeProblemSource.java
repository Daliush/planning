package com.planning.support;

import com.planning.domain.port.ProblemSource;
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
import java.util.stream.Collectors;

/**
 * La source en mémoire.
 *
 * <p>C'est l'intérêt concret de l'inversion de dépendance du §4.3 : le
 * {@code ProblemBuilder} se teste intégralement — compilation des disponibilités,
 * viviers, validation — sans base de données ni contexte Spring.
 */
public class FakeProblemSource implements ProblemSource {

    private final TimeModelRow timeModel;
    private final List<SlotRow> slots;
    private final List<ResourceRow> resources;
    private final List<AvailabilityRow> availability;
    private final List<DemandRow> demands;
    private final List<ConstraintConfigRow> configs;
    private final List<ConstraintWeightRow> weights;

    public FakeProblemSource(TimeModelRow timeModel, List<SlotRow> slots,
                             List<ResourceRow> resources, List<AvailabilityRow> availability,
                             List<DemandRow> demands, List<ConstraintConfigRow> configs,
                             List<ConstraintWeightRow> weights) {
        this.timeModel = timeModel;
        this.slots = slots;
        this.resources = resources;
        this.availability = availability;
        this.demands = demands;
        this.configs = configs;
        this.weights = weights;
    }

    @Override
    public TimeModelRow timeModel(UUID scenarioId) {
        return timeModel;
    }

    @Override
    public List<SlotRow> slots(UUID timeModelId) {
        return slots;
    }

    @Override
    public List<ResourceRow> resources(UUID scenarioId) {
        return resources;
    }

    @Override
    public Map<UUID, List<AvailabilityRow>> availabilityByResource(UUID scenarioId) {
        return availability.stream().collect(Collectors.groupingBy(AvailabilityRow::resourceId));
    }

    @Override
    public List<DemandRow> demands(UUID scenarioId) {
        return demands;
    }

    @Override
    public List<ConstraintConfigRow> enabledConstraintConfigs(UUID scenarioId) {
        return configs.stream().filter(ConstraintConfigRow::enabled).toList();
    }

    @Override
    public List<ConstraintWeightRow> constraintWeights(UUID scenarioId) {
        return weights;
    }
}
