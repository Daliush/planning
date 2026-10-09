package com.planning.domain.constraint.plugin;

import com.planning.domain.model.Resource;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceConsistencyConstraintTest {
    private final ResourceConsistencyConstraint plugin = new ResourceConsistencyConstraint();

    @Test void uneEquipeDeDeuxPersonnesResteConformeMemeSiLesPostesPermutent() {
        PluginTestSupport fixture = new PluginTestSupport(2);
        Resource first = fixture.resource("Alex", "{}");
        Resource second = fixture.resource("Camille", "{}");
        UUID demand = UUID.randomUUID();
        var monday = fixture.activity(demand, 0, 0, 8, 60);
        var tuesday = fixture.activity(demand, 1, 1, 8, 60);
        fixture.booking(monday, first);
        fixture.booking(monday, second);
        fixture.booking(tuesday, second);
        fixture.booking(tuesday, first);
        var rule = fixture.rule(plugin, new ResourceConsistencyConstraint.Params(), 1);

        assertThat(plugin.validate(fixture.schedule(), rule)).isEmpty();
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 0);
    }

    @Test void leChangementDunePersonneProduitUneEquipeDistincte() {
        PluginTestSupport fixture = new PluginTestSupport(2);
        Resource first = fixture.resource("Alex", "{}");
        Resource second = fixture.resource("Camille", "{}");
        Resource replacement = fixture.resource("Robin", "{}");
        UUID demand = UUID.randomUUID();
        var monday = fixture.activity(demand, 0, 0, 8, 60);
        var tuesday = fixture.activity(demand, 1, 1, 8, 60);
        fixture.booking(monday, first);
        fixture.booking(monday, second);
        fixture.booking(tuesday, first);
        fixture.booking(tuesday, replacement);
        var rule = fixture.rule(plugin, new ResourceConsistencyConstraint.Params(), 2);
        assertThat(plugin.validate(fixture.schedule(), rule)).singleElement().satisfies(violation -> {
            assertThat(violation.penalty()).isEqualTo(2);
            assertThat(violation.resourceIds()).containsExactlyInAnyOrder(first.id(), second.id(), replacement.id());
            assertThat(violation.activityIds()).containsExactly(demand);
            assertThat(violation.context()).containsEntry("distinctTeams", 2);
        });
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 2);
    }

    @Test void deuxReglesConcurrentesNeMelangentPasLesEquipes() {
        PluginTestSupport fixture = new PluginTestSupport(2);
        Resource first = fixture.resource("Alex", "{}");
        Resource second = fixture.resource("Camille", "{}");
        UUID demand = UUID.randomUUID();
        fixture.booking(fixture.activity(demand, 0, 0, 8, 60), first);
        fixture.booking(fixture.activity(demand, 1, 1, 8, 60), second);
        fixture.rule(plugin, new ResourceConsistencyConstraint.Params(), 1);
        fixture.rule(plugin, new ResourceConsistencyConstraint.Params(), 3);
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 4);
    }
}
