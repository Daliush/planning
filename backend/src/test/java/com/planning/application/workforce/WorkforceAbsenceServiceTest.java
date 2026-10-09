package com.planning.application.workforce;

import com.planning.api.dto.WorkforceDtos.AbsenceInput;
import com.planning.api.dto.WorkforceDtos.AbsenceReason;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.repository.entity.Entities.*;
import com.planning.repository.jpa.AbsenceJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkforceAbsenceServiceTest {
    private final WorkforceAccess access = mock(WorkforceAccess.class);
    private final AbsenceJpaRepository absences = mock(AbsenceJpaRepository.class);
    private final WorkforceAbsenceService service = new WorkforceAbsenceService(access, absences);
    private WorkforceAccess.Context context;
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void context() {
        ScenarioEntity scenario = new ScenarioEntity();
        scenario.id = UUID.randomUUID();
        TimeModelEntity model = new TimeModelEntity();
        model.horizonFrom = LocalDate.of(2026, 10, 5);
        model.horizonTo = model.horizonFrom.plusDays(6);
        context = new WorkforceAccess.Context(scenario, new TenantEntity(), model, new ResourceTypeEntity());
        when(access.mutable(scenario.id)).thenReturn(context);
        when(absences.findByResourceId(employeeId)).thenReturn(List.of());
    }

    @Test
    void uneMaladieDunJourEstPersistanteEtInvalideLePlanningCalcule() {
        service.create(context.scenario().id, new AbsenceInput(employeeId, context.model().horizonFrom,
                context.model().horizonFrom, AbsenceReason.SICKNESS, null));
        ArgumentCaptor<AbsenceEntity> captor = ArgumentCaptor.forClass(AbsenceEntity.class);
        verify(absences).save(captor.capture());
        assertThat(captor.getValue().reason).isEqualTo("SICKNESS");
        assertThat(captor.getValue().fromDate).isEqualTo(captor.getValue().toDate);
        assertThat(captor.getValue().scenarioId).isEqualTo(context.scenario().id);
        verify(access).employee(context, employeeId);
        verify(access).changed(context);
    }

    @Test
    void lesAbsencesQuiSeChevauchentSontRefuseesBornesIncluses() {
        AbsenceEntity existing = new AbsenceEntity();
        existing.fromDate = context.model().horizonFrom;
        existing.toDate = existing.fromDate.plusDays(2);
        when(absences.findByResourceId(employeeId)).thenReturn(List.of(existing));
        assertThatThrownBy(() -> service.create(context.scenario().id, new AbsenceInput(employeeId,
                existing.toDate, existing.toDate.plusDays(1), AbsenceReason.LEAVE, "Congé")))
                .isInstanceOf(InvalidConfigException.class).hasMessageContaining("déjà une absence");
        verify(absences, never()).save(any());
    }

    @Test
    void uneAbsenceHorsHorizonEstRefusee() {
        assertThatThrownBy(() -> service.create(context.scenario().id, new AbsenceInput(employeeId,
                context.model().horizonFrom.minusDays(1), context.model().horizonFrom, AbsenceReason.LEAVE, null)))
                .isInstanceOf(InvalidConfigException.class);
        verifyNoInteractions(absences);
    }
}
