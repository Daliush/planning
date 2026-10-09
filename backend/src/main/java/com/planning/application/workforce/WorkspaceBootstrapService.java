package com.planning.application.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.api.dto.WorkforceDtos.AbsenceInput;
import com.planning.api.dto.WorkforceDtos.AbsenceReason;
import com.planning.api.dto.WorkforceDtos.EmployeeDto;
import com.planning.api.dto.WorkforceDtos.EmployeeInput;
import com.planning.api.dto.WorkforceDtos.ShiftInput;
import com.planning.api.dto.WorkforceDtos.ShiftRoleInput;
import com.planning.api.dto.WorkforceDtos.WorkspaceCreated;
import com.planning.api.dto.WorkforceDtos.WorkspaceInput;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.model.BusinessProfile;
import com.planning.domain.model.HorizonKind;
import com.planning.repository.entity.Entities.ResourceTypeEntity;
import com.planning.repository.entity.Entities.ScenarioEntity;
import com.planning.repository.entity.Entities.SlotTemplateEntity;
import com.planning.repository.entity.Entities.TenantEntity;
import com.planning.repository.entity.Entities.TimeModelEntity;
import com.planning.repository.jpa.ResourceTypeJpaRepository;
import com.planning.repository.jpa.ScenarioJpaRepository;
import com.planning.repository.jpa.SlotTemplateJpaRepository;
import com.planning.repository.jpa.TenantJpaRepository;
import com.planning.repository.jpa.TimeModelJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Crée un établissement indépendant et, sur demande, des données explicitement de démonstration. */
@Service
public class WorkspaceBootstrapService {
    private final TenantJpaRepository tenants;
    private final ResourceTypeJpaRepository types;
    private final TimeModelJpaRepository models;
    private final SlotTemplateJpaRepository slots;
    private final ScenarioJpaRepository scenarios;
    private final WorkforcePolicyService policy;
    private final WorkforceEmployeeService employees;
    private final WorkforceShiftService shifts;
    private final WorkforceAbsenceService absences;
    private final ObjectMapper mapper;

    public WorkspaceBootstrapService(TenantJpaRepository tenants, ResourceTypeJpaRepository types,
                                     TimeModelJpaRepository models, SlotTemplateJpaRepository slots,
                                     ScenarioJpaRepository scenarios, WorkforcePolicyService policy,
                                     WorkforceEmployeeService employees, WorkforceShiftService shifts,
                                     WorkforceAbsenceService absences, ObjectMapper mapper) {
        this.tenants = tenants;
        this.types = types;
        this.models = models;
        this.slots = slots;
        this.scenarios = scenarios;
        this.policy = policy;
        this.employees = employees;
        this.shifts = shifts;
        this.absences = absences;
        this.mapper = mapper;
    }

