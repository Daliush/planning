package com.planning.domain.exception;

/**
 * Une durée n'est pas représentable sur la grille, ou la grille elle-même est
 * inexploitable. C'est le contrôle qui rend le changement de granularité sûr
 * (§10.4) : passer de 30 à 45 minutes lève une exception explicite plutôt que de
 * produire un planning faux.
 */
public class IncompatibleTimeModelException extends PlanningException {

    public IncompatibleTimeModelException(String message) {
        super("INCOMPATIBLE_TIME_MODEL", message);
    }
}
