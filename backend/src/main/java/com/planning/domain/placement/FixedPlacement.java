package com.planning.domain.placement;

import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.model.Timeslot;
import com.planning.domain.time.TimeHorizon;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Horaire imposé : il n'y a rien à décider, le vivier se réduit à un élément.
 *
 * <pre>{"strategy":"fixed","startTime":"07:00","dayInCycle":0}</pre>
 *
 * <p>{@code dayInCycle} absent signifie « ce créneau horaire, n'importe quel jour ».
 */
public final class FixedPlacement implements PlacementStrategy {

    public static final String ID = "fixed";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public JsonNode paramSchema() {
        return PlacementSchemas.object(Map.of(
                "startTime", PlacementSchemas.time("Heure de début imposée"),
                "dayInCycle", PlacementSchemas.integer("Jour du cycle, 0-indexé", 0)),
                List.of("startTime"));
    }

    @Override
    public List<Timeslot> restrict(List<Timeslot> feasible, JsonNode placement, int occurrence,
                                   TimeHorizon horizon) {
        JsonNode startTime = placement.get("startTime");
        if (startTime == null || !startTime.isTextual()) {
            throw new InvalidConfigException(
                    "Le placement « fixed » exige un « startTime ». Reçu : " + placement);
        }
        LocalTime start = PlacementSchemas.parseTime(startTime.asText());
        JsonNode day = placement.get("dayInCycle");

        return feasible.stream()
                .filter(slot -> slot.start().equals(start))
                .filter(slot -> day == null || day.isNull() || slot.dayInCycle() == day.asInt())
                .toList();
    }
}
