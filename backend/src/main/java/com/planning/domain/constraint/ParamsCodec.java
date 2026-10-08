package com.planning.domain.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.planning.domain.exception.InvalidConfigException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Désérialise et valide les {@code params} d'une ligne de configuration.
 *
 * <p>Le typage a lieu une fois, au chargement : aucun parsing JSON ne descend dans
 * la boucle chaude (§7.7). La validation contre le JSON Schema du plugin est
 * faite ici <em>et</em> à la saisie côté API — une erreur de configuration ne doit
 * pas faire échouer une résolution quinze jours plus tard (§15, contrôle 8).
 */
public class ParamsCodec {

    private final ObjectMapper mapper;
    private final JsonSchemaFactory schemaFactory =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    public ParamsCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Valide sans désérialiser — utilisé par l'API à la saisie. */
    public void check(ConstraintPlugin plugin, JsonNode params) {
        JsonNode payload = params == null || params.isNull() ? mapper.createObjectNode() : params;
        JsonSchema schema = schemaCache.computeIfAbsent(plugin.type(),
                type -> schemaFactory.getSchema(plugin.paramSchema()));

        Set<ValidationMessage> problems = schema.validate(payload);
        if (!problems.isEmpty()) {
            throw new InvalidConfigException(
                    "Paramètres invalides pour « %s » : %s".formatted(plugin.type(),
                            problems.stream().map(ValidationMessage::getMessage)
                                    .collect(Collectors.joining(" ; "))));
        }
    }

    public Object decode(ConstraintPlugin plugin, JsonNode params) {
        if (plugin.paramsType() == NoParams.class) {
            return NoParams.INSTANCE;
        }
        check(plugin, params);
        // Un plugin sans réglage déclare un record vide. Jackson sait le
        // construire, mais passer par la réflexion évite d'en dépendre pour un cas
        // qui n'a rien à désérialiser.
        if (plugin.paramsType().isRecord() && plugin.paramsType().getRecordComponents().length == 0) {
            try {
                return plugin.paramsType().getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new InvalidConfigException(
                        "Impossible d'instancier les paramètres de « %s ».".formatted(plugin.type()));
            }
        }
        JsonNode payload = params == null || params.isNull() ? mapper.createObjectNode() : params;
        try {
            return mapper.treeToValue(payload, plugin.paramsType());
        } catch (Exception e) {
            throw new InvalidConfigException(
                    "Paramètres illisibles pour « %s » : %s".formatted(plugin.type(), e.getMessage()));
        }
    }
}
