package com.planning.application.workforce;

import com.planning.api.dto.WorkforceDtos.AbsenceDto;
import com.planning.api.dto.WorkforceDtos.AbsenceInput;
import com.planning.api.dto.WorkforceDtos.AbsenceReason;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.exception.NotFoundException;
import com.planning.repository.entity.Entities.AbsenceEntity;
import com.planning.repository.jpa.AbsenceJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WorkforceAbsenceService {
    private final WorkforceAccess access;
    private final AbsenceJpaRepository absences;

    public WorkforceAbsenceService(WorkforceAccess access, AbsenceJpaRepository absences) {
        this.access = access;
        this.absences = absences;
    }

    public List<AbsenceDto> list(UUID scenarioId) {
        access.context(scenarioId);
        return absences.findByScenarioIdOrderByFromDateAsc(scenarioId).stream().map(this::toDto).toList();
    }

    @Transactional
    public AbsenceDto create(UUID scenarioId, AbsenceInput input) {
        WorkforceAccess.Context context = access.mutable(scenarioId);
        if (input == null || input.resourceId() == null || input.reason() == null) {
            throw new InvalidConfigException("L'employé, les dates et le motif de l'absence sont obligatoires.");
        }
        access.employee(context, input.resourceId());
        WorkforceAccess.date(context, input.from());
        WorkforceAccess.date(context, input.to());
        if (input.to().isBefore(input.from())) throw new InvalidConfigException("La fin de l'absence doit suivre son début.");
        if (input.note() != null && input.note().length() > 500) throw new InvalidConfigException("La note d'absence est limitée à 500 caractères.");
        boolean overlaps = absences.findByResourceId(input.resourceId()).stream()
                .anyMatch(existing -> !input.from().isAfter(existing.toDate) && !input.to().isBefore(existing.fromDate));
        if (overlaps) throw new InvalidConfigException("Cet employé a déjà une absence sur une partie de cette période.");
        AbsenceEntity absence = new AbsenceEntity();
        absence.id = UUID.randomUUID();
        absence.scenarioId = scenarioId;
        absence.resourceId = input.resourceId();
        absence.fromDate = input.from();
        absence.toDate = input.to();
        absence.reason = input.reason().name();
        absence.note = input.note() == null || input.note().isBlank() ? null : input.note().trim();
        absences.save(absence);
        access.changed(context);
        return toDto(absence);
    }

    @Transactional
    public void delete(UUID scenarioId, UUID absenceId) {
        WorkforceAccess.Context context = access.mutable(scenarioId);
        AbsenceEntity absence = absences.findById(absenceId)
                .orElseThrow(() -> NotFoundException.of("Absence", absenceId));
        if (!scenarioId.equals(absence.scenarioId)) throw new InvalidConfigException("Cette absence n'appartient pas à ce planning.");
        absences.delete(absence);
        access.changed(context);
    }

    private AbsenceDto toDto(AbsenceEntity absence) {
        return new AbsenceDto(absence.id, absence.resourceId, absence.fromDate, absence.toDate,
                AbsenceReason.valueOf(absence.reason), absence.note);
    }
}
