package com.planning.domain.constraint.plugin;

import com.planning.domain.model.Resource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadBalanceConstraintTest {
    private final LoadBalanceConstraint plugin = new LoadBalanceConstraint();

    @Test void uneChargeSuperieureARacineDeIntegerMaxNeDebordePasAvantDivision() {
        PluginTestSupport fixture = new PluginTestSupport(40);
        Resource person = fixture.resource("Charge annuelle", "{}");
        fixture.work(person, 0, 0, 50_040);
        var rule = fixture.rule(plugin, new LoadBalanceConstraint.Params(), 3);
        assertThat(plugin.validate(fixture.schedule(), rule)).singleElement()
                .satisfies(violation -> assertThat(violation.penalty()).isEqualTo(7_512_004));
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 7_512_004);
    }

    @Test void unePenaliteHorsCapaciteDuScoreEstRefuseeAuLieuDeChangerDeSigne() {
        PluginTestSupport fixture = new PluginTestSupport(1);
        Resource person = fixture.resource("Charge excessive", "{}");
        fixture.work(person, 0, 0, 2_000_000);
        var rule = fixture.rule(plugin, new LoadBalanceConstraint.Params(), 1);
        assertThatThrownBy(() -> plugin.validate(fixture.schedule(), rule)).isInstanceOf(ArithmeticException.class);
    }

    @Test void deuxReglesConcurrentesAppliquentLeurPoidsSansChangerLarrondi() {
        PluginTestSupport fixture = new PluginTestSupport(1);
        Resource person = fixture.resource("Alex", "{}");
        fixture.work(person, 0, 8, 60);
        fixture.rule(plugin, new LoadBalanceConstraint.Params(), 1);
        fixture.rule(plugin, new LoadBalanceConstraint.Params(), 3);
        // floor(60² / 1000) + floor(3 * 60² / 1000) = 3 + 10.
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 13);
    }
}
