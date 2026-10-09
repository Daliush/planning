package com.planning.domain.constraint.plugin;

import com.planning.domain.constraint.NoParams;
import com.planning.domain.model.TimeProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;

class DatedStructuralMessagesTest {
    @Test void leConflitNommeLaDateReelleQuandLhorizonCommenceMercredi() {
        PluginTestSupport fixture = new PluginTestSupport(LocalDate.of(2026, 10, 7), 1);
        var person = fixture.resource("Alex", "{}");
        fixture.work(person, 0, 8, 60);
        fixture.work(person, 0, 8, 60);
        var plugin = new ResourceConflictConstraint();
        var rule = fixture.rule(plugin, new NoParams(), 1);
        assertThat(plugin.validate(fixture.schedule(), rule)).singleElement()
                .satisfies(violation -> assertThat(violation.message()).contains("mercredi 7 octobre"));
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 1);
    }

    @Test void lindisponibiliteNommeLaDateReelleQuandLhorizonCommenceMercredi() {
        PluginTestSupport fixture = new PluginTestSupport(LocalDate.of(2026, 10, 7), 1);
        var person = fixture.resource("Alex", "{}", fixture.unavailableDays(0, 1));
        fixture.work(person, 0, 8, 60);
        var plugin = new ResourceAvailabilityConstraint();
        var rule = fixture.rule(plugin, new NoParams(), 1);
        assertThat(plugin.validate(fixture.schedule(), rule)).singleElement()
                .satisfies(violation -> assertThat(violation.message()).contains("mercredi 7 octobre"));
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 1);
    }

    @Test void leCreneauDeconseilleNommeLaDateReelleQuandLhorizonCommenceMercredi() {
        PluginTestSupport fixture = new PluginTestSupport(LocalDate.of(2026, 10, 7), 1);
        BitSet allowed = new BitSet();
        allowed.set(0, fixture.horizon().size());
        BitSet discouraged = new BitSet();
        discouraged.set(8);
        var person = fixture.resource("Alex", "{}", new TimeProfile(allowed, discouraged));
        fixture.work(person, 0, 8, 60);
        var plugin = new ResourceDiscouragedSlotConstraint();
        var rule = fixture.rule(plugin, new NoParams(), 1);
        assertThat(plugin.validate(fixture.schedule(), rule)).singleElement()
                .satisfies(violation -> assertThat(violation.message()).contains("mercredi 7 octobre"));
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 1);
    }
}