    @Transactional
    public WorkspaceCreated create(WorkspaceInput input) {
        if (input == null) throw new InvalidConfigException("L'établissement à créer est obligatoire.");
        WorkforceAccess.businessProfile(input.profile());
        WorkforceAccess.period(input.from(), input.to());
        String label = WorkforceAccess.text(input.label(), "Le nom de l'établissement", 120);
        boolean demo = Boolean.TRUE.equals(input.demo());
        if (demo && !label.toLowerCase(java.util.Locale.FRENCH).contains("démonstration")) label += " — Démonstration";
        TenantEntity tenant = new TenantEntity();
        tenant.id = UUID.randomUUID();
        tenant.code = "workforce-" + tenant.id;
        tenant.label = label;
        tenant.profile = input.profile().name();
        tenant.createdAt = Instant.now();
        tenants.save(tenant);
        ResourceTypeEntity type = new ResourceTypeEntity();
        type.id = UUID.randomUUID();
        type.tenantId = tenant.id;
        type.code = "employee";
        type.label = "Personnel";
        type.attrSchema = mapper.createObjectNode();
        types.save(type);
        TimeModelEntity model = new TimeModelEntity();
        model.id = UUID.randomUUID();
        model.tenantId = tenant.id;
        model.label = "Période du " + input.from() + " au " + input.to();
        model.horizonKind = HorizonKind.DATED;
        model.cycleDays = null;
        model.horizonFrom = input.from();
        model.horizonTo = input.to();
        model.maxGapMinutes = 0;
        models.save(model);
        List<SlotTemplateEntity> grid = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            for (int ordinal = 0; ordinal < 48; ordinal++) {
                SlotTemplateEntity slot = new SlotTemplateEntity();
                slot.id = UUID.randomUUID();
                slot.timeModelId = model.id;
                slot.dayInCycle = day;
                slot.ordinal = ordinal;
                slot.startTime = LocalTime.MIDNIGHT.plusMinutes(ordinal * 30L);
                slot.endTime = slot.startTime.plusMinutes(30);
                grid.add(slot);
            }
        }
        slots.saveAll(grid);
        ScenarioEntity scenario = new ScenarioEntity();
        scenario.id = UUID.randomUUID();
        scenario.tenantId = tenant.id;
        scenario.timeModelId = model.id;
        scenario.label = model.label;
        scenario.profile = input.profile();
        scenario.status = "DRAFT";
        scenario.createdAt = Instant.now();
        scenarios.save(scenario);
        policy.bootstrap(scenario.id);
        if (demo) demonstrate(scenario.id, input.profile(), input.from(), input.to());
        return new WorkspaceCreated(tenant.id, scenario.id);
    }

    private void demonstrate(UUID scenarioId, BusinessProfile profile, LocalDate from, LocalDate to) {
        List<EmployeeDto> staff = profile == BusinessProfile.RESTAURANT
                ? restaurantStaff(scenarioId) : companyStaff(scenarioId);
        int demoDays = (int) Math.min(7, java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1);
        for (int offset = 0; offset < demoDays; offset++) {
            LocalDate date = from.plusDays(offset);
            if (profile == BusinessProfile.RESTAURANT) {
                shifts.create(scenarioId, new ShiftInput("Service du midi", date, LocalTime.of(11, 30),
                        LocalTime.of(15, 0), false, restaurantRoles(date)));
                shifts.create(scenarioId, new ShiftInput("Service du soir", date, LocalTime.of(18, 0),
                        LocalTime.of(22, 30), false, restaurantRoles(date)));
            } else if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                shifts.create(scenarioId, new ShiftInput("Équipe du matin", date, LocalTime.of(8, 0),
                        LocalTime.of(12, 0), false, companyRoles()));
                shifts.create(scenarioId, new ShiftInput("Équipe de l'après-midi", date, LocalTime.of(13, 0),
                        LocalTime.of(17, 0), false, companyRoles()));
            }
        }
        LocalDate absentDate = from.plusDays(Math.min(2, demoDays - 1));
        absences.create(scenarioId, new AbsenceInput(staff.getFirst().id(), absentDate, absentDate,
                AbsenceReason.LEAVE, "Congé illustratif — données de démonstration"));
        if (demoDays > 1) {
            LocalDate sickness = from.plusDays(Math.min(4, demoDays - 1));
            absences.create(scenarioId, new AbsenceInput(staff.get(3).id(), sickness, sickness,
                    AbsenceReason.SICKNESS, "Indisponibilité illustrative — données de démonstration"));
        }
    }

    private List<EmployeeDto> restaurantStaff(UUID scenarioId) {
        return List.of(
                employee(scenarioId, "Alice Martin", List.of("responsable", "salle"), 2100),
                employee(scenarioId, "Karim Bernard", List.of("responsable", "salle"), 2100),
                employee(scenarioId, "Léa Robert", List.of("salle", "bar"), 2100),
                employee(scenarioId, "Hugo Petit", List.of("salle", "bar"), 2100),
                employee(scenarioId, "Inès Moreau", List.of("salle"), 1800),
                employee(scenarioId, "Louis Simon", List.of("salle"), 1800),
                employee(scenarioId, "Sofia Laurent", List.of("cuisine"), 2100),
                employee(scenarioId, "Gabriel Michel", List.of("cuisine"), 2100),
                employee(scenarioId, "Emma Garcia", List.of("cuisine", "salle"), 1800),
                employee(scenarioId, "Noah Roux", List.of("salle", "bar"), 1440));
    }

    private List<EmployeeDto> companyStaff(UUID scenarioId) {
        return List.of(
                employee(scenarioId, "Camille Dubois", List.of("coordination", "accueil"), 2100),
                employee(scenarioId, "Alexandre Leroy", List.of("coordination", "support"), 2100),
                employee(scenarioId, "Sarah Fontaine", List.of("accueil", "support"), 2100),
                employee(scenarioId, "Thomas Chevalier", List.of("support"), 2100),
                employee(scenarioId, "Julie François", List.of("support", "logistique"), 1800),
                employee(scenarioId, "Mehdi Legrand", List.of("logistique"), 2100),
                employee(scenarioId, "Chloé Gauthier", List.of("accueil", "logistique"), 1800),
                employee(scenarioId, "Lucas Garnier", List.of("support", "logistique"), 2100));
    }

    private EmployeeDto employee(UUID scenarioId, String name, List<String> skills, int minutes) {
        return employees.create(scenarioId, new EmployeeInput(name, skills, minutes, List.of()));
    }

    private List<ShiftRoleInput> restaurantRoles(LocalDate date) {
        boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        return List.of(new ShiftRoleInput("Responsable", 1, "responsable"),
                new ShiftRoleInput("Salle", weekend ? 3 : 2, "salle"),
                new ShiftRoleInput("Cuisine", 1, "cuisine"));
    }

    private List<ShiftRoleInput> companyRoles() {
        return List.of(new ShiftRoleInput("Coordination", 1, "coordination"),
                new ShiftRoleInput("Accueil", 1, "accueil"), new ShiftRoleInput("Support", 2, "support"),
                new ShiftRoleInput("Logistique", 1, "logistique"));
    }
}
