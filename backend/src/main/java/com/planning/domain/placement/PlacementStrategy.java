package com.planning.domain.placement;

import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.model.Timeslot;
import com.planning.domain.time.TimeHorizon;

import java.util.List;

/**
 * {@code feasibleStarts} calcule ce qui est <em>matériellement possible</em> ; la
 * stratégie restreint ensuite selon ce qui est <em>voulu</em> (§10.5).
 *
 * <p>PIÈGE §10.6 : ne jamais mettre dans le vivier une règle qui <em>ressemble</em>
 * à une préférence. L'utilisateur verrait « aucune solution » sans jamais savoir
 * quelle règle bloque, alors qu'une contrainte souple violée apparaît noir sur
 * blanc dans le rapport. « Les maths plutôt le matin » est un plugin, pas une
 * fenêtre de placement.
 */
public interface PlacementStrategy {

    String id();

    /** JSON Schema des réglages, exposé à l'API pour valider la saisie. */
    JsonNode paramSchema();

    List<Timeslot> restrict(List<Timeslot> feasible, JsonNode placement, int occurrence,
                            TimeHorizon horizon);
}
