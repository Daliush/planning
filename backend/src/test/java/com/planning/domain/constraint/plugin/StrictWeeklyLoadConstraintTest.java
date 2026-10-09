package com.planning.domain.constraint.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.domain.constraint.ParamsCodec;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.model.Level;
import com.planning.domain.model.Period;
import com.planning.domain.model.Resource;
import com.planning.support.ScheduleBuilder;
import com.planning.support.SchoolFixture;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrictWeeklyLoadConstraintTest {
    private static final String CONTRACT = "{\"quota_hebdo_minutes\":600,\"minutes_absence_jour\":120}";

    @Test
    void lePlancherHebdomadaireDeduitChaqueDateDAbsenceEtRapporteLeSeuilEffectif() {
        MinLoadConstraint plugin = new MinLoadConstraint();
        PluginTestSupport fixture = new PluginTestSupport(7);
        Resource person = fixture.resource("Alex", CONTRACT, fixture.absentDays(1, 3));
        fixture.work(person, 0, 8, 300);
        var rule = fixture.rule(plugin, new MinLoadConstraint.Params(600, Period.WEEK, true), 2);
        fixture.rule(plugin, new MinLoadConstraint.Params(480, Period.WEEK, true), 3);
        assertThat(plugin.validate(fixture.schedule(), rule)).singleElement().satisfies(violation -> {
            assertThat(violation.penalty()).isEqualTo(120);
            assertThat(violation.context()).containsEntry("minMinutes", 360);
            assertThat(violation.message()).contains("Alex").contains("6 h");
        });
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 120);
    }

    @Test
    void lePlancherNulOuEntierementCrediteNeProduitPasDeViolationVide() {
        MinLoadConstraint plugin = new MinLoadConstraint();
        PluginTestSupport fixture = new PluginTestSupport(7);
        fixture.resource("Alex", CONTRACT, fixture.absentDays(0, 7));
        fixture.rule(plugin, new MinLoadConstraint.Params(600, Period.WEEK, true), 1);
        fixture.rule(plugin, new MinLoadConstraint.Params(0, Period.WEEK), 1);
        assertThat(plugin.paramSchema().path("properties").path("minMinutes").path("minimum").intValue()).isZero();
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 0);
    }

    @Test
    void lePlafondDeduitLesAbsencesMaisConserveLesHeuresSupplementaires() {
        MaxLoadConstraint plugin = new MaxLoadConstraint();
        PluginTestSupport fixture = new PluginTestSupport(14);
        Resource person = fixture.resource("Alex", CONTRACT, fixture.absentDays(0, 7));
        fixture.work(person, 0, 8, 180);
        fixture.work(person, 7, 8, 780);
        var rule = fixture.rule(plugin, new MaxLoadConstraint.Params(720, Period.WEEK, true), 1);
        assertThat(plugin.validate(fixture.schedule(), rule)).hasSize(2)
                .extracting(violation -> violation.context().get("maxMinutes")).containsExactlyInAnyOrder(120, 720);
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 120);
    }

    @Test
    void laCibleContractuelleEstCrediteeSeulementPourLesAbsencesDatees() {
        ContractLoadConstraint plugin = new ContractLoadConstraint();
        PluginTestSupport fixture = new PluginTestSupport(14);
        Resource person = fixture.resource("Alex", CONTRACT, fixture.absentDays(0, 7));
        fixture.work(person, 7, 8, 480);
        var rule = fixture.rule(plugin, new ContractLoadConstraint.Params(true), 1);
        assertThat(plugin.validate(fixture.schedule(), rule)).singleElement()
                .satisfies(violation -> assertThat(violation.context()).containsEntry("targetMinutes", 600));
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 120);

        PluginTestSupport unavailable = new PluginTestSupport(7);
        unavailable.resource("Sans disponibilité", CONTRACT, unavailable.unavailableDays(0, 7));
        unavailable.rule(plugin, new ContractLoadConstraint.Params(true), 1);
        PluginTestSupport.assertScore(plugin, unavailable.schedule(), 600);
    }

    @Test
    void leFlagDesactiveConserveLesSeuilsHistoriques() {
        MinLoadConstraint minimum = new MinLoadConstraint();
        PluginTestSupport minFixture = new PluginTestSupport(7);
        Resource minPerson = minFixture.resource("Alex", CONTRACT, minFixture.absentDays(1, 3));
        minFixture.work(minPerson, 0, 8, 480);
        minFixture.rule(minimum, new MinLoadConstraint.Params(600, Period.WEEK), 1);
        PluginTestSupport.assertScore(minimum, minFixture.schedule(), 120);

        MaxLoadConstraint maximum = new MaxLoadConstraint();
        PluginTestSupport maxFixture = new PluginTestSupport(7);
        Resource maxPerson = maxFixture.resource("Alex", CONTRACT, maxFixture.absentDays(1, 3));
        maxFixture.work(maxPerson, 0, 8, 660);
        maxFixture.rule(maximum, new MaxLoadConstraint.Params(720, Period.WEEK), 1);
        PluginTestSupport.assertScore(maximum, maxFixture.schedule(), 0);

        ContractLoadConstraint contract = new ContractLoadConstraint();
        PluginTestSupport contractFixture = new PluginTestSupport(7);
        contractFixture.resource("Alex", CONTRACT, contractFixture.absentDays(0, 7));
        contractFixture.rule(contract, new ContractLoadConstraint.Params(), 1);
        PluginTestSupport.assertScore(contract, contractFixture.schedule(), 0);
    }

    @Test
    void lesAutresPeriodesEtLeCycleNeSontPasProratises() {
        MinLoadConstraint minimum = new MinLoadConstraint();
        PluginTestSupport dated = new PluginTestSupport(2);
        dated.resource("Alex", CONTRACT, dated.absentDays(0, 2));
        dated.rule(minimum, new MinLoadConstraint.Params(60, Period.DAY, true), 1);
        PluginTestSupport.assertScore(minimum, dated.schedule(), 120);

        for (boolean deduct : new boolean[]{false, true}) {
            ScheduleBuilder cyclic = new ScheduleBuilder();
            Resource person = cyclic.resource("Alex", CONTRACT);
            cyclic.booking(cyclic.activity("Cours", UUID.randomUUID(), 0, 60,
                    cyclic.slot(0, LocalTime.of(8, 0)), "{}"), "participant", person);
            cyclic.config(minimum.type(), "{}", new MinLoadConstraint.Params(180, Period.WEEK, deduct), 1, Level.SOFT);
            PluginTestSupport.assertScore(minimum, cyclic.build(), 120);
        }
    }

    @Test
    void lesSchemasAcceptentLesAnciensParametresEtExigentUnVraiBooleen() {
        ParamsCodec codec = new ParamsCodec(new ObjectMapper());
        assertThat(codec.decode(new ContractLoadConstraint(), SchoolFixture.json("{}")))
                .isEqualTo(new ContractLoadConstraint.Params(false));
        assertThat(codec.decode(new MinLoadConstraint(), SchoolFixture.json("{\"minMinutes\":0,\"period\":\"WEEK\"}")))
                .isEqualTo(new MinLoadConstraint.Params(0, Period.WEEK, false));
        assertThat(codec.decode(new MaxLoadConstraint(), SchoolFixture.json("{\"maxMinutes\":720,\"period\":\"WEEK\",\"deductAbsences\":true}")))
                .isEqualTo(new MaxLoadConstraint.Params(720, Period.WEEK, true));
        assertThatThrownBy(() -> codec.decode(new ContractLoadConstraint(), SchoolFixture.json("{\"deductAbsences\":\"true\"}")))
                .isInstanceOf(InvalidConfigException.class);
    }
}
