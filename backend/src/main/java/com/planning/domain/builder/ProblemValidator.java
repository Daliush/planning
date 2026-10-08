package com.planning.domain.builder;

import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.exception.InfeasibleProblemException;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Resource;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.ScheduledActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La passe de validation obligatoire (§15), exécutée <strong>avant chaque
 * {@code solve()}</strong> et jamais dans une contrainte.
 *
 * <p>Un {@code throw} au chargement coûte quelques microsecondes ; la même
 * vérification en contrainte serait rejouée des millions de fois pour ne rien
 * détecter dans la quasi-totalité des cas.
 *
 * <p>C'est la contrepartie du §2.5 : dans un modèle générique piloté par la
 * donnée, les erreurs que le compilateur ne peut plus voir deviennent possibles
 * <em>et silencieuses</em>. Tout ce qu'il ne vérifie plus, le chargeur doit le
 * vérifier bruyamment, avec un message métier exploitable.
 */
public class ProblemValidator {

    private static final Logger log = LoggerFactory.getLogger(ProblemValidator.class);

    public void check(Schedule schedule, ConstraintRegistry registry) {
        checkStarts(schedule);
        checkPools(schedule);
        checkPoolTypes(schedule);
        checkConfigs(schedule, registry);
        logPoolSizes(schedule);
    }

    /** Contrôle 2 — aucune activité sans départ valide. */
    private void checkStarts(Schedule schedule) {
        for (ScheduledActivity activity : schedule.getActivities()) {
            if (activity.getValidStarts().isEmpty()) {
                throw InfeasibleProblemException.noValidStart(
                        ("« %s » dure %d min : aucun créneau de départ ne convient sur cette grille. "
                                + "Vérifier que la durée est un multiple du pas, qu'elle tient dans une "
                                + "plage d'ouverture continue, et que la fenêtre de placement n'est pas "
                                + "trop étroite.")
                                .formatted(activity.getDemandLabel(), activity.getDurationMinutes()));
            }
        }
    }

    /**
     * Contrôle 1 — aucun vivier vide.
     *
     * <p>PIÈGE §2.4 : sans ce contrôle, le solveur échouerait à l'initialisation
     * sans jamais dire pourquoi. C'est la contrepartie obligatoire d'une exclusion
     * par vivier, qui est rapide, définitive et muette.
     */
    private void checkPools(Schedule schedule) {
        for (ResourceBooking booking : schedule.getBookings()) {
            if (booking.getEligible().isEmpty()) {
                throw InfeasibleProblemException.emptyPool(
                        ("Aucune ressource ne satisfait le besoin « %s » de « %s ». Critères : %s. "
                                + "Vérifier le type visé, la présence réelle des attributs cités, et "
                                + "l'usage de « contains » plutôt que « eq » sur un tableau.")
                                .formatted(booking.getRole(),
                                        booking.getActivity().getDemandLabel(),
                                        booking.getCriteriaSummary()));
            }
        }
    }

    /** Contrôle 3 — cohérence de type dans chaque vivier. Un échec ici est un bug du builder. */
    private void checkPoolTypes(Schedule schedule) {
        for (ResourceBooking booking : schedule.getBookings()) {
            List<Resource> pool = booking.getEligible();
            UUID expected = pool.getFirst().typeId();
            for (Resource resource : pool) {
                if (!resource.typeId().equals(expected)) {
                    throw new IllegalStateException(
                            "Vivier hétérogène pour le rôle « %s » de « %s » : %s et %s n'ont pas le même type."
                                    .formatted(booking.getRole(),
                                            booking.getActivity().getDemandLabel(),
                                            pool.getFirst().label(), resource.label()));
                }
            }
        }
    }

    /**
     * Contrôle 7 — chaque configuration est compatible avec l'horizon, et ses
     * paramètres tiennent la route.
     *
     * <p>Ce contrôle mérite une attention particulière : sans lui, une ligne
     * référençant un plugin filtré par l'horizon serait ignorée <em>sans un mot</em>,
     * et l'utilisateur croirait sa règle appliquée.
     */
    private void checkConfigs(Schedule schedule, ConstraintRegistry registry) {
        for (ConstraintConfig config : schedule.getConfigs()) {
            var plugin = registry.get(config.type());
            if (!plugin.horizons().contains(schedule.getHorizon().kind())) {
                throw new com.planning.domain.exception.IncompatibleConfigException(
                        "« %s » est configurée mais incompatible avec un horizon %s."
                                .formatted(config.type(), schedule.getHorizon().kind()));
            }
            plugin.validate(config, schedule.getHorizon());
        }
    }

    /**
     * Contrôle complémentaire non bloquant (§10.7). Une activité avec 2 départs
     * valides sur 110 créneaux, ou un vivier de 3 ressources pour 40 bookings
     * simultanés, est le premier signal à consulter quand un planning ne converge
     * pas.
     */
    private void logPoolSizes(Schedule schedule) {
        if (!log.isInfoEnabled()) {
            return;
        }
        int slots = schedule.getTimeslots().size();
        log.info("Problème construit : {} créneaux, {} ressources, {} séances, {} postes, {} règles",
                slots, schedule.getResources().size(), schedule.getActivities().size(),
                schedule.getBookings().size(), schedule.getConfigs().size());

        Map<String, Integer> smallestPoolByRole = new HashMap<>();
        for (ResourceBooking booking : schedule.getBookings()) {
            smallestPoolByRole.merge(booking.getRole(), booking.getEligible().size(), Math::min);
        }
        smallestPoolByRole.forEach((role, size) ->
                log.info("  vivier minimal pour le rôle « {} » : {} ressource(s)", role, size));

        schedule.getActivities().stream()
                .filter(activity -> activity.getValidStarts().size() * 10 < slots)
                .forEach(activity -> log.warn(
                        "  « {} » n'a que {} départ(s) valide(s) sur {} créneaux : configuration serrée",
                        activity.getDemandLabel(), activity.getValidStarts().size(), slots));
    }
}
