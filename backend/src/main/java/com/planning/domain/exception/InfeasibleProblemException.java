package com.planning.domain.exception;

/**
 * Le problème est mal formé : un vivier vide, ou une activité sans départ valide.
 *
 * <p>PIÈGE §2.4 : une exclusion par vivier est définitive et silencieuse. Si aucun
 * candidat ne subsiste, le solveur échoue à l'initialisation sans jamais dire
 * pourquoi — d'où le rejet explicite avant {@code solve()}.
 */
public class InfeasibleProblemException extends PlanningException {

    public InfeasibleProblemException(String code, String message) {
        super(code, message);
    }

    public static InfeasibleProblemException emptyPool(String message) {
        return new InfeasibleProblemException("INFEASIBLE_EMPTY_POOL", message);
    }

    public static InfeasibleProblemException noValidStart(String message) {
        return new InfeasibleProblemException("INFEASIBLE_NO_START", message);
    }
}
