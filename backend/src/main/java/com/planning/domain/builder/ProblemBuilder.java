package com.planning.domain.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.constraint.NoParams;
import com.planning.domain.constraint.ParamsCodec;
import com.planning.domain.criteria.Criterion;
import com.planning.domain.criteria.CriterionParser;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Resource;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.ScheduledActivity;
import com.planning.domain.model.Timeslot;
import com.planning.domain.placement.PlacementRegistry;
import com.planning.domain.port.ProblemSource;
import com.planning.domain.port.Rows.AvailabilityRow;
import com.planning.domain.port.Rows.ConstraintConfigRow;
import com.planning.domain.port.Rows.DemandRow;
import com.planning.domain.port.Rows.RequirementRow;
import com.planning.domain.port.Rows.ResourceRow;
import com.planning.domain.port.Rows.SlotRow;
import com.planning.domain.port.Rows.TimeModelRow;
import com.planning.domain.time.TimeHorizon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Traduit la base en objets solveur, une fois par résolution (§9).
 *
 * <p>C'est ici que vit toute la sémantique métier absente du moteur. La flèche va
 * vers {@link Schedule}, jamais l'inverse : la solution n'est pas une source dans
 * laquelle on puise, c'est un produit fini assemblé en dernier.
 */
public class ProblemBuilder {

    private final ProblemSource source;
    private final ObjectMapper mapper;
    private final ConstraintRegistry registry;
    private final ParamsCodec paramsCodec;
    private final PlacementRegistry placements;
    private final HorizonFactory horizons;
    private final AvailabilityCompiler availabilityCompiler;
    private final StartsCompiler startsCompiler;
    private final ProblemValidator validator;

    public ProblemBuilder(ProblemSource source, ObjectMapper mapper, ConstraintRegistry registry,
                          ParamsCodec paramsCodec, PlacementRegistry placements,
                          HorizonFactory horizons, AvailabilityCompiler availabilityCompiler,
                          StartsCompiler startsCompiler, ProblemValidator validator) {
        this.source = source;
        this.mapper = mapper;
        this.registry = registry;
        this.paramsCodec = paramsCodec;
        this.placements = placements;
        this.horizons = horizons;
        this.availabilityCompiler = availabilityCompiler;
        this.startsCompiler = startsCompiler;
        this.validator = validator;
    }

    /** Le problème prêt à résoudre, et le livre de poids qui explique son score. */
    public record BuiltProblem(Schedule schedule, WeightBook weights) {
    }

    private record PoolKey(UUID typeId, String canonicalCriteria) {
    }

