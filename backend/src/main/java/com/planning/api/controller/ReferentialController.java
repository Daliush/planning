package com.planning.api.controller;

import com.planning.api.dto.Dtos.CalendarWeekDto;
import com.planning.api.dto.Dtos.CatalogDto;
import com.planning.api.dto.Dtos.ResourceTypeDto;
import com.planning.api.dto.Dtos.ResourceTypeInput;
import com.planning.api.dto.Dtos.ScenarioDto;
import com.planning.api.dto.Dtos.ScenarioInput;
import com.planning.api.dto.Dtos.SlotDto;
import com.planning.api.dto.Dtos.TenantDto;
import com.planning.api.dto.Dtos.TimeModelDto;
import com.planning.api.dto.Dtos.TimeModelInput;
import com.planning.application.CatalogService;
import com.planning.application.ReferentialService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ReferentialController {

    private final ReferentialService referential;
    private final CatalogService catalog;

    public ReferentialController(ReferentialService referential, CatalogService catalog) {
        this.referential = referential;
        this.catalog = catalog;
    }

    /** La grammaire du moteur : plugins, opérateurs, stratégies de placement (§2.1). */
    @GetMapping("/catalog")
    public CatalogDto catalog() {
        return catalog.catalog();
    }

    /**
     * Le catalogue restreint à ce qui est proposable sur l'horizon et dans la
     * verticale du scénario. C'est celui que l'interface de configuration doit
     * consommer : le catalogue global offrirait des réglages que le chargement
     * refusera, et le vocabulaire métier de toutes les verticales à la fois.
     */
    @GetMapping("/scenarios/{scenarioId}/catalog")
    public CatalogDto scenarioCatalog(@PathVariable UUID scenarioId) {
        return catalog.catalogFor(referential.horizonKindOf(scenarioId),
                referential.getScenario(scenarioId).profile());
    }

    @GetMapping("/tenants")
    public List<TenantDto> tenants() {
        return referential.listTenants();
    }

    @GetMapping("/tenants/{tenantId}/resource-types")
    public List<ResourceTypeDto> resourceTypes(@PathVariable UUID tenantId) {
        return referential.listResourceTypes(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/resource-types")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceTypeDto createResourceType(@PathVariable UUID tenantId,
                                              @RequestBody ResourceTypeInput input) {
        return referential.createResourceType(tenantId, input);
    }

    @GetMapping("/tenants/{tenantId}/time-models")
    public List<TimeModelDto> timeModels(@PathVariable UUID tenantId) {
        return referential.listTimeModels(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/time-models")
    @ResponseStatus(HttpStatus.CREATED)
    public TimeModelDto createTimeModel(@PathVariable UUID tenantId,
                                        @RequestBody TimeModelInput input) {
        return referential.createTimeModel(tenantId, input);
    }

    @GetMapping("/time-models/{timeModelId}/slots")
    public List<SlotDto> slots(@PathVariable UUID timeModelId) {
        return referential.listSlots(timeModelId);
    }

    @GetMapping("/tenants/{tenantId}/scenarios")
    public List<ScenarioDto> scenarios(@PathVariable UUID tenantId) {
        return referential.listScenarios(tenantId);
    }

    @GetMapping("/scenarios/{scenarioId}")
    public ScenarioDto scenario(@PathVariable UUID scenarioId) {
        return referential.getScenario(scenarioId);
    }

    @PostMapping("/scenarios")
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioDto createScenario(@RequestBody ScenarioInput input) {
        return referential.createScenario(input);
    }

    @GetMapping("/scenarios/{scenarioId}/calendar-weeks")
    public List<CalendarWeekDto> calendarWeeks(@PathVariable UUID scenarioId) {
        return referential.listCalendarWeeks(scenarioId);
    }

    @PutMapping("/scenarios/{scenarioId}/calendar-weeks")
    public List<CalendarWeekDto> replaceCalendarWeeks(@PathVariable UUID scenarioId,
                                                      @RequestBody List<CalendarWeekDto> weeks) {
        return referential.replaceCalendarWeeks(scenarioId, weeks);
    }
}
