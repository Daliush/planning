package com.planning.domain.model;

/**
 * La verticale métier d'un tenant ou d'un scénario.
 *
 * <p>Ce n'est <strong>pas</strong> une variante du moteur : aucun plugin, aucun
 * critère et aucune contrainte ne lit cette valeur. Elle ne sert qu'à filtrer le
 * vocabulaire proposé par l'interface — quels attributs métier suggérer sur une
 * ressource. Un profil qui influencerait la résolution serait une valeur métier
 * en dur dans le noyau, exactement ce que le §2.6 interdit.
 *
 * <p>Sur un scénario, l'absence de profil ({@code null}) signifie « ne rien
 * filtrer » : tout le vocabulaire est proposé. C'est le mode expert, pour qui
 * veut poser un attribut d'entreprise sur une école.
 */
public enum BusinessProfile {
    SCHOOL,
    COMPANY,
    RESTAURANT
}
