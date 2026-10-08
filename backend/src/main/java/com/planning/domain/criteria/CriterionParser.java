package com.planning.domain.criteria;

import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.exception.InvalidConfigException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Traduit la forme JSON des critères vers le {@link Criterion} typé.
 *
 * <pre>
 * {"eq":   {"path":"/type", "value":"enseignant"}}
 * {"gte":  {"path":"/capacite", "value":24}}
 * {"contains": {"path":"/matieres", "value":"maths"}}
 * {"and":  [ … ]}
 * {"not":  { … }}
 * {}                                    → Always
 * </pre>
 *
 * <p>Le parsing a lieu une fois au chargement : aucune analyse JSON ne descend
 * dans la boucle chaude.
 */
public final class CriterionParser {

    private static final Map<String, Function<JsonNode, Criterion>> OPERATORS = Map.ofEntries(
            Map.entry("eq", body -> new Criterion.Eq(path(body), value(body))),
            Map.entry("neq", body -> new Criterion.Neq(path(body), value(body))),
            Map.entry("gt", body -> new Criterion.Gt(path(body), number(body))),
            Map.entry("gte", body -> new Criterion.Gte(path(body), number(body))),
            Map.entry("lt", body -> new Criterion.Lt(path(body), number(body))),
            Map.entry("lte", body -> new Criterion.Lte(path(body), number(body))),
            Map.entry("in", body -> new Criterion.In(path(body), valueList(body))),
            Map.entry("contains", body -> new Criterion.Contains(path(body), text(body))),
            Map.entry("containsAll", body -> new Criterion.ContainsAll(path(body), textList(body))),
            Map.entry("and", body -> new Criterion.And(children(body, "and"))),
            Map.entry("or", body -> new Criterion.Or(children(body, "or"))),
            Map.entry("not", body -> new Criterion.Not(parse(body))));

    private CriterionParser() {
    }

    /** Les identifiants d'opérateurs, exposés au frontend pour construire l'éditeur. */
    public static Set<String> operators() {
        return OPERATORS.keySet();
    }

    public static Criterion parse(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || node.isEmpty()) {
            return new Criterion.Always();
        }
        if (!node.isObject()) {
            throw new InvalidConfigException("Un critère doit être un objet JSON, reçu : " + node);
        }
        if (node.size() != 1) {
            throw new InvalidConfigException(
                    "Un critère porte exactement un opérateur ; utiliser « and » pour en combiner plusieurs. Reçu : " + node);
        }

        Map.Entry<String, JsonNode> entry = node.fields().next();
        Function<JsonNode, Criterion> factory = OPERATORS.get(entry.getKey());
        if (factory == null) {
            throw new InvalidConfigException(
                    "Opérateur de critère inconnu : « %s ». Disponibles : %s"
                            .formatted(entry.getKey(), String.join(", ", OPERATORS.keySet().stream().sorted().toList())));
        }
        return factory.apply(entry.getValue());
    }

    /**
     * Forme canonique servant de clé de mutualisation des viviers (§9.4). Deux
     * requirements de même type et de critères identiques partagent alors la même
     * instance de liste : avec 10 000 bookings, la différence entre partager et
     * dupliquer se compte en centaines de mégaoctets.
     */
    public static String canonical(JsonNode node) {
        return node == null || node.isNull() ? "{}" : node.toString();
    }

    private static String path(JsonNode body) {
        JsonNode path = body.get("path");
        if (path == null || !path.isTextual() || !path.asText().startsWith("/")) {
            throw new InvalidConfigException(
                    "« path » doit être un JSON Pointer commençant par « / ». Reçu : " + body);
        }
        return path.asText();
    }

    private static JsonNode value(JsonNode body) {
        JsonNode value = body.get("value");
        if (value == null) {
            throw new InvalidConfigException("« value » est obligatoire. Reçu : " + body);
        }
        return value;
    }

    private static BigDecimal number(JsonNode body) {
        JsonNode value = value(body);
        if (!value.isNumber()) {
            throw new InvalidConfigException(
                    "Une comparaison numérique attend un nombre, reçu : " + value);
        }
        return value.decimalValue();
    }

    private static String text(JsonNode body) {
        JsonNode value = value(body);
        if (!value.isValueNode()) {
            throw new InvalidConfigException("« contains » attend une valeur scalaire, reçu : " + value);
        }
        return value.asText();
    }

    private static List<JsonNode> valueList(JsonNode body) {
        JsonNode values = body.get("values");
        if (values == null || !values.isArray()) {
            throw new InvalidConfigException("« in » attend un tableau « values ». Reçu : " + body);
        }
        List<JsonNode> result = new ArrayList<>();
        values.forEach(result::add);
        return List.copyOf(result);
    }

    private static List<String> textList(JsonNode body) {
        return valueListFrom(body).stream().map(JsonNode::asText).toList();
    }

    private static List<JsonNode> valueListFrom(JsonNode body) {
        JsonNode values = body.get("values");
        if (values == null || !values.isArray()) {
            throw new InvalidConfigException("« containsAll » attend un tableau « values ». Reçu : " + body);
        }
        List<JsonNode> result = new ArrayList<>();
        values.forEach(result::add);
        return result;
    }

    private static List<Criterion> children(JsonNode body, String operator) {
        if (!body.isArray() || body.isEmpty()) {
            throw new InvalidConfigException(
                    "« %s » attend un tableau non vide de critères. Reçu : %s".formatted(operator, body));
        }
        List<Criterion> result = new ArrayList<>();
        body.forEach(child -> result.add(parse(child)));
        return List.copyOf(result);
    }
}
