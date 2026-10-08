package com.planning.api.controller;

import com.planning.api.dto.Dtos.ConstraintConfigDto;
import com.planning.api.dto.Dtos.ConstraintConfigInput;
import com.planning.api.dto.Dtos.ConstraintWeightDto;
import com.planning.api.dto.Dtos.ConstraintWeightInput;
import com.planning.api.dto.Dtos.ScopePreviewDto;
import com.planning.api.dto.Dtos.ScopePreviewRequest;
import com.planning.application.ConstraintService;
import com.planning.application.ScopePreviewService;
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
public class ConstraintController {

    private final ConstraintService constraints;
    private final ScopePreviewService scopePreview;

    public ConstraintController(ConstraintService constraints, ScopePreviewService scopePreview) {
        this.constraints = constraints;
        this.scopePreview = scopePreview;
    }

    /**
     * Qui une règle vise réellement, évalué à la saisie contre les données du
     * scénario. Un scope qui ne matche personne ne lève aucune erreur : c'est le
     * mode de défaillance le plus silencieux du moteur de critères.
     */
    @PostMapping("/scenarios/{scenarioId}/scope-preview")
    public ScopePreviewDto scopePreview(@PathVariable UUID scenarioId,
                                        @RequestBody ScopePreviewRequest request) {
        return scopePreview.preview(scenarioId, request);
    }

    @GetMapping("/scenarios/{scenarioId}/constraint-configs")
    public List<ConstraintConfigDto> listConfigs(@PathVariable UUID scenarioId) {
        return constraints.listConfigs(scenarioId);
    }

    @PostMapping("/scenarios/{scenarioId}/constraint-configs")
    @ResponseStatus(HttpStatus.CREATED)
    public ConstraintConfigDto createConfig(@PathVariable UUID scenarioId,
                                            @RequestBody ConstraintConfigInput input) {
        return constraints.createConfig(scenarioId, input);
    }

    @PutMapping("/constraint-configs/{configId}")
    public ConstraintConfigDto updateConfig(@PathVariable UUID configId,
                                            @RequestBody ConstraintConfigInput input) {
        return constraints.updateConfig(configId, input);
    }

    @DeleteMapping("/constraint-configs/{configId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConfig(@PathVariable UUID configId) {
        constraints.deleteConfig(configId);
    }

    /** Poids ① : l'arbitrage de politique entre types de contraintes (§13.2). */
    @GetMapping("/scenarios/{scenarioId}/constraint-weights")
    public List<ConstraintWeightDto> listWeights(@PathVariable UUID scenarioId) {
        return constraints.listWeights(scenarioId);
    }

    @PutMapping("/scenarios/{scenarioId}/constraint-weights")
    public ConstraintWeightDto upsertWeight(@PathVariable UUID scenarioId,
                                            @RequestBody ConstraintWeightInput input) {
        return constraints.upsertWeight(scenarioId, input);
    }
}
