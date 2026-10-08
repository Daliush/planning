package com.planning.domain.criteria;

import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.model.Resource;

import java.math.BigDecimal;
import java.util.List;

/**
 * Le moteur de critères (§7.8). Un seul moteur, deux usages :
 * {@code requirement.criteria} répond « quelles ressources peuvent occuper ce
 * poste », {@code constraint_config.scope} répond « à quelles ressources
 * s'applique cette règle ». Les deux sont évalués une seule fois.
 *
 * <p>Le chemin utilise JSON Pointer (RFC 6901), la syntaxe attendue par
 * {@code JsonNode.at()}.
 *
 * <p>INVARIANT §7.8 : un plugin ne contient jamais de valeur métier en dur. Le mot
 * « maths » n'apparaît nulle part dans le code, il vit dans le {@code scope}
 * d'une ligne de configuration.
 */
public sealed interface Criterion {

    boolean test(JsonNode attributes);

    default boolean matches(Resource resource) {
        return test(resource.attributes());
    }

    // ─── Égalité ────────────────────────────────────────────────────────────

    record Eq(String path, JsonNode value) implements Criterion {
        @Override
        public boolean test(JsonNode attributes) {
            return JsonValues.equal(attributes.at(path), value);
        }
    }

    record Neq(String path, JsonNode value) implements Criterion {
        @Override
        public boolean test(JsonNode attributes) {
            JsonNode actual = attributes.at(path);
            // Un attribut absent n'est pas « différent de » : il est inconnu.
            // Le laisser matcher ferait passer toutes les ressources mal saisies.
            return !actual.isMissingNode() && !JsonValues.equal(actual, value);
        }
    }

    // ─── Comparaisons numériques ────────────────────────────────────────────

    record Gt(String path, BigDecimal value) implements Criterion {
        @Override
        public boolean test(JsonNode attributes) {
            return JsonValues.compare(attributes.at(path), value) > 0;
        }
    }

    record Gte(String path, BigDecimal value) implements Criterion {
        @Override
        public boolean test(JsonNode attributes) {
            return JsonValues.compare(attributes.at(path), value) >= 0;
        }
    }

    record Lt(String path, BigDecimal value) implements Criterion {
        @Override
        public boolean test(JsonNode attributes) {
            Integer c = JsonValues.compareOrNull(attributes.at(path), value);
            return c != null && c < 0;
        }
    }

    record Lte(String path, BigDecimal value) implements Criterion {
        @Override
        public boolean test(JsonNode attributes) {
            Integer c = JsonValues.compareOrNull(attributes.at(path), value);
            return c != null && c <= 0;
        }
    }

    // ─── Ensembles ──────────────────────────────────────────────────────────

    record In(String path, List<JsonNode> values) implements Criterion {
        @Override
        public boolean test(JsonNode attributes) {
            JsonNode actual = attributes.at(path);
            return values.stream().anyMatch(candidate -> JsonValues.equal(actual, candidate));
        }
    }

    /**
     * PIÈGE §7.8 : {@code JsonNode.has(String)} teste la présence d'une clé
     * d'objet, pas d'un élément de tableau. Sur {@code ["maths","physique"]},
     * {@code has("maths")} renvoie systématiquement {@code false} sans lever
     * d'exception. Cet opérateur itère correctement.
     */
    record Contains(String path, String value) implements Criterion {
        @Override
        public boolean test(JsonNode attributes) {
            return JsonValues.contains(attributes.at(path), value);
        }
    }

    record ContainsAll(String path, List<String> values) implements Criterion {
        @Override
        public boolean test(JsonNode attributes) {
            JsonNode actual = attributes.at(path);
            return values.stream().allMatch(value -> JsonValues.contains(actual, value));
        }
    }

    // ─── Composition ────────────────────────────────────────────────────────

    record And(List<Criterion> children) implements Criterion {
        @Override
        public boolean test(JsonNode attributes) {
            return children.stream().allMatch(child -> child.test(attributes));
        }
    }

    record Or(List<Criterion> children) implements Criterion {
        @Override
        public boolean test(JsonNode attributes) {
            return children.stream().anyMatch(child -> child.test(attributes));
        }
    }

    record Not(Criterion child) implements Criterion {
        @Override
        public boolean test(JsonNode attributes) {
            return !child.test(attributes);
        }
    }

    /** {@code {}} — s'applique à tout le monde. */
    record Always() implements Criterion {
        @Override
        public boolean test(JsonNode attributes) {
            return true;
        }
    }
}
