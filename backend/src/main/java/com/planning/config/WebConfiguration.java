package com.planning.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Aucune authentification en V1 — l'API est ouverte, comme convenu. Le SDD pose le
 * multi-tenant mais laisse la sécurité à spécifier (§25) : quand elle arrivera,
 * elle se branchera ici sans toucher au domaine.
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private final PlanningProperties properties;

    public WebConfiguration(PlanningProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
