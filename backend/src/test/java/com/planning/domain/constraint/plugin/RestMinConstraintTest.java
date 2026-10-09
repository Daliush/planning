package com.planning.domain.constraint.plugin;

import com.planning.domain.exception.IncompatibleConfigException;
import com.planning.domain.model.Resource;
import com.planning.support.ScheduleBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestMinConstraintTest {
    private final RestMinConstraint plugin = new RestMinConstraint();

    @Test void unReposSuffisantEstConformeAuScoreEtAuRapport() {
        PluginTestSupport fixture = new PluginTestSupport(3);
        Resource person = fixture.resource("Camille", "{}");
        fixture.work(person, 0, 8, 480);
        fixture.work(person, 1, 8, 480);
        var rule = fixture.rule(plugin, new RestMinConstraint.Params(660), 1);

        assertThat(plugin.validate(fixture.schedule(), rule)).isEmpty();
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 0);
    }

    @Test void uneNuitUtiliseLaDateDeFinEtLesMinutesManquantes() {
        PluginTestSupport fixture = new PluginTestSupport(3);
        Resource person = fixture.resource("Camille", "{}");
        var night = fixture.work(person, 0, 22, 480);
        var following = fixture.work(person, 1, 12, 240);
        var rule = fixture.rule(plugin, new RestMinConstraint.Params(660), 2);

        assertThat(plugin.validate(fixture.schedule(), rule)).singleElement().satisfies(violation -> {
            assertThat(violation.penalty()).isEqualTo(600);
            assertThat(violation.message()).contains("Camille", "6 h", "11 h");
            assertThat(violation.resourceIds()).containsExactly(person.id());
            assertThat(violation.activityIds()).containsExactlyInAnyOrder(night.getActivity().getDemandId(), following.getActivity().getDemandId());
            assertThat(violation.context()).containsEntry("previousEnd", "2026-10-06T06:00");
        });
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 600);
    }

    @Test void lesPostesDuMemeJourNeCreentPasDeuxJournees() {
        PluginTestSupport fixture = new PluginTestSupport(2);
        Resource person = fixture.resource("Camille", "{}");
        fixture.work(person, 0, 8, 240);
        fixture.work(person, 0, 16, 240);
        var rule = fixture.rule(plugin, new RestMinConstraint.Params(660), 1);

        assertThat(plugin.validate(fixture.schedule(), rule)).isEmpty();
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 0);
    }

    @Test void deuxReglesConcurrentesGardentLeursSeuilsEtPoids() {
        PluginTestSupport fixture = new PluginTestSupport(3);
        Resource person = fixture.resource("Camille", "{\"code\":\"C\"}");
        fixture.work(person, 0, 22, 480);
        fixture.work(person, 1, 12, 240);
        var general = fixture.rule(plugin, new RestMinConstraint.Params(660), 1);
        var specific = fixture.rule(plugin, "{\"eq\":{\"path\":\"/code\",\"value\":\"C\"}}",
                new RestMinConstraint.Params(480), 3);

        assertThat(plugin.validate(fixture.schedule(), general)).singleElement().satisfies(v -> assertThat(v.penalty()).isEqualTo(300));
        assertThat(plugin.validate(fixture.schedule(), specific)).singleElement().satisfies(v -> assertThat(v.penalty()).isEqualTo(360));
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 660);
    }

    @Test void uneRegleHorsScopeEstIgnoree() {
        PluginTestSupport fixture = new PluginTestSupport(2);
        Resource person = fixture.resource("Camille", "{\"code\":\"C\"}");
        fixture.work(person, 0, 22, 480);
        fixture.work(person, 1, 8, 240);
        var rule = fixture.rule(plugin, "{\"eq\":{\"path\":\"/code\",\"value\":\"X\"}}",
                new RestMinConstraint.Params(660), 1);
        assertThat(plugin.validate(fixture.schedule(), rule)).isEmpty();
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 0);
    }

    @Test void unHorizonCycliqueEstRefuse() {
        PluginTestSupport fixture = new PluginTestSupport(1);
        var rule = fixture.rule(plugin, new RestMinConstraint.Params(660), 1);
        assertThatThrownBy(() -> plugin.validate(rule, new ScheduleBuilder().horizon()))
                .isInstanceOf(IncompatibleConfigException.class);
    }

    @Test void unReposNulDesactiveLaBorneSansRejeterLaConfiguration() {
        PluginTestSupport fixture = new PluginTestSupport(2);
        Resource person = fixture.resource("Camille", "{}");
        fixture.work(person, 0, 22, 480);
        fixture.work(person, 1, 6, 240);
        var rule = fixture.rule(plugin, new RestMinConstraint.Params(0), 1);
        plugin.validate(rule, fixture.horizon());
        assertThat(plugin.paramSchema().path("properties").path("minMinutes").path("minimum").intValue()).isZero();
        assertThat(plugin.validate(fixture.schedule(), rule)).isEmpty();
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 0);
    }
}
