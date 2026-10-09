package com.planning.domain.constraint.plugin;

import com.planning.domain.model.Resource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkDaysMaxConstraintTest {
    private final WorkDaysMaxConstraint plugin = new WorkDaysMaxConstraint();

    @Test void uneDateLibreRomptLaSerie() {
        PluginTestSupport fixture = new PluginTestSupport(7);
        Resource person = fixture.resource("Alex", "{}");
        for (int day : new int[]{0, 1, 3, 4}) {
            fixture.work(person, day, 8, 480);
        }
        var rule = fixture.rule(plugin, new WorkDaysMaxConstraint.Params(2), 1);
        assertThat(plugin.validate(fixture.schedule(), rule)).isEmpty();
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 0);
    }

    @Test void lesDatesEtNonLesPostesSontComptees() {
        PluginTestSupport fixture = new PluginTestSupport(4);
        Resource person = fixture.resource("Alex", "{}");
        for (int day = 0; day < 4; day++) {
            fixture.work(person, day, 8, 240);
            fixture.work(person, day, 14, 240);
        }
        var rule = fixture.rule(plugin, new WorkDaysMaxConstraint.Params(3), 2);
        assertThat(plugin.validate(fixture.schedule(), rule)).singleElement().satisfies(violation -> {
            assertThat(violation.penalty()).isEqualTo(2);
            assertThat(violation.context()).containsEntry("days", 4);
            assertThat(violation.message()).contains("Alex", "2026-10-05", "2026-10-08");
        });
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 2);
    }

    @Test void uneNuitCompteSesDeuxDatesMaisUneFinAMinuitEstExclusive() {
        PluginTestSupport fixture = new PluginTestSupport(3);
        Resource midnight = fixture.resource("Minuit", "{}");
        Resource night = fixture.resource("Nuit", "{}");
        fixture.work(midnight, 0, 22, 120);
        fixture.work(night, 0, 22, 480);
        var rule = fixture.rule(plugin, new WorkDaysMaxConstraint.Params(1), 1);
        assertThat(plugin.validate(fixture.schedule(), rule)).singleElement().satisfies(violation -> {
            assertThat(violation.resourceIds()).containsExactly(night.id());
            assertThat(violation.penalty()).isEqualTo(1);
        });
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 1);
    }

    @Test void deuxReglesConcurrentesNeFusionnentPasLesDates() {
        PluginTestSupport fixture = new PluginTestSupport(4);
        Resource person = fixture.resource("Alex", "{}");
        for (int day = 0; day < 4; day++) {
            fixture.work(person, day, 8, 480);
        }
        fixture.rule(plugin, new WorkDaysMaxConstraint.Params(2), 1);
        fixture.rule(plugin, new WorkDaysMaxConstraint.Params(3), 3);
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 5);
    }
}
