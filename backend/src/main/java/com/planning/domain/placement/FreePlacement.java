package com.planning.domain.placement;

import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.model.Timeslot;
import com.planning.domain.time.TimeHorizon;

import java.util.List;

/** Le solveur choisit. Vivier inchangé. */
public final class FreePlacement implements PlacementStrategy {

    public static final String ID = "free";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public JsonNode paramSchema() {
        return PlacementSchemas.object(PlacementSchemas.NO_PROPERTIES, List.of());
    }

    @Override
    public List<Timeslot> restrict(List<Timeslot> feasible, JsonNode placement, int occurrence,
                                   TimeHorizon horizon) {
        return feasible;
    }
}
