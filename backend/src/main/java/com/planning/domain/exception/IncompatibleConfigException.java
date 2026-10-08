package com.planning.domain.exception;

/** Une règle configurée n'a pas de sens sur l'horizon du scénario (§14.5). */
public class IncompatibleConfigException extends PlanningException {

    public IncompatibleConfigException(String message) {
        super("INCOMPATIBLE_CONFIG", message);
    }
}
