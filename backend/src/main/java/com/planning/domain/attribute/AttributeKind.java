package com.planning.domain.attribute;

/**
 * La forme d'un attribut métier — ce qui détermine le widget du formulaire et
 * l'opérateur de critère qui a du sens sur lui.
 *
 * <p>La distinction {@link #TEXT_LIST} / {@link #TEXT} n'est pas cosmétique :
 * elle décide entre {@code contains} et {@code eq}. Tester l'égalité sur un
 * tableau ne matche jamais rien, et ne lève aucune erreur — c'est le piège du
 * §7.8, et c'est le catalogue qui le retire des mains de l'utilisateur.
 */
public enum AttributeKind {

    /** Une chaîne libre — un code, un bâtiment. */
    TEXT,

    /** Plusieurs chaînes — des matières, des compétences. Interrogé avec {@code contains}. */
    TEXT_LIST,

    /** Un entier — une capacité, un effectif. */
    INTEGER,

    /** Un entier exprimé en minutes, affiché « 8 h 00 ». Jamais en créneaux (§5.3). */
    DURATION_MINUTES,

    /** Une valeur parmi une liste fermée — un statut de contrat. */
    ENUM
}
