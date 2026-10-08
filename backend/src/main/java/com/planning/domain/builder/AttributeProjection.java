package com.planning.domain.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * La projection du code de type dans les attributs (INVARIANT §6.4).
 *
 * <p>Le moteur de critères ne lit que {@code attributes} : sans cette projection,
 * un scope {@code {"eq":{"path":"/type","value":"enseignant"}}} ne matcherait
 * jamais rien, silencieusement.
 *
 * <p>Extraite du builder pour que <strong>l'aperçu de scope de l'API applique
 * exactement la même transformation</strong>. Sans ce partage, un scope portant sur
 * {@code /type} s'afficherait comme ne visant personne alors qu'il fonctionne
 * parfaitement à la résolution — l'aperçu mentirait, ce qui est pire que pas
 * d'aperçu du tout.
 */
public final class AttributeProjection {

    private AttributeProjection() {
    }

    public static JsonNode withTypeCode(ObjectMapper mapper, JsonNode attributes, String typeCode) {
        ObjectNode enriched = attributes == null || !attributes.isObject()
                ? mapper.createObjectNode()
                : attributes.deepCopy();
        enriched.put("type", typeCode);
        return enriched;
    }
}
