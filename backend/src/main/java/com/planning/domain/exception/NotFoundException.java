package com.planning.domain.exception;

import java.util.UUID;

/** Une entité référencée n'existe pas. */
public class NotFoundException extends PlanningException {

    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }

    public static NotFoundException of(String what, UUID id) {
        return new NotFoundException("%s introuvable : %s".formatted(what, id));
    }
}
