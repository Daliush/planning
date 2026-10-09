package com.planning.domain.constraint;

import com.planning.domain.constraint.plugin.BookingUnassignedConstraint;
import com.planning.domain.constraint.plugin.DaySpanMaxConstraint;
import com.planning.domain.constraint.plugin.ContractLoadConstraint;
import com.planning.domain.constraint.plugin.DemandSpreadConstraint;
import com.planning.domain.constraint.plugin.GapsAvoidConstraint;
import com.planning.domain.constraint.plugin.LoadBalanceConstraint;
import com.planning.domain.constraint.plugin.MaxLoadConstraint;
import com.planning.domain.constraint.plugin.MinLoadConstraint;
import com.planning.domain.constraint.plugin.PreferredWindowConstraint;
import com.planning.domain.constraint.plugin.ResourceAvailabilityConstraint;
import com.planning.domain.constraint.plugin.ResourceConflictConstraint;
import com.planning.domain.constraint.plugin.ResourceConsistencyConstraint;
import com.planning.domain.constraint.plugin.ResourceDiscouragedSlotConstraint;
import com.planning.domain.constraint.plugin.RestMinConstraint;
import com.planning.domain.constraint.plugin.SequenceConsecutiveMaxConstraint;
import com.planning.domain.constraint.plugin.WeeklyRegularityConstraint;
import com.planning.domain.constraint.plugin.WeekendFairnessConstraint;
import com.planning.domain.constraint.plugin.WorkDaysMaxConstraint;
import com.planning.domain.exception.UnknownConstraintException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le catalogue des formes de raisonnement (§12.1).
 *
 * <p><strong>Un seul registre.</strong> Il n'existe aucune taxonomie « contraintes
 * générales » contre « contraintes par ressource » : tous les plugins implémentent
 * la même interface et vivent ici. Ce qui varie est uniquement où chaque plugin va
 * lire ses paramètres, et de nombreux plugins lisent les deux sources (§2.6).
 *
 * <p>Le registre est un singleton sans état : les plugins sont des objets
 * immuables et sans dépendance, ce qui permet à
 * {@link GenericConstraintProvider} — instancié par Timefold, hors du contexte
 * Spring — d'y accéder sans injection.
 */
public final class ConstraintRegistry {

    private static final ConstraintRegistry STANDARD = new ConstraintRegistry(List.of(
            new ResourceConflictConstraint(),
            new ResourceAvailabilityConstraint(),
            new ResourceDiscouragedSlotConstraint(),
            new ResourceConsistencyConstraint(),
            new BookingUnassignedConstraint(),
            new MaxLoadConstraint(),
            new MinLoadConstraint(),
            new LoadBalanceConstraint(),
            new DemandSpreadConstraint(),
            new GapsAvoidConstraint(),
            new DaySpanMaxConstraint(),
            new PreferredWindowConstraint(),
            new WeeklyRegularityConstraint(),
            new SequenceConsecutiveMaxConstraint(),
            new RestMinConstraint(),
            new WorkDaysMaxConstraint(),
            new ContractLoadConstraint(),
            new WeekendFairnessConstraint()));

    private final Map<String, ConstraintPlugin> plugins = new LinkedHashMap<>();

    public ConstraintRegistry(List<ConstraintPlugin> plugins) {
        plugins.forEach(plugin -> {
            ConstraintPlugin previous = this.plugins.put(plugin.type(), plugin);
            if (previous != null) {
                throw new IllegalStateException(
                        "Deux plugins déclarent le type « %s » : %s et %s."
                                .formatted(plugin.type(), previous.getClass().getSimpleName(),
                                        plugin.getClass().getSimpleName()));
            }
        });
    }

    public static ConstraintRegistry standard() {
        return STANDARD;
    }

    public ConstraintPlugin get(String type) {
        ConstraintPlugin plugin = plugins.get(type);
        if (plugin == null) {
            throw new UnknownConstraintException(
                    "La contrainte « %s » n'existe pas dans le registre. Disponibles : %s"
                            .formatted(type, String.join(", ", plugins.keySet())));
        }
        return plugin;
    }

    public boolean contains(String type) {
        return plugins.containsKey(type);
    }

    public List<ConstraintPlugin> all() {
        return List.copyOf(plugins.values());
    }

    /** Les plugins qui s'appliquent sans ligne de configuration. */
    public List<ConstraintPlugin> structural() {
        return plugins.values().stream().filter(plugin -> !plugin.configDriven()).toList();
    }
}
