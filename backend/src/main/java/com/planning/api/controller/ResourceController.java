package com.planning.api.controller;

import com.planning.api.dto.Dtos.AvailabilityDto;
import com.planning.api.dto.Dtos.AvailabilityInput;
import com.planning.api.dto.Dtos.ResourceDto;
import com.planning.api.dto.Dtos.ResourceInput;
import com.planning.application.ResourceCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ResourceController {

    private final ResourceCatalogService resources;

    public ResourceController(ResourceCatalogService resources) {
        this.resources = resources;
    }

    @GetMapping("/scenarios/{scenarioId}/resources")
    public List<ResourceDto> list(@PathVariable UUID scenarioId,
                                  @RequestParam(required = false) UUID typeId) {
        return resources.list(scenarioId, typeId);
    }

    @PostMapping("/scenarios/{scenarioId}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceDto create(@PathVariable UUID scenarioId, @RequestBody ResourceInput input) {
        return resources.create(scenarioId, input);
    }

    @GetMapping("/resources/{resourceId}")
    public ResourceDto get(@PathVariable UUID resourceId) {
        return resources.get(resourceId);
    }

    @PutMapping("/resources/{resourceId}")
    public ResourceDto update(@PathVariable UUID resourceId, @RequestBody ResourceInput input) {
        return resources.update(resourceId, input);
    }

    @DeleteMapping("/resources/{resourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID resourceId) {
        resources.delete(resourceId);
    }

    /**
     * Les disponibilités se remplacent en bloc : elles forment un jeu de règles
     * dont la résolution dépend des priorités relatives (§6.5).
     */
    @PutMapping("/resources/{resourceId}/availability")
    public List<AvailabilityDto> replaceAvailability(@PathVariable UUID resourceId,
                                                     @RequestBody List<AvailabilityInput> inputs) {
        return resources.replaceAvailability(resourceId, inputs);
    }
}
