package com.planning.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Une ressource planifiable (§7.4). Cinq champs, dont trois d'identité : la
 * classe générique reste minuscule, toute la richesse métier vit dans
 * {@code attributes}.
 *
 * <p>Règle d'arbitrage champ fixe / attributs : le moteur lit-il ce champ dans la
 * boucle chaude, ou seulement le builder au chargement ? C'est une ligne de
 * performance, pas une ligne conceptuelle. Le jour où {@code if (typeId == SALLE)}
 * apparaît dans le moteur, la ligne a été franchie.
 */
public record Resource(
        UUID id,
        UUID typeId,
        String label,
        TimeProfile time,
        JsonNode attributes) {

    /**
     * Identité sur l'{@code id} seul. Cet objet sert de clé d'agrégation dans les
     * {@code groupBy} : l'{@code equals} de record par défaut comparerait le
     * {@link JsonNode} en profondeur à chaque hachage — inacceptable en boucle
     * chaude.
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof Resource other && other.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return label;
    }
}
