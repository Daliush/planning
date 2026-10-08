package com.planning.domain.exception;

/**
 * Base des erreurs métier du moteur.
 *
 * <p>INVARIANT §2.5 : tout ce que le compilateur ne vérifie plus, le chargeur doit
 * le vérifier — <strong>bruyamment</strong>, avec un message métier exploitable.
 * Le {@code code} est celui qu'on retrouve dans {@code solve_job.error_code} et
 * que le skill {@code infeasibility-diagnoser} consulte en premier.
 */
public abstract class PlanningException extends RuntimeException {

    private final String code;

    protected PlanningException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
