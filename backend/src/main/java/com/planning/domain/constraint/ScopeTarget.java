package com.planning.domain.constraint;

/**
 * Contre quoi le {@code scope} d'une règle est évalué.
 *
 * <p>Le moteur de critères est le même, la question diffère : la plupart des
 * règles parlent des ressources — « les vacataires » — mais certaines parlent de
 * la demande — « les maths plutôt le matin ». Un scope évalué contre les mauvais
 * attributs ne matche rien, <strong>sans lever d'erreur</strong>.
 *
 * <p>L'interface de configuration a besoin de cette information pour proposer les
 * bons attributs et interroger le bon aperçu.
 */
public enum ScopeTarget {
    RESOURCE,
    DEMAND
}
