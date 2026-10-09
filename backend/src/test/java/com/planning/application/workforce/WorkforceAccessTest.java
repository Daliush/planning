package com.planning.application.workforce;

import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.model.BusinessProfile;
import com.planning.domain.model.HorizonKind;
import com.planning.repository.entity.Entities.*;
import com.planning.repository.jpa.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class WorkforceAccessTest {
    private final ScenarioJpaRepository scenarios = mock(ScenarioJpaRepository.class);
    private final TenantJpaRepository tenants = mock(TenantJpaRepository.class);
    private final TimeModelJpaRepository models = mock(TimeModelJpaRepository.class);
    private final ResourceTypeJpaRepository types = mock(ResourceTypeJpaRepository.class);
    private final ResourceJpaRepository resources = mock(ResourceJpaRepository.class);
    private final SolveJobJpaRepository jobs = mock(SolveJobJpaRepository.class);
    private final WorkforceAccess access = new WorkforceAccess(scenarios, tenants, models, types, resources, jobs);
    private ScenarioEntity scenario;
    private TimeModelEntity model;

    @BeforeEach
    void businessWorkspace() {
        scenario = new ScenarioEntity();
        scenario.id = UUID.randomUUID();
        scenario.tenantId = UUID.randomUUID();
        scenario.timeModelId = UUID.randomUUID();
        scenario.profile = BusinessProfile.RESTAURANT;
        scenario.status = "DRAFT";
        TenantEntity tenant = new TenantEntity();
        tenant.id = scenario.tenantId;
        tenant.profile = "RESTAURANT";
        model = new TimeModelEntity();
        model.id = scenario.timeModelId;
        model.tenantId = tenant.id;
        model.horizonKind = HorizonKind.DATED;
        model.horizonFrom = LocalDate.of(2026, 10, 5);
        model.horizonTo = model.horizonFrom.plusDays(6);
        ResourceTypeEntity type = new ResourceTypeEntity();
        type.id = UUID.randomUUID();
        type.tenantId = tenant.id;
        type.code = "employee";
        when(scenarios.findById(scenario.id)).thenReturn(Optional.of(scenario));
        when(scenarios.findByIdForUpdate(scenario.id)).thenReturn(Optional.of(scenario));
        when(tenants.findById(tenant.id)).thenReturn(Optional.of(tenant));
        when(models.findById(model.id)).thenReturn(Optional.of(model));
        when(types.findByTenantIdOrderByLabel(tenant.id)).thenReturn(List.of(type));
        when(jobs.findByScenarioIdOrderByCreatedAtDesc(scenario.id)).thenReturn(List.of());
    }

    @Test
    void leProfilScolaireNePeutPasEtreModifieParLaFacadeMetier() {
        scenario.profile = BusinessProfile.SCHOOL;
        assertThatThrownBy(() -> access.mutable(scenario.id)).isInstanceOf(InvalidConfigException.class);
        verifyNoInteractions(tenants, models, resources, jobs);
    }

    @Test
    void unModeleDunAutreEtablissementEstRefuse() {
        model.tenantId = UUID.randomUUID();
        assertThatThrownBy(() -> access.context(scenario.id)).isInstanceOf(InvalidConfigException.class);
    }

    @Test
    void unEmployeDunAutrePlanningEstRefuse() {
        ResourceEntity employee = new ResourceEntity();
        employee.id = UUID.randomUUID();
        employee.scenarioId = UUID.randomUUID();
        employee.typeId = access.context(scenario.id).employeeType().id;
        when(resources.findById(employee.id)).thenReturn(Optional.of(employee));
        assertThatThrownBy(() -> access.employee(access.context(scenario.id), employee.id))
                .isInstanceOf(InvalidConfigException.class).hasMessageContaining("n'appartient pas");
    }

    @Test
    void uneResolutionEnFileInterditLesMutations() {
        SolveJobEntity job = new SolveJobEntity();
        job.status = "QUEUED";
        when(jobs.findByScenarioIdOrderByCreatedAtDesc(scenario.id)).thenReturn(List.of(job));
        assertThatThrownBy(() -> access.mutable(scenario.id)).isInstanceOf(InvalidConfigException.class)
                .hasMessageContaining("fin du calcul");
        verify(scenarios).findByIdForUpdate(scenario.id);
    }

    @Test
    void lesBornesDePeriodeSontInclusivesEtLimiteesA28Jours() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        assertDoesNotThrow(() -> WorkforceAccess.period(from, from.plusDays(27)));
        assertThatThrownBy(() -> WorkforceAccess.period(from, from.plusDays(28)))
                .isInstanceOf(InvalidConfigException.class);
        assertThatThrownBy(() -> WorkforceAccess.period(from, from.minusDays(1)))
                .isInstanceOf(InvalidConfigException.class);
    }
}
