package com.planning.api.controller;

import com.planning.api.dto.Dtos.DemandDto;
import com.planning.api.dto.Dtos.DemandInput;
import com.planning.api.dto.Dtos.RequirementDto;
import com.planning.api.dto.Dtos.RequirementInput;
import com.planning.application.DemandService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class DemandController {

    private final DemandService demands;

    public DemandController(DemandService demands) {
        this.demands = demands;
    }

    @GetMapping("/scenarios/{scenarioId}/demands")
    public List<DemandDto> list(@PathVariable UUID scenarioId) {
        return demands.list(scenarioId);
    }

    @PostMapping("/scenarios/{scenarioId}/demands")
    @ResponseStatus(HttpStatus.CREATED)
    public DemandDto create(@PathVariable UUID scenarioId, @RequestBody DemandInput input) {
        return demands.create(scenarioId, input);
    }

    @GetMapping("/demands/{demandId}")
    public DemandDto get(@PathVariable UUID demandId) {
        return demands.get(demandId);
    }

    @PutMapping("/demands/{demandId}")
    public DemandDto update(@PathVariable UUID demandId, @RequestBody DemandInput input) {
        return demands.update(demandId, input);
    }

    @DeleteMapping("/demands/{demandId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID demandId) {
        demands.delete(demandId);
    }

    @PostMapping("/demands/{demandId}/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementDto addRequirement(@PathVariable UUID demandId,
                                         @RequestBody RequirementInput input) {
        return demands.addRequirement(demandId, input);
    }

    @DeleteMapping("/requirements/{requirementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRequirement(@PathVariable UUID requirementId) {
        demands.deleteRequirement(requirementId);
    }
}
