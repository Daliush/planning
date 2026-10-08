package com.planning.domain.constraint;

import java.util.UUID;

/**
 * « Toutes les séances d'un cours, pour un rôle donné ».
 *
 * <p>La clé raisonne sur le <strong>rôle</strong> et non sur le type : un examen a
 * besoin de deux surveillants, et un surveillant peut être un enseignant ou un
 * membre du personnel administratif (§6.10).
 */
public record ConsistencyKey(UUID demandId, String role) {
}
