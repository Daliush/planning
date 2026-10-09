package com.planning.domain.constraint.plugin;

import com.planning.domain.model.Resource;
import com.planning.domain.model.Level;
import com.planning.support.ScheduleBuilder;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PreferredWindowConstraintTest {
    private final PreferredWindowConstraint plugin = new PreferredWindowConstraint();

    @Test void unPosteDeNuitNePerdPasSaPenaliteAuPassageDeMinuit() {
        PluginTestSupport fixture = new PluginTestSupport(2);
        Resource person = fixture.resource("Nuit", "{}");
        fixture.work(person, 0, 22, 480);
        var rule = fixture.rule(plugin, new PreferredWindowConstraint.Params("08:00", "18:00"), 1);
        assertThat(plugin.validate(fixture.schedule(), rule)).singleElement().satisfies(violation -> {
            assertThat(violation.penalty()).isEqualTo(480);
            assertThat(violation.message()).contains("lundi 5 octobre");
        });
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 480);
    }

    @Test void laPartieDansLaFenetreEstDeduiteDeLaDureeReelle() {
        PluginTestSupport fixture = new PluginTestSupport(2);
        Resource person = fixture.resource("Poste", "{}");
        fixture.work(person, 0, 17, 720);
        fixture.rule(plugin, new PreferredWindowConstraint.Params("08:00", "18:00"), 2);
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 1_320);
    }

    @Test void uneRecreationHorsFenetreNestPasPenaliseeCommeDuTravail() {
        ScheduleBuilder builder = new ScheduleBuilder();
        Resource person = builder.resource("Alex", "{}");
        var activity = builder.activity("Cours", UUID.randomUUID(), 0, 120,
                builder.slot(0, LocalTime.of(9, 0)), "{}");
        builder.booking(activity, "participant", person);
        var rule = builder.config(plugin.type(), "{}", new PreferredWindowConstraint.Params("10:15", "11:15"), 1, Level.SOFT);

        assertThat(plugin.validate(builder.build(), rule)).singleElement()
                .satisfies(violation -> assertThat(violation.penalty()).isEqualTo(60));
        PluginTestSupport.assertScore(plugin, builder.build(), 60);
    }
}
