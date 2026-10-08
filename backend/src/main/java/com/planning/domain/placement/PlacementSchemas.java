package com.planning.domain.placement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planning.domain.exception.InvalidConfigException;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Fabriques de JSON Schema partagées par les stratégies de placement. */
public final class PlacementSchemas {

    static final Map<String, JsonNode> NO_PROPERTIES = Map.of();

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private PlacementSchemas() {
    }

    public static ObjectNode object(Map<String, JsonNode> properties, List<String> required) {
        ObjectNode schema = NODES.objectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        properties.forEach(props::set);
        if (!required.isEmpty()) {
            required.forEach(schema.putArray("required")::add);
        }
        return schema;
    }

    public static ObjectNode time(String description) {
        ObjectNode node = NODES.objectNode();
        node.put("type", "string");
        node.put("pattern", "^\\d{2}:\\d{2}(:\\d{2})?$");
        node.put("description", description);
        return node;
    }

    public static ObjectNode integer(String description, int minimum) {
        ObjectNode node = NODES.objectNode();
        node.put("type", "integer");
        node.put("minimum", minimum);
        node.put("description", description);
        return node;
    }

    public static LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw new InvalidConfigException("Heure invalide : « %s ». Format attendu HH:mm.".formatted(raw));
        }
    }
}
