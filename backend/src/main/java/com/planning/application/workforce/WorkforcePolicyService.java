package com.planning.application.workforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.api.dto.Dtos.ConstraintConfigInput;
import com.planning.api.dto.Dtos.ConstraintWeightInput;
import com.planning.api.dto.WorkforceDtos.PolicyDto;
import com.planning.application.ConstraintService;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.repository.entity.Entities.ConstraintConfigEntity;
import com.planning.repository.entity.Entities.ResourceEntity;
import com.planning.repository.jpa.ConstraintConfigJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Traduit les réglages métier vers les plugins du catalogue existant. */
@Service
@Transactional(readOnly = true)
public class WorkforcePolicyService {
    public static final PolicyDto DEFAULT_POLICY = new PolicyDto(660, 600, true);
    private final WorkforceAccess access;
    private final ConstraintService constraints;
    private final ConstraintConfigJpaRepository configs;
    private final ConstraintRegistry registry;
    private final ObjectMapper mapper;

    public WorkforcePolicyService(WorkforceAccess access, ConstraintService constraints,
                                   ConstraintConfigJpaRepository configs,
                                   ConstraintRegistry registry, ObjectMapper mapper) {
        this.access = access;
        this.constraints = constraints;
        this.configs = configs;
        this.registry = registry;
        this.mapper = mapper;
    }

    public PolicyDto get(UUID scenarioId) {
        List<ConstraintConfigEntity> rules = configs.findByScenarioIdOrderByType(scenarioId);
        int rest = rules.stream().filter(rule -> "rest.min".equals(rule.type))
                .findFirst().map(rule -> rule.params.path("minMinutes").asInt()).orElse(660);
        int daily = rules.stream().filter(this::dailyMaximum)
                .findFirst().map(rule -> rule.params.path("maxMinutes").asInt()).orElse(600);
        boolean balanced = rules.stream().anyMatch(rule -> "load.balance".equals(rule.type) && rule.enabled);
        return new PolicyDto(rest, daily, balanced);
    }

    @Transactional
    public PolicyDto update(UUID scenarioId, PolicyDto policy) {
        WorkforceAccess.Context context = access.mutable(scenarioId);
        validate(policy);
        for (ConstraintConfigEntity rule : configs.findByScenarioIdOrderByType(scenarioId)) {
            if ("rest.min".equals(rule.type)) {
                constraints.updateConfig(rule.id, new ConstraintConfigInput(rule.type, rule.scope,
                        mapper.createObjectNode().put("minMinutes", policy.minRestMinutes()), rule.weight, true));
            } else if (dailyMaximum(rule)) {
                constraints.updateConfig(rule.id, new ConstraintConfigInput(rule.type, rule.scope,
                        mapper.createObjectNode().put("maxMinutes", policy.maximumDailyMinutes()).put("period", "DAY"),
                        rule.weight, true));
            } else if ("load.balance".equals(rule.type)) {
                constraints.updateConfig(rule.id, new ConstraintConfigInput(rule.type, rule.scope,
                        rule.params, rule.weight, policy.balanceWorkload()));
            }
        }
        access.changed(context);
        return get(scenarioId);
    }

    @Transactional
    public void bootstrap(UUID scenarioId) {
        Map<String, Integer> selected = Map.ofEntries(
                Map.entry("resource.conflict", 1000), Map.entry("resource.availability", 1000),
                // Un poste vacant coûte moins que toute violation des protections métier.
                Map.entry("booking.unassigned", 1), Map.entry("resource.discouraged_slot", 1),
                Map.entry("rest.min", 1000), Map.entry("work.days_max", 1000),
                Map.entry("load.max", 1000), Map.entry("load.contract", 1),
                Map.entry("load.balance", 1), Map.entry("fairness.weekend", 10));
        for (var plugin : registry.all()) {
            constraints.upsertWeight(scenarioId, new ConstraintWeightInput(plugin.type(),
                    plugin.defaultLevel(), selected.getOrDefault(plugin.type(), 0)));
        }
        add(scenarioId, "rest.min", mapper.createObjectNode().put("minMinutes", 660));
        add(scenarioId, "work.days_max", mapper.createObjectNode().put("maxDays", 6));
        add(scenarioId, "load.max", mapper.createObjectNode().put("maxMinutes", 600).put("period", "DAY"));
        add(scenarioId, "load.contract", mapper.createObjectNode());
        add(scenarioId, "load.balance", mapper.createObjectNode());
        add(scenarioId, "fairness.weekend", mapper.createObjectNode());
    }

    @Transactional
    public void employeeContract(ResourceEntity employee) {
        String code = employee.attributes.path("code").asText();
        deleteEmployeeContract(employee.scenarioId, code);
        JsonNode scope = mapper.createObjectNode().set("eq",
                mapper.createObjectNode().put("path", "/code").put("value", code));
        constraints.createConfig(employee.scenarioId, new ConstraintConfigInput("load.max", scope,
                mapper.createObjectNode().put("maxMinutes", employee.attributes.path("quota_hebdo_minutes").asInt())
                        .put("period", "WEEK"), 1, true));
    }

    @Transactional
    public void deleteEmployeeContract(UUID scenarioId, String code) {
        List<ConstraintConfigEntity> oldRules = configs.findByScenarioIdOrderByType(scenarioId).stream()
                .filter(rule -> "load.max".equals(rule.type) && "WEEK".equals(rule.params.path("period").asText()))
                .filter(rule -> "/code".equals(rule.scope.path("eq").path("path").asText()))
                .filter(rule -> code.equals(rule.scope.path("eq").path("value").asText())).toList();
        configs.deleteAll(oldRules);
    }

    private void add(UUID scenarioId, String type, JsonNode params) {
        JsonNode scope = mapper.createObjectNode().set("eq",
                mapper.createObjectNode().put("path", "/type").put("value", "employee"));
        constraints.createConfig(scenarioId, new ConstraintConfigInput(type, scope, params, 1, true));
    }

    private boolean dailyMaximum(ConstraintConfigEntity rule) {
        return "load.max".equals(rule.type) && "DAY".equals(rule.params.path("period").asText())
                && "employee".equals(rule.scope.path("eq").path("value").asText());
    }

    private void validate(PolicyDto policy) {
        if (policy == null || policy.minRestMinutes() < 0 || policy.minRestMinutes() > 2880
                || policy.minRestMinutes() % 30 != 0 || policy.maximumDailyMinutes() < 30
                || policy.maximumDailyMinutes() > 1440 || policy.maximumDailyMinutes() % 30 != 0) {
            throw new InvalidConfigException("Les réglages doivent utiliser des multiples de 30 minutes : repos de 0 à 48 h et charge quotidienne de 30 min à 24 h.");
        }
    }
}
