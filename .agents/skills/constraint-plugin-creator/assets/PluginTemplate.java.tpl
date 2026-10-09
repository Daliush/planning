package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.constraint.AggregationScope;
import com.planning.domain.constraint.Bookings;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.Schemas;
import com.planning.domain.constraint.Streams;
import com.planning.domain.exception.IncompatibleConfigException;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Level;
import com.planning.domain.model.Period;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.Violation;
import com.planning.domain.model.PeriodBucket;
import com.planning.domain.time.TimeHorizon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exemple de plafond avec charge ventilée sur les périodes CYCLIC et DATED.
 * Un minimum doit aussi considérer les périodes vides (voir MinLoadConstraint).
 * {{PHRASE_METIER}}
 *
 * <p>{{POURQUOI_CE_PLUGIN_EXISTE — ce qui se passerait sans lui}}
 */
public final class {{ClassName}}Constraint implements ConstraintPlugin {

    public static final String TYPE = "{{domaine.regle}}";

    public record Params({{int seuil, Period period}}) {
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  LOGIQUE MÉTIER — ÉCRITE UNE SEULE FOIS, APPELÉE PAR LES DEUX TRAVERSÉES
    //
    //  Ne jamais recopier le seuil, la clé ou la formule dans les traversées :
    //  une correction appliquée d'un seul côté rendrait le rapport utilisateur
    //  faux sans qu'aucun signal ne l'indique.
    // ═══════════════════════════════════════════════════════════════════════

    private static Params params(ConstraintConfig rule) {
        return rule.paramsAs(Params.class);
    }

    /**
     * La clé d'agrégation. Sa composition définit la sémantique : retirer la
     * ressource transforme un quota individuel en quota d'équipe, retirer la
     * période un quota journalier en quota sur tout l'horizon.
     */
    private static AggregationScope scopeOf(ResourceBooking booking, PeriodBucket bucket) {
        return new AggregationScope(booking.getResource(), bucket.period(), bucket.index());
    }

    private static boolean violates({{int total}}, Params params) {
        return {{total > params.seuil()}};
    }

    /** Poids ② × poids ③. Le poids ① est appliqué par Timefold, pas ici. */
    private static int penalty({{int total}}, Params params, int ruleWeight) {
        return ruleWeight * {{(total - params.seuil())}};
    }

    // ═══ IDENTITÉ ═══

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String label() {
        return "{{Libellé affiché}}";
    }

    @Override
    public String description() {
        return "{{Ce que la règle exprime, en vocabulaire métier.}}";
    }

    @Override
    public Level defaultLevel() {
        return Level.{{SOFT}};
    }

    @Override
    public Class<?> paramsType() {
        return Params.class;
    }

    @Override
    public JsonNode paramSchema() {
        return Schemas.object(Map.of(
                "{{seuil}}", Schemas.minutes("{{description du seuil}}", 1),
                "period", Schemas.period("{{découpage}}")),
                List.of("{{seuil}}", "period"));
    }

    // Décommenter si la règle n'a de sens que sur un mode d'horizon.
    // @Override
    // public Set<HorizonKind> horizons() {
    //     return EnumSet.of(HorizonKind.CYCLIC);
    // }

    /**
     * Validation fine au chargement. La restriction porte souvent sur le couple
     * plugin-paramètre, pas sur le plugin.
     */
    @Override
    public void validate(ConstraintConfig config, TimeHorizon horizon) {
        Period period = params(config).period();
        if (!horizon.supports(period)) {
            throw new IncompatibleConfigException(
                    "La période %s n'est pas disponible sur un horizon %s."
                            .formatted(period, horizon.kind()));
        }
    }

    // ═══ TRAVERSÉE A — incrémentale, pour le solveur ═══

    @Override
    public Constraint build(ConstraintFactory factory) {
        return Streams.scopedBookings(factory, TYPE)
                .join(PeriodBucket.class,
                        Joiners.equal((rule, booking) -> params(rule).period(), PeriodBucket::period),
                        Joiners.filtering((rule, booking, bucket) -> Bookings.minutesIn(booking, bucket) > 0))
                .groupBy((rule, booking, bucket) -> rule,
                        (rule, booking, bucket) -> scopeOf(booking, bucket),
                        ConstraintCollectors.sum((rule, booking, bucket) -> Bookings.minutesIn(booking, bucket)))
                .filter((rule, scope, total) -> violates(total, params(rule)))
                .penalize(HardSoftScore.ONE_{{SOFT}},
                        (rule, scope, total) -> penalty(total, params(rule), rule.weight()))
                .asConstraint(TYPE);
    }

    // ═══ TRAVERSÉE B — naïve, pour l'utilisateur ═══
    //
    //  Tourne une fois sur une solution figée. C'est la seule source
    //  d'explicabilité du produit : l'édition Community ne garantit aucune API
    //  d'analyse.

    @Override
    public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        Params params = params(rule);
        Map<AggregationScope, Integer> totals = new HashMap<>();
        Map<AggregationScope, List<ResourceBooking>> members = new HashMap<>();

        // Même prédicat que la tête de pipeline : toute divergence ici fait
        // tomber le test de cohérence.
        Streams.placedBookings(solution.getBookings(), rule).forEach(booking -> {
            for (PeriodBucket bucket : solution.getPeriodBuckets()) {
                if (bucket.period() != params.period()) continue;
                int minutes = Bookings.minutesIn(booking, bucket);
                if (minutes <= 0) continue;
                AggregationScope scope = scopeOf(booking, bucket);
                totals.merge(scope, minutes, Math::addExact);
                members.computeIfAbsent(scope, key -> new ArrayList<>()).add(booking);
            }
        });

        List<Violation> violations = new ArrayList<>();
        totals.forEach((scope, total) -> {
            if (!violates(total, params)) {
                return;
            }
            violations.add(new Violation(
                    TYPE,
                    rule.level(),
                    penalty(total, params, rule.weight()),
                    // Le message utilise les mots du métier.
                    "{{%s dépasse … : %s au lieu de %s.}}"
                            .formatted(scope.resource().label(),
                                    Bookings.hoursMinutes(total),
                                    Bookings.hoursMinutes(params.{{seuil}}())),
                    // Les identifiants permettent le surlignage côté interface.
                    List.of(scope.resource().id()),
                    members.get(scope).stream()
                            .map(booking -> booking.getActivity().getDemandId())
                            .distinct().toList(),
                    // Valeurs brutes, pour que le frontend formate et trie
                    // sans réanalyser le message.
                    Map.of("total", total,
                            "{{seuil}}", params.{{seuil}}(),
                            "period", params.period().name(),
                            "periodIndex", scope.periodIndex())));
        });
        return violations;
    }
}