    public BuiltProblem build(UUID scenarioId) {

        // ── 1. Chargement ──────────────────────────────────────────────────
        TimeModelRow model = source.timeModel(scenarioId);
        List<SlotRow> slotRows = source.slots(model.id());
        TimeHorizon horizon = horizons.create(model, slotRows);
        List<Timeslot> slots = horizon.slots();

        Map<UUID, List<AvailabilityRow>> availability = source.availabilityByResource(scenarioId);
        Map<UUID, List<com.planning.domain.port.Rows.AbsenceRow>> absences = source.absences(scenarioId)
                .stream().collect(Collectors.groupingBy(com.planning.domain.port.Rows.AbsenceRow::resourceId));
        List<Resource> resources = source.resources(scenarioId).stream()
                .map(row -> new Resource(
                        row.id(), row.typeId(), row.label(),
                        availabilityCompiler.compile(row, availability.get(row.id()), horizon,
                                absences.getOrDefault(row.id(), List.of())),
                        withTypeCode(row.attributes(), row.typeCode())))
                .toList();

        WeightBook weights = WeightBook.of(registry, source.constraintWeights(scenarioId));
        List<ConstraintConfig> configs = loadConfigs(scenarioId, weights);

        // ── 2. Index et caches ─────────────────────────────────────────────
        Map<UUID, List<Resource>> byType = resources.stream()
                .collect(Collectors.groupingBy(Resource::typeId));
        Map<PoolKey, List<Resource>> poolCache = new HashMap<>();
        Map<Integer, List<Timeslot>> startCache = new HashMap<>();

        List<ScheduledActivity> activities = new ArrayList<>();
        List<ResourceBooking> bookings = new ArrayList<>();

        // ── 3. Génération des entités ──────────────────────────────────────
        // Le solveur ne crée jamais d'entités : l'ensemble est figé ici (§18.2).
        for (DemandRow demand : source.demands(scenarioId)) {

            int durationSlots = horizon.grid().toSlots(demand.durationMinutes());
            List<Timeslot> feasible = startCache.computeIfAbsent(demand.durationMinutes(),
                    duration -> startsCompiler.feasibleStarts(horizon, duration));

            for (int occurrence = 0; occurrence < demand.occurrences(); occurrence++) {

                List<Timeslot> starts = placements.resolve(demand.placement())
                        .restrict(feasible, demand.placement(), occurrence, horizon);

                ScheduledActivity activity = new ScheduledActivity(
                        UUID.randomUUID(), demand.id(), demand.label(), occurrence,
                        demand.durationMinutes(), durationSlots, demand.attributes(), starts);
                activity.setTimeSlots(slots);
                activities.add(activity);

                List<ResourceBooking> activityBookings = new ArrayList<>();
                for (RequirementRow requirement : demand.requirements()) {

                    // Le type est le premier filtre, le plus grossier ; les
                    // critères affinent. Le vivier est mutualisé entre requirements
                    // de clé identique : avec 10 000 bookings, la différence entre
                    // partager et dupliquer se compte en centaines de mégaoctets.
                    List<Resource> pool = poolCache.computeIfAbsent(
                            new PoolKey(requirement.typeId(), CriterionParser.canonical(requirement.criteria())),
                            key -> {
                                Criterion criteria = CriterionParser.parse(requirement.criteria());
                                return byType.getOrDefault(requirement.typeId(), List.of()).stream()
                                        .filter(criteria::matches)
                                        .toList();
                            });

                    String summary = CriterionParser.canonical(requirement.criteria());
                    for (int k = 0; k < requirement.quantity(); k++) {
                        activityBookings.add(new ResourceBooking(
                                UUID.randomUUID(), activity, requirement.role(), pool, summary));
                    }
                }
                activity.setBookings(activityBookings);
                bookings.addAll(activityBookings);
            }
        }

        // ── 4. Assemblage ──────────────────────────────────────────────────
        Schedule schedule = new Schedule();
        schedule.setTimeslots(slots);
        schedule.setResources(resources);
        schedule.setConfigs(configs);
        schedule.setActivities(activities);
        schedule.setBookings(bookings);
        schedule.setHorizon(horizon);
        schedule.setConstraintWeightOverrides(weights.overrides());

        // ── 5. Validation (§15) ────────────────────────────────────────────
        validator.check(schedule, registry);

        return new BuiltProblem(schedule, weights);
    }

    /**
     * Charge les règles configurées et fabrique une ligne synthétique par plugin
     * structurel.
     *
     * <p>Cette ligne synthétique n'a aucun effet sur le solveur — un plugin
     * structurel ne joint jamais {@code ConstraintConfig} — mais elle rend la
     * traversée d'explicabilité uniforme : chaque plugin est toujours interrogé
     * avec au moins une configuration. Sans elle, les violations structurelles
     * seraient absentes du rapport alors qu'elles comptent dans le score, et le
     * test de cohérence du §16.5 échouerait.
     */
    private List<ConstraintConfig> loadConfigs(UUID scenarioId, WeightBook weights) {
        List<ConstraintConfig> configs = new ArrayList<>();

        for (ConstraintConfigRow row : source.enabledConstraintConfigs(scenarioId)) {
            ConstraintPlugin plugin = registry.get(row.type());
            if (!plugin.configDriven()) {
                throw new IllegalArgumentException("La règle structurelle " + row.type()
                        + " se règle par son poids, pas par une configuration.");
            }
            configs.add(new ConstraintConfig(
                    row.id(),
                    row.type(),
                    CriterionParser.parse(row.scope()),
                    paramsCodec.decode(plugin, row.params()),
                    row.weight(),
                    true,
                    weights.levelOf(row.type())));
        }

        for (ConstraintPlugin plugin : registry.structural()) {
            configs.add(new ConstraintConfig(
                    syntheticId(scenarioId, plugin.type()),
                    plugin.type(),
                    new Criterion.Always(),
                    NoParams.INSTANCE,
                    1,
                    true,
                    weights.levelOf(plugin.type())));
        }
        return List.copyOf(configs);
    }

    /** Identifiant stable et reproductible, pour que deux constructions coïncident. */
    private UUID syntheticId(UUID scenarioId, String type) {
        return UUID.nameUUIDFromBytes((scenarioId + "/" + type).getBytes());
    }

    /** Voir {@link AttributeProjection} — partagée avec l'aperçu de scope de l'API. */
    private JsonNode withTypeCode(JsonNode attributes, String typeCode) {
        return AttributeProjection.withTypeCode(mapper, attributes, typeCode);
    }
}
