package com.planning.application.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.api.dto.WorkforceDtos.EmployeeInput;
import com.planning.api.dto.WorkforceDtos.RecurringAvailabilityInput;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.model.Effect;
import com.planning.repository.entity.Entities.*;
import com.planning.repository.jpa.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkforceEmployeeServiceTest {
    private final WorkforceAccess access = mock(WorkforceAccess.class);
    private final ResourceJpaRepository resources = mock(ResourceJpaRepository.class);
    private final ResourceAvailabilityJpaRepository availability = mock(ResourceAvailabilityJpaRepository.class);
    private final WorkforcePolicyService policy = mock(WorkforcePolicyService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final WorkforceEmployeeService service = new WorkforceEmployeeService(access, resources, availability,
            mock(AbsenceJpaRepository.class), policy, mapper);
    private WorkforceAccess.Context context;

    @BeforeEach
    void context() {
        ScenarioEntity scenario = new ScenarioEntity();
        scenario.id = UUID.randomUUID();
        ResourceTypeEntity type = new ResourceTypeEntity();
        type.id = UUID.randomUUID();
        context = new WorkforceAccess.Context(scenario, new TenantEntity(), new TimeModelEntity(), type);
        when(access.mutable(scenario.id)).thenReturn(context);
        when(availability.findByResourceIdOrderByPriority(any())).thenReturn(List.of());
    }

    @Test
    void renommerUnEmployePreserveLeCodeStableDesReglesEtLesAttributsDuTenant() {
        ResourceEntity employee = new ResourceEntity();
        employee.id = UUID.randomUUID();
        employee.scenarioId = context.scenario().id;
        employee.attributes = mapper.createObjectNode().put("code", "stable").put("site", "Paris");
        when(access.employee(context, employee.id)).thenReturn(employee);
        service.update(context.scenario().id, employee.id, new EmployeeInput("Nouveau nom",
                List.of("salle"), 2100, null));
        assertThat(employee.attributes.path("code").asText()).isEqualTo("stable");
        assertThat(employee.attributes.path("site").asText()).isEqualTo("Paris");
        assertThat(employee.attributes.path("quota_hebdo_minutes").asInt()).isEqualTo(2100);
        verify(policy).employeeContract(employee);
        verify(availability, never()).deleteAll(any());
    }

    @Test
    void uneOuvertureRecurrenteRendLEmployeIndisponibleHorsDeLaPlage() {
        service.create(context.scenario().id, new EmployeeInput("Extra", List.of("salle"), 600,
                List.of(new RecurringAvailabilityInput(Effect.AVAILABLE, 4,
                        LocalTime.of(18, 0), LocalTime.of(22, 0), 0))));
        ArgumentCaptor<ResourceEntity> captor = ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resources, times(2)).save(captor.capture());
        assertThat(captor.getValue().defaultEffect).isEqualTo(Effect.UNAVAILABLE);
        assertThat(captor.getValue().typeId).isEqualTo(context.employeeType().id);
    }

    @Test
    void uneDisponibiliteHorsGrilleEstRefuseeAvantLaCreationDeLEmploye() {
        assertThatThrownBy(() -> service.create(context.scenario().id, new EmployeeInput("Extra", List.of("salle"), 600,
                List.of(new RecurringAvailabilityInput(Effect.AVAILABLE, 4,
                        LocalTime.of(18, 15), LocalTime.of(22, 0), 0)))))
                .isInstanceOf(InvalidConfigException.class);
        verifyNoInteractions(resources, availability, policy);
    }
}
