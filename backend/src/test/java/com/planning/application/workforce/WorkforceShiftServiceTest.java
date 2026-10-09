package com.planning.application.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.api.dto.WorkforceDtos.ShiftInput;
import com.planning.api.dto.WorkforceDtos.ShiftRoleInput;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.repository.entity.Entities.*;
import com.planning.repository.jpa.DemandJpaRepository;
import com.planning.repository.jpa.RequirementJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkforceShiftServiceTest {
    private final WorkforceAccess access = mock(WorkforceAccess.class);
    private final DemandJpaRepository demands = mock(DemandJpaRepository.class);
    private final RequirementJpaRepository requirements = mock(RequirementJpaRepository.class);
    private final WorkforceShiftService service = new WorkforceShiftService(access, demands, requirements, new ObjectMapper());
    private WorkforceAccess.Context context;

    @BeforeEach
    void context() {
        ScenarioEntity scenario = new ScenarioEntity();
        scenario.id = UUID.randomUUID();
        TimeModelEntity model = new TimeModelEntity();
        model.horizonFrom = LocalDate.of(2026, 10, 5);
        model.horizonTo = model.horizonFrom.plusDays(6);
        ResourceTypeEntity type = new ResourceTypeEntity();
        type.id = UUID.randomUUID();
        context = new WorkforceAccess.Context(scenario, new TenantEntity(), model, type);
        when(access.mutable(scenario.id)).thenReturn(context);
        when(requirements.findByDemandIdOrderByRole(any())).thenReturn(List.of());
    }

    @Test
    void unServiceNocturneConserveLaDateEtLaDureeReelles() {
        ShiftInput input = shift(context.model().horizonFrom, "22:00", "02:00", true);
        service.create(context.scenario().id, input);
        ArgumentCaptor<DemandEntity> captor = ArgumentCaptor.forClass(DemandEntity.class);
        verify(demands).save(captor.capture());
        assertThat(captor.getValue().durationMinutes).isEqualTo(240);
        assertThat(captor.getValue().placement.path("date").asText()).isEqualTo("2026-10-05");
        assertThat(captor.getValue().placement.path("startTime").asText()).isEqualTo("22:00");
        verify(access).changed(context);
    }

    @Test
    void unServiceQuiDepasseLaDerniereDateEstRefuseAvantEcriture() {
        ShiftInput input = shift(context.model().horizonTo, "22:00", "02:00", true);
        assertThatThrownBy(() -> service.create(context.scenario().id, input)).isInstanceOf(InvalidConfigException.class);
        verifyNoInteractions(demands, requirements);
    }

    @Test
    void unServicePeutFinirAMinuitALaBorneDeLaPeriode() {
        var created = service.create(context.scenario().id, shift(context.model().horizonTo, "20:00", "00:00", true));
        assertThat(created.durationMinutes()).isEqualTo(240);
        assertThat(created.nextDay()).isTrue();
    }

    @Test
    void lesHeuresInverseesEtHorsGrilleSontRefusees() {
        assertThatThrownBy(() -> WorkforceShiftService.duration(shift(context.model().horizonFrom, "22:00", "02:00", false)))
                .isInstanceOf(InvalidConfigException.class);
        assertThatThrownBy(() -> WorkforceShiftService.duration(shift(context.model().horizonFrom, "10:15", "12:00", false)))
                .isInstanceOf(InvalidConfigException.class);
    }

    @Test
    void laCompetenceExigeeEstCompileeDansLeCritereDePoste() {
        service.create(context.scenario().id, shift(context.model().horizonFrom, "11:00", "15:00", false));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RequirementEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(requirements).saveAll(captor.capture());
        RequirementEntity requirement = captor.getValue().getFirst();
        assertThat(requirement.typeId).isEqualTo(context.employeeType().id);
        assertThat(requirement.criteria.path("contains").path("path").asText()).isEqualTo("/competences");
        assertThat(requirement.criteria.path("contains").path("value").asText()).isEqualTo("cuisine");
        assertThat(requirement.quantity).isEqualTo(2);
    }

    private ShiftInput shift(LocalDate date, String start, String end, boolean nextDay) {
        return new ShiftInput("Service", date, LocalTime.parse(start), LocalTime.parse(end), nextDay,
                List.of(new ShiftRoleInput("Cuisine", 2, "cuisine")));
    }
}
