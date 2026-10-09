package com.planning.domain.constraint.plugin;

import com.planning.domain.model.Resource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeekendFairnessConstraintTest {
    private final WeekendFairnessConstraint plugin = new WeekendFairnessConstraint();

    @Test void lesPostesDeSemaineNeContribuentPas() {
        PluginTestSupport fixture = new PluginTestSupport(7);
        Resource person = fixture.resource("Robin", "{}");
        fixture.work(person, 0, 8, 480);
        var rule = fixture.rule(plugin, new WeekendFairnessConstraint.Params(), 1);
        assertThat(plugin.validate(fixture.schedule(), rule)).isEmpty();
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 0);
    }

    @Test void samediEtDimancheComptentUnWeekendUnique() {
        PluginTestSupport fixture = new PluginTestSupport(14);
        Resource person = fixture.resource("Robin", "{}");
        fixture.work(person, 5, 8, 480);
        fixture.work(person, 6, 8, 480);
        fixture.work(person, 12, 8, 480);
        var rule = fixture.rule(plugin, new WeekendFairnessConstraint.Params(), 3);
        assertThat(plugin.validate(fixture.schedule(), rule)).singleElement().satisfies(violation -> {
            assertThat(violation.penalty()).isEqualTo(12);
            assertThat(violation.context()).containsEntry("weekends", 2);
            assertThat(violation.message()).contains("Robin");
            assertThat(violation.resourceIds()).containsExactly(person.id());
        });
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 12);
    }

    @Test void uneNuitVersSamediCompteMaisUneFinAMinuitNeComptePas() {
        PluginTestSupport fixture = new PluginTestSupport(7);
        Resource midnight = fixture.resource("Minuit", "{}");
        Resource night = fixture.resource("Nuit", "{}");
        fixture.work(midnight, 4, 22, 120);
        fixture.work(night, 4, 22, 480);
        var rule = fixture.rule(plugin, new WeekendFairnessConstraint.Params(), 1);
        assertThat(plugin.validate(fixture.schedule(), rule)).singleElement()
                .satisfies(violation -> assertThat(violation.resourceIds()).containsExactly(night.id()));
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 1);
    }

    @Test void repartirDeuxWeekendsReduitLaSommeDesCarres() {
        PluginTestSupport concentrated = new PluginTestSupport(14);
        Resource one = concentrated.resource("Robin", "{}");
        concentrated.work(one, 5, 8, 480);
        concentrated.work(one, 12, 8, 480);
        concentrated.rule(plugin, new WeekendFairnessConstraint.Params(), 1);
        PluginTestSupport.assertScore(plugin, concentrated.schedule(), 4);

        PluginTestSupport shared = new PluginTestSupport(14);
        Resource first = shared.resource("Robin", "{}");
        Resource second = shared.resource("Charlie", "{}");
        shared.work(first, 5, 8, 480);
        shared.work(second, 12, 8, 480);
        shared.rule(plugin, new WeekendFairnessConstraint.Params(), 1);
        PluginTestSupport.assertScore(plugin, shared.schedule(), 2);
    }

    @Test void deuxConfigurationsGardentLeursComptesDistincts() {
        PluginTestSupport fixture = new PluginTestSupport(14);
        Resource person = fixture.resource("Robin", "{}");
        fixture.work(person, 5, 8, 480);
        fixture.work(person, 12, 8, 480);
        fixture.rule(plugin, new WeekendFairnessConstraint.Params(), 1);
        fixture.rule(plugin, new WeekendFairnessConstraint.Params(), 3);
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 16);
    }
}
