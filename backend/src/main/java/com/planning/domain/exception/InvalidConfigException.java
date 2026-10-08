package com.planning.domain.exception;

/**
 * Les {@code params} d'une règle ne respectent pas le JSON Schema de son plugin,
 * ou un {@code scope} / {@code criteria} est mal formé.
 *
 * <p>Ce contrôle se fait deux fois (§15) : à la saisie via l'API, pour un message
 * immédiat, et au chargement comme filet de sécurité. Une erreur de configuration
 * ne doit pas faire échouer une résolution quinze jours plus tard.
 */
public class InvalidConfigException extends PlanningException {

    public InvalidConfigException(String message) {
        super("INVALID_CONFIG", message);
    }
}
