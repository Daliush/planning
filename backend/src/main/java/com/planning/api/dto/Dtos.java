package com.planning.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.model.BusinessProfile;
import com.planning.domain.model.Effect;
import com.planning.domain.model.HorizonKind;
import com.planning.domain.model.Level;
import com.planning.domain.model.SolveReport;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les contrats d'API. Regroupés dans un porteur unique : ce sont des projections
 * de transport, pas des objets métier, et les disperser en quarante fichiers
 * n'aiderait personne à les lire.
 *
 * <p>Ils ne portent jamais de logique : la validation métier vit dans les
 * services, les contraintes SQL sont un filet de sécurité.
 */
public final class Dtos {

    private Dtos() {
    }

    // ─── Référentiel ────────────────────────────────────────────────────────

    public record TenantDto(UUID id, String code, String label, String profile) {
    }

    public record ResourceTypeDto(UUID id, UUID tenantId, String code, String label, JsonNode attrSchema) {
    }

    public record ResourceTypeInput(String code, String label, JsonNode attrSchema) {
    }

    public record TimeModelDto(UUID id, UUID tenantId, String label, HorizonKind horizonKind,
                               Integer cycleDays, int maxGapMinutes, int slotCount) {
    }

    /**
     * Génère une grille régulière. La table {@code slot_template} reste la source
     * de vérité : ce générateur ne fait que la remplir en trois lignes quand une
     * grille régulière suffit (§6.7).
     */
    public record TimeModelInput(String label, int cycleDays, int maxGapMinutes,
                                 int workingDays, LocalTime dayStart, int slotMinutes,
                                 int slotsPerDay, List<BreakInput> breaks) {
    }

    public record BreakInput(int afterOrdinal, int minutes) {
    }

    public record SlotDto(int index, int dayInCycle, int ordinal, LocalTime startTime, LocalTime endTime) {
    }

    /**
     * @param profile la verticale du scénario, ou {@code null} pour « ne rien
     *                filtrer ». Purement documentaire pour le moteur : elle ne
     *                choisit que le vocabulaire d'attributs proposé par l'interface.
     */
    public record ScenarioDto(UUID id, UUID tenantId, String label, UUID timeModelId,
                              String timeModelLabel, String status, UUID parentId,
                              BusinessProfile profile, Instant createdAt) {
    }

    public record ScenarioInput(UUID tenantId, String label, UUID timeModelId, UUID parentId,
                                BusinessProfile profile) {
    }

    public record CalendarWeekDto(int weekNumber, boolean worked, int cycleOffset) {
    }

    // ─── Ressources ─────────────────────────────────────────────────────────

    public record AvailabilityDto(UUID id, Effect effect, Integer dayInCycle,
                                  LocalTime startTime, LocalTime endTime, int priority) {
    }

    public record AvailabilityInput(Effect effect, Integer dayInCycle,
                                    LocalTime startTime, LocalTime endTime, Integer priority) {
    }

    public record ResourceDto(UUID id, UUID scenarioId, UUID typeId, String typeCode,
                              String label, Effect defaultEffect, JsonNode attributes,
                              List<AvailabilityDto> availability) {
    }

    public record ResourceInput(UUID typeId, String label, Effect defaultEffect, JsonNode attributes) {
    }

    // ─── Demande ────────────────────────────────────────────────────────────

    public record RequirementDto(UUID id, UUID demandId, String role, UUID typeId,
                                 String typeCode, int quantity, JsonNode criteria) {
    }

    public record RequirementInput(String role, UUID typeId, Integer quantity, JsonNode criteria) {
    }

    public record DemandDto(UUID id, UUID scenarioId, String label, int durationMinutes,
                            int occurrences, JsonNode attributes, JsonNode placement,
                            List<RequirementDto> requirements) {
    }

    public record DemandInput(String label, int durationMinutes, int occurrences,
                              JsonNode attributes, JsonNode placement) {
    }

    // ─── Règles ─────────────────────────────────────────────────────────────

