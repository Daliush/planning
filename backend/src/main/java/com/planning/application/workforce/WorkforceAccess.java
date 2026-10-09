package com.planning.application.workforce;

import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.exception.NotFoundException;
import com.planning.domain.model.BusinessProfile;
import com.planning.domain.model.HorizonKind;
import com.planning.repository.entity.Entities.ResourceEntity;
import com.planning.repository.entity.Entities.ResourceTypeEntity;
import com.planning.repository.entity.Entities.ScenarioEntity;
import com.planning.repository.entity.Entities.TenantEntity;
import com.planning.repository.entity.Entities.TimeModelEntity;
import com.planning.repository.jpa.ResourceJpaRepository;
import com.planning.repository.jpa.ResourceTypeJpaRepository;
import com.planning.repository.jpa.ScenarioJpaRepository;
import com.planning.repository.jpa.SolveJobJpaRepository;
import com.planning.repository.jpa.TenantJpaRepository;
import com.planning.repository.jpa.TimeModelJpaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Vérifie le contexte métier avant chaque lecture ou mutation de la façade. */
@Component
public class WorkforceAccess {
    private final ScenarioJpaRepository scenarios;
    private final TenantJpaRepository tenants;
    private final TimeModelJpaRepository models;
    private final ResourceTypeJpaRepository types;
    private final ResourceJpaRepository resources;
    private final SolveJobJpaRepository jobs;

    public WorkforceAccess(ScenarioJpaRepository scenarios, TenantJpaRepository tenants,
                           TimeModelJpaRepository models, ResourceTypeJpaRepository types,
                           ResourceJpaRepository resources, SolveJobJpaRepository jobs) {
        this.scenarios = scenarios;
        this.tenants = tenants;
        this.models = models;
        this.types = types;
        this.resources = resources;
        this.jobs = jobs;
    }

    public record Context(ScenarioEntity scenario, TenantEntity tenant,
                          TimeModelEntity model, ResourceTypeEntity employeeType) {}

    public Context context(UUID scenarioId) {
        ScenarioEntity scenario = scenarios.findById(scenarioId)
                .orElseThrow(() -> NotFoundException.of("Scénario", scenarioId));
        return context(scenario);
    }

    private Context context(ScenarioEntity scenario) {
        businessProfile(scenario.profile);
        TenantEntity tenant = tenants.findById(scenario.tenantId)
                .orElseThrow(() -> NotFoundException.of("Établissement", scenario.tenantId));
        if (!scenario.profile.name().equals(tenant.profile)) {
            throw new InvalidConfigException("Le profil du planning doit correspondre à celui de l'établissement.");
        }
        TimeModelEntity model = models.findById(scenario.timeModelId)
                .orElseThrow(() -> NotFoundException.of("Modèle temporel", scenario.timeModelId));
        if (!scenario.tenantId.equals(model.tenantId) || model.horizonKind != HorizonKind.DATED) {
            throw new InvalidConfigException("Ce planning métier exige une période datée de son établissement.");
        }
        period(model.horizonFrom, model.horizonTo);
        ResourceTypeEntity type = types.findByTenantIdOrderByLabel(tenant.id).stream()
                .filter(candidate -> "employee".equals(candidate.code)).findFirst()
                .orElseThrow(() -> new InvalidConfigException("Le type de personnel de cet établissement est absent."));
        return new Context(scenario, tenant, model, type);
    }

    public Context mutable(UUID scenarioId) {
        ScenarioEntity scenario = scenarios.findByIdForUpdate(scenarioId)
                .orElseThrow(() -> NotFoundException.of("Scénario", scenarioId));
        Context context = context(scenario);
        if ("PUBLISHED".equals(context.scenario().status) || "ARCHIVED".equals(context.scenario().status)) {
            throw new InvalidConfigException("Ce planning est figé : créez une nouvelle période pour le modifier.");
        }
        boolean active = jobs.findByScenarioIdOrderByCreatedAtDesc(scenarioId).stream()
                .anyMatch(job -> "QUEUED".equals(job.status) || "RUNNING".equals(job.status));
        if (active || "SOLVING".equals(context.scenario().status)) {
            throw new InvalidConfigException("Attendez la fin du calcul avant de modifier ce planning.");
        }
        return context;
    }

    public ResourceEntity employee(Context context, UUID resourceId) {
        ResourceEntity resource = resources.findById(resourceId)
                .orElseThrow(() -> NotFoundException.of("Employé", resourceId));
        if (!context.scenario().id.equals(resource.scenarioId)
                || !context.employeeType().id.equals(resource.typeId)) {
            throw new InvalidConfigException("Cet employé n'appartient pas à ce planning.");
        }
        return resource;
    }

    public void changed(Context context) {
        context.scenario().status = "DRAFT";
        scenarios.save(context.scenario());
    }

    public static void businessProfile(BusinessProfile profile) {
        if (profile != BusinessProfile.RESTAURANT && profile != BusinessProfile.COMPANY) {
            throw new InvalidConfigException("Choisissez un établissement de restauration ou d'entreprise.");
        }
    }

    public static void period(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= 28) {
            throw new InvalidConfigException("La période doit contenir entre 1 et 28 jours, avec deux dates valides.");
        }
    }

    public static void date(Context context, LocalDate date) {
        if (date == null || date.isBefore(context.model().horizonFrom) || date.isAfter(context.model().horizonTo)) {
            throw new InvalidConfigException("La date doit appartenir à la période du planning.");
        }
    }

    public static String text(String value, String label, int maximum) {
        if (value == null || value.isBlank() || value.trim().length() > maximum) {
            throw new InvalidConfigException(label + " doit contenir entre 1 et " + maximum + " caractères.");
        }
        return value.trim();
    }
}
