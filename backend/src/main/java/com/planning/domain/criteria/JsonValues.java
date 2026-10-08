package com.planning.domain.criteria;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * Comparaisons tolérantes sur {@link JsonNode}, partagées par tous les opérateurs
 * de critères.
 *
 * <p>Règle transverse : un attribut absent ou d'un type inattendu ne fait jamais
 * planter le moteur, il ne matche simplement pas. C'est volontaire — une
 * exception au milieu de la construction des viviers rendrait tout le scénario
 * inexploitable pour une seule ressource mal saisie, alors que le
 * {@code ProblemValidator} détecte le vivier vide qui en résulte et nomme le
 * besoin concerné.
 */
final class JsonValues {

    private JsonValues() {
    }

    static boolean equal(JsonNode actual, JsonNode expected) {
        if (actual.isMissingNode() || actual.isNull()) {
            return expected == null || expected.isNull();
        }
        if (expected == null) {
            return false;
        }
        if (actual.isNumber() && expected.isNumber()) {
            return actual.decimalValue().compareTo(expected.decimalValue()) == 0;
        }
        if (actual.isValueNode() && expected.isValueNode()) {
            return actual.asText().equals(expected.asText());
        }
        return actual.equals(expected);
    }

    /** Renvoie {@code -1} si la comparaison est impossible, pour que gt/gte échouent. */
    static int compare(JsonNode actual, BigDecimal expected) {
        Integer result = compareOrNull(actual, expected);
        return result == null ? -1 : result;
    }

    static Integer compareOrNull(JsonNode actual, BigDecimal expected) {
        if (expected == null || !actual.isNumber()) {
            return null;
        }
        return actual.decimalValue().compareTo(expected);
    }

    static boolean contains(JsonNode actual, String value) {
        if (value == null) {
            return false;
        }
        if (actual.isArray()) {
            for (JsonNode element : actual) {
                if (element.asText().equals(value)) {
                    return true;
                }
            }
            return false;
        }
        return actual.isValueNode() && actual.asText().equals(value);
    }
}