    public record ConstraintConfigDto(UUID id, UUID scenarioId, String type, String typeLabel,
                                      JsonNode scope, JsonNode params, int weight, boolean enabled) {
    }

    public record ConstraintConfigInput(String type, JsonNode scope, JsonNode params,
                                        Integer weight, Boolean enabled) {
    }

    public record ConstraintWeightDto(String constraintId, String label, Level level,
                                      int weight, boolean fromDefault) {
    }

    public record ConstraintWeightInput(String constraintId, Level level, int weight) {
    }

    // ─── Catalogue ──────────────────────────────────────────────────────────

    /**
     * @param scopeTarget {@code RESOURCE} ou {@code DEMAND} — contre quoi le scope
     *                    est évalué. L'interface en a besoin pour proposer les bons
     *                    attributs : un scope évalué contre les mauvais ne matche
     *                    rien, sans lever d'erreur.
     */
    public record PluginDto(String type, String label, String description, Level defaultLevel,
                            boolean configDriven, String scopeTarget, List<String> horizons,
                            JsonNode paramSchema) {
    }

    public record PlacementStrategyDto(String id, JsonNode paramSchema) {
    }

    /**
     * Un attribut métier proposable sur une ressource.
     *
     * @param readBy les plugins qui lisent cette clé directement. Calculé depuis
     *               {@code ConstraintPlugin.readsAttributes()}, jamais saisi : une
     *               liste tenue à la main divergerait du code qu'elle décrit.
     *               Vide dans l'immense majorité des cas — les règles passent par
     *               les scopes, qui sont des données.
     */
    public record AttributeDefinitionDto(String key, String title, String description,
                                         String kind, List<String> options, Integer minimum,
                                         List<String> profiles, List<String> resourceTypes,
                                         List<String> readBy) {
    }

    public record CatalogDto(List<PluginDto> plugins, List<PlacementStrategyDto> placements,
                             List<String> criterionOperators, List<String> periods,
                             List<String> effects, List<AttributeDefinitionDto> attributes) {
    }

    // ─── Aperçu de scope ────────────────────────────────────────────────────

    /** {@code target} vaut {@code RESOURCE} ou {@code DEMAND} selon ce que la règle vise. */
    public record ScopePreviewRequest(JsonNode scope, String target) {
    }

    public record ScopeMatchDto(UUID id, String label, String typeCode) {
    }

    /**
     * Combien de ressources — ou de demandes — une règle vise réellement.
     *
     * <p>Un scope qui ne matche personne ne lève aucune erreur : c'est le mode de
     * défaillance le plus silencieux du moteur de critères. Cet aperçu le rend
     * visible au moment de la saisie.
     */
    public record ScopePreviewDto(int matched, int total, List<ScopeMatchDto> samples) {
    }

    // ─── Résolution ─────────────────────────────────────────────────────────

    public record SolveRequest(Integer timeLimitSeconds) {
    }

    public record SolveJobDto(UUID id, UUID scenarioId, String status, int timeLimitSeconds,
                              Integer hardScore, Integer softScore, boolean feasible,
                              Instant createdAt, Instant startedAt, Instant finishedAt,
                              String errorCode, String errorDetail) {
    }

    public record AssignmentDto(UUID id, UUID demandId, String demandLabel, int occurrence,
                                UUID resourceId, String resourceLabel, String resourceTypeCode,
                                String role, int dayInCycle, LocalTime startTime,
                                LocalTime endTime, boolean pinned) {
    }

    public record ResourceBriefDto(UUID id, String label, String typeCode) {
    }

    /** Tout ce qu'il faut pour dessiner la grille en une requête. */
    public record ScheduleViewDto(SolveJobDto job, int cycleDays, List<SlotDto> slots,
                                  List<ResourceBriefDto> resources, List<AssignmentDto> assignments) {
    }

    public record SolveReportDto(SolveReport report, Map<String, String> constraintLabels) {
    }
}
