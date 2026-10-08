package com.planning.domain.exception;

/**
 * Un poids ou une configuration référence un plugin absent du registre.
 *
 * <p>INVARIANT §11.1 : {@code type()} est un contrat. Renommer {@code load.max}
 * laisserait toutes les lignes existantes pointer dans le vide, silencieusement —
 * on ne renomme pas un identifiant de plugin, on le déprécie.
 */
public class UnknownConstraintException extends PlanningException {

    public UnknownConstraintException(String message) {
        super("UNKNOWN_CONSTRAINT", message);
    }
}
