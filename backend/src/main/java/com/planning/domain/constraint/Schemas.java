package com.planning.domain.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planning.domain.model.Period;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fabriques de JSON Schema pour les {@code params} des plugins.
 *
 * <p>Le schéma sert à valider la saisie côté API : sans lui, une configuration
 * invalide ferait échouer une résolution des semaines plus tard, sans message
 * exploitable (§15, contrôle 8).
 */
public final class Schemas {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private Schemas() {
    }

    public static ObjectNode object(Map<String, JsonNode> properties, List<String> required) {
        ObjectNode schema = NODES.objectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = schema.putObject("properties");
        properties.forEach(props::set);
        if (!required.isEmpty()) {
            required.forEach(schema.putArray("required")::add);
        }
        return schema;
    }

    /**
     * Déclare les propriétés <strong>dans l'ordre</strong>.
     *
     * <p>{@code Map.of()} n'a pas d'ordre d'itération défini : les champs du
     * formulaire généré apparaîtraient dans un ordre arbitraire, et différent d'une
     * exécution à l'autre. L'ordre de déclaration est une information de conception
     * — on saisit un plafond avant de choisir sur quelle période il porte.
     */
    public static Properties properties() {
        return new Properties();
    }

    public static final class Properties {

        private final Map<String, JsonNode> ordered = new LinkedHashMap<>();

        private Properties() {
        }

        public Properties add(String name, JsonNode schema) {
            ordered.put(name, schema);
            return this;
        }

        public ObjectNode required(String... names) {
            return object(ordered, List.of(names));
        }
    }

    public static ObjectNode empty() {
        return object(Map.of(), List.of());
    }

    /**
     * Marqueur d'unité. Sans lui, une durée en minutes et un simple compteur
     * produisent exactement le même schéma, et une interface générique ne peut pas
     * savoir qu'il faut proposer « 10 h 00 » pour l'un et « 3 » pour l'autre. Elle
     * devrait deviner d'après le nom de la propriété, c'est-à-dire coder en dur
     * une convention de nommage — exactement ce que l'architecture interdit
     * ailleurs.
     */
    public static final String UNIT = "x-unit";
    public static final String UNIT_MINUTES = "minutes";
    public static final String UNIT_TIME_OF_DAY = "timeOfDay";

    /** Les durées s'expriment en minutes, jamais en créneaux (§5.3). */
    public static ObjectNode minutes(String description, int minimum) {
        ObjectNode node = integer(description, minimum);
        node.put(UNIT, UNIT_MINUTES);
        return node;
    }

    /** Un compteur — un nombre de séances, d'occurrences. Sans unité. */
    public static ObjectNode integer(String description, int minimum) {
        ObjectNode node = NODES.objectNode();
        node.put("type", "integer");
        node.put("minimum", minimum);
        node.put("description", description);
        return node;
    }

    /**
     * Marque une énumération dont les valeurs proposables dépendent du scénario.
     * Le catalogue scopé s'en sert pour retirer celles que l'horizon refuse : une
     * interface qui offre un réglage voué à l'échec au chargement est une faute.
     */
    public static final String ENUM_KIND = "x-enum";
    public static final String ENUM_PERIOD = "period";

    public static ObjectNode period(String description) {
        ObjectNode node = NODES.objectNode();
        var values = node.putArray("enum");
        for (Period period : Period.values()) {
            values.add(period.name());
        }
        node.put("description", description);
        node.put(ENUM_KIND, ENUM_PERIOD);
        return node;
    }

    public static ObjectNode time(String description) {
        ObjectNode node = NODES.objectNode();
        node.put("type", "string");
        node.put("pattern", "^\\d{2}:\\d{2}(:\\d{2})?$");
        node.put("description", description);
        node.put(UNIT, UNIT_TIME_OF_DAY);
        return node;
    }
}
