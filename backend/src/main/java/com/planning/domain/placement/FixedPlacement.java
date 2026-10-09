package com.planning.domain.placement;

import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.model.Timeslot;
import com.planning.domain.time.TimeHorizon;

import java.time.LocalTime;
import java.time.LocalDate;
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
                "date", com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                        .put("type", "string").put("format", "date").put("title", "Date imposée"),
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
        LocalDate date = placement.hasNonNull("date") ? LocalDate.parse(placement.get("date").asText()) : null;
        if (date != null && horizon.kind() != com.planning.domain.model.HorizonKind.DATED) {
            throw new InvalidConfigException("Une date imposée exige un planning daté.");
        }

        return feasible.stream()
                .filter(slot -> slot.start().equals(start))
                .filter(slot -> day == null || day.isNull() || slot.dayInCycle() == day.asInt())
                .filter(slot -> date == null || date.equals(slot.date()))
                .toList();
    }
}
