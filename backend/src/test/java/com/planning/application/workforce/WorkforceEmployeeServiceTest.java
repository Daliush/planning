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
        employee.attributes = mapper.createObjectNode().put("code", "stable").put("site", "Paris")
                .put("heures_supp_hebdo_minutes", 180).put("minutes_absence_jour", 360);
        when(access.employee(context, employee.id)).thenReturn(employee);
        service.update(context.scenario().id, employee.id, new EmployeeInput("Nouveau nom",
                List.of("salle"), 2100, null));
        assertThat(employee.attributes.path("code").asText()).isEqualTo("stable");
        assertThat(employee.attributes.path("site").asText()).isEqualTo("Paris");
        assertThat(employee.attributes.path("quota_hebdo_minutes").asInt()).isEqualTo(2100);
        assertThat(employee.attributes.path("heures_supp_hebdo_minutes").asInt()).isEqualTo(180);
        assertThat(employee.attributes.path("minutes_absence_jour").asInt()).isEqualTo(360);
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

    @Test
    void unEmployeCreeSansSupplementRenvoieZero() {
        var created = service.create(context.scenario().id,
                new EmployeeInput("Alice", List.of("salle"), 2100, null));
        assertThat(created.overtimeWeeklyMinutes()).isZero();
        assertThat(created.absenceDailyMinutes()).isEqualTo(420);
        ArgumentCaptor<ResourceEntity> captor = ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resources).save(captor.capture());
        assertThat(captor.getValue().attributes.path("heures_supp_hebdo_minutes").asInt(-1)).isZero();
    }

    @Test
    void unSupplementExpliciteMetAJourLaFiche() {
        ResourceEntity employee = new ResourceEntity();
        employee.id = UUID.randomUUID();
        employee.scenarioId = context.scenario().id;
        employee.attributes = mapper.createObjectNode().put("code", "stable")
                .put("heures_supp_hebdo_minutes", 180);
        when(access.employee(context, employee.id)).thenReturn(employee);
        var updated = service.update(context.scenario().id, employee.id,
                new EmployeeInput("Alice", List.of("salle"), 2100, 60, null));
        assertThat(updated.overtimeWeeklyMinutes()).isEqualTo(60);
        assertThat(employee.attributes.path("heures_supp_hebdo_minutes").asInt()).isEqualTo(60);
    }

    @Test
    void unSupplementNegatifHorsGrilleOuAuDelaDeLaSemaineEstRefuse() {
        for (int minutes : List.of(-30, 15, 10080)) {
            assertThatThrownBy(() -> service.create(context.scenario().id,
                    new EmployeeInput("Alice", List.of("salle"), 2100, minutes, null)))
                    .isInstanceOf(InvalidConfigException.class);
        }
        verifyNoInteractions(resources, availability, policy);
    }

    @Test
    void uneIndisponibiliteDeNuitEstPersisteeCommeUnePlageUnique() {
        service.create(context.scenario().id, new EmployeeInput("Alice", List.of("salle"), 2100,
                List.of(new RecurringAvailabilityInput(Effect.UNAVAILABLE, 4,
                        LocalTime.of(22, 0), LocalTime.of(2, 0), 0))));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ResourceAvailabilityEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(availability).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        ResourceAvailabilityEntity night = captor.getValue().getFirst();
        assertThat(night.effect).isEqualTo(Effect.UNAVAILABLE);
        assertThat(night.dayInCycle).isEqualTo(4);
        assertThat(night.startTime).isEqualTo(LocalTime.of(22, 0));
        assertThat(night.endTime).isEqualTo(LocalTime.of(2, 0));
        ArgumentCaptor<ResourceEntity> employee = ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resources, times(2)).save(employee.capture());
        assertThat(employee.getValue().defaultEffect).isEqualTo(Effect.AVAILABLE);
    }

    @Test
    void lesPlagesAmbiguesOuPartiellesSontRefuseesSansEcriture() {
        List<RecurringAvailabilityInput> invalid = List.of(
                new RecurringAvailabilityInput(Effect.UNAVAILABLE, 0, LocalTime.of(9, 0), LocalTime.of(9, 0), 0),
                new RecurringAvailabilityInput(Effect.UNAVAILABLE, 0, LocalTime.of(9, 0), null, 0),
                new RecurringAvailabilityInput(Effect.UNAVAILABLE, 7, null, null, 0));
        for (RecurringAvailabilityInput input : invalid) {
            assertThatThrownBy(() -> service.create(context.scenario().id,
                    new EmployeeInput("Alice", List.of("salle"), 2100, List.of(input))))
                    .isInstanceOf(InvalidConfigException.class);
        }
        verifyNoInteractions(resources, availability, policy);
    }

    @Test
    void laDureeDeduiteParJourPeutEtrePersonnalisee() {
        var created = service.create(context.scenario().id,
                new EmployeeInput("Alice", List.of("salle"), 2100, 180, 360, null));
        assertThat(created.absenceDailyMinutes()).isEqualTo(360);
        assertThat(created.overtimeWeeklyMinutes()).isEqualTo(180);
    }

    @Test
    void laDureeParJourNegativHorsGrilleOuSuperieureAUnJourEstRefusee() {
        for (int minutes : List.of(-30, 15, 1470)) {
            assertThatThrownBy(() -> service.create(context.scenario().id,
                    new EmployeeInput("Alice", List.of("salle"), 2100, 0, minutes, null)))
                    .isInstanceOf(InvalidConfigException.class);
        }
        verifyNoInteractions(resources, availability, policy);
    }
}
