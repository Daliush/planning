package com.planning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("planning")
public record PlanningProperties(Solver solver, Cors cors) {

    /**
     * @param environmentMode {@code FULL_ASSERT} recalcule le score depuis zéro à
     *                        chaque mouvement et le compare à l'incrémental : il
     *                        fait tomber toute impureté de lambda (§11.8). Bien
     *                        plus lent — à réserver au développement.
     */
    public record Solver(int defaultTimeLimitSeconds, int maxTimeLimitSeconds,
                         int workerThreads, String environmentMode) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
