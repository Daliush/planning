package com.planning.domain.placement;

import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.model.Timeslot;
import com.planning.domain.time.TimeHorizon;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Borne dure d'ouverture : le démarrage doit tomber dans {@code [from, to]}.
 *
 * <pre>{"strategy":"window","from":"06:00","to":"09:00"}</pre>
 *
 * <p>C'est bien une borne, pas une préférence. Une préférence se configure sur le
 * plugin {@code time.preferred_window}, qui reste souple et apparaît dans le
 * rapport de violations.
 */
public final class WindowPlacement implements PlacementStrategy {

    public static final String ID = "window";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public JsonNode paramSchema() {
        return PlacementSchemas.object(Map.of(
                "from", PlacementSchemas.time("Début de la fenêtre autorisée"),
                "to", PlacementSchemas.time("Fin de la fenêtre autorisée"),
                "dayInCycle", PlacementSchemas.integer("Restreindre à un jour du cycle", 0)),
                List.of("from", "to"));
    }

    @Override
    public List<Timeslot> restrict(List<Timeslot> feasible, JsonNode placement, int occurrence,
                                   TimeHorizon horizon) {
        JsonNode fromNode = placement.get("from");
        JsonNode toNode = placement.get("to");
        if (fromNode == null || toNode == null) {
            throw new InvalidConfigException(
                    "Le placement « window » exige « from » et « to ». Reçu : " + placement);
        }
        LocalTime from = PlacementSchemas.parseTime(fromNode.asText());
        LocalTime to = PlacementSchemas.parseTime(toNode.asText());
        if (from.isAfter(to)) {
            throw new InvalidConfigException(
                    "Fenêtre de placement inversée : « from » (%s) est après « to » (%s)."
                            .formatted(from, to));
        }
        JsonNode day = placement.get("dayInCycle");

        return feasible.stream()
                .filter(slot -> !slot.start().isBefore(from) && !slot.start().isAfter(to))
                .filter(slot -> day == null || day.isNull() || slot.dayInCycle() == day.asInt())
                .toList();
    }
}
