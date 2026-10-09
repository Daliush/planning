package com.planning.application.workforce;

import com.planning.api.dto.Dtos.AssignmentDto;
import com.planning.api.dto.WorkforceDtos.AbsenceDto;
import com.planning.api.dto.WorkforceDtos.ContractWeekDto;
import com.planning.api.dto.WorkforceDtos.EmployeeDto;
import com.planning.domain.constraint.AbsenceLoadAdjustment;
import com.planning.domain.time.TemporalBuckets;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Lecture métier des mêmes cibles hebdomadaires que celles du solveur. */
final class WorkforceContractProjection {
    private WorkforceContractProjection() {}

    static List<ContractWeekDto> weeks(LocalDate from, LocalDate to, List<EmployeeDto> employees,
                                       List<AbsenceDto> absences, List<AssignmentDto> assignments) {
        List<ContractWeekDto> weeks = new ArrayList<>();
        LocalDate firstMonday = LocalDate.ofEpochDay(TemporalBuckets.weekIndex(from));
        for (EmployeeDto employee : employees) {
            for (LocalDate monday = firstMonday; !monday.isAfter(to); monday = monday.plusWeeks(1)) {
                LocalDate weekFrom = monday.isBefore(from) ? from : monday;
                LocalDate sunday = monday.plusDays(6);
                LocalDate weekTo = sunday.isAfter(to) ? to : sunday;
                int absenceDays = (int) weekFrom.datesUntil(weekTo.plusDays(1))
                        .filter(date -> absences.stream().anyMatch(absence ->
                                employee.id().equals(absence.resourceId())
                                        && !date.isBefore(absence.from()) && !date.isAfter(absence.to())))
                        .count();
                int deduction = AbsenceLoadAdjustment.deductionMinutes(employee.contractWeeklyMinutes(),
                        employee.absenceDailyMinutes(), absenceDays);
                int due = employee.contractWeeklyMinutes() - deduction;
                int assigned = assignments.stream().filter(assignment -> employee.id().equals(assignment.resourceId()))
                        .mapToInt(assignment -> minutesIn(assignment, weekFrom, weekTo)).sum();
                weeks.add(new ContractWeekDto(employee.id(), weekFrom, weekTo, employee.contractWeeklyMinutes(),
                        deduction, due, due + employee.overtimeWeeklyMinutes(), assigned,
                        Math.max(0, due - assigned), Math.max(0, assigned - due)));
            }
        }
        return List.copyOf(weeks);
    }

    private static int minutesIn(AssignmentDto assignment, LocalDate from, LocalDate to) {
        if (assignment.date() == null || assignment.startTime() == null || assignment.endTime() == null) return 0;
        LocalDateTime start = assignment.date().atTime(assignment.startTime());
        LocalDate endDate = assignment.endDate() == null ? assignment.date() : assignment.endDate();
        LocalDateTime end = endDate.atTime(assignment.endTime());
        LocalDateTime overlapFrom = start.isAfter(from.atStartOfDay()) ? start : from.atStartOfDay();
        LocalDateTime overlapTo = end.isBefore(to.plusDays(1).atStartOfDay()) ? end : to.plusDays(1).atStartOfDay();
        return overlapTo.isAfter(overlapFrom) ? Math.toIntExact(Duration.between(overlapFrom, overlapTo).toMinutes()) : 0;
    }
}
