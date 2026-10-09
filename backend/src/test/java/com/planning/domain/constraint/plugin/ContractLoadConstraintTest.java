package com.planning.domain.constraint.plugin;

import com.planning.domain.exception.IncompatibleConfigException;
import com.planning.domain.model.Resource;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractLoadConstraintTest {
    private final ContractLoadConstraint plugin = new ContractLoadConstraint();

    @Test void desContratsDifferentsOntDesCiblesDifferentesChaqueSemaine() {
        PluginTestSupport fixture = new PluginTestSupport(14);
        Resource partTime = fixture.resource("Temps partiel", "{\"quota_hebdo_minutes\":360}");
        Resource fullTime = fixture.resource("Temps complet", "{\"quota_hebdo_minutes\":900}");
        for (int day : new int[]{0, 7}) {
            fixture.work(partTime, day, 8, 360);
            fixture.work(fullTime, day, 8, 900);
        }
        var rule = fixture.rule(plugin, new ContractLoadConstraint.Params(), 1);
        assertThat(plugin.validate(fixture.schedule(), rule)).isEmpty();
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 0);
    }

    @Test void unEcartEtUneSemaineVideSontTousDeuxNotes() {
        PluginTestSupport fixture = new PluginTestSupport(14);
        Resource person = fixture.resource("Sam", "{\"quota_hebdo_minutes\":900}");
        fixture.work(person, 0, 8, 600);
        var rule = fixture.rule(plugin, new ContractLoadConstraint.Params(), 2);
        assertThat(plugin.validate(fixture.schedule(), rule)).hasSize(2)
                .extracting(v -> v.penalty()).containsExactlyInAnyOrder(600, 1800);
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 2400);
    }

    @Test void uneAbsenceTouteLaSemaineSupprimeSaCible() {
        PluginTestSupport fixture = new PluginTestSupport(14);
        Resource person = fixture.resource("Sam", "{\"quota_hebdo_minutes\":600}", fixture.unavailableDays(7, 14));
        fixture.work(person, 0, 8, 600);
        fixture.rule(plugin, new ContractLoadConstraint.Params(), 1);
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 0);
    }

    @Test void uneAbsencePartielleConserveLaCibleExpliciteDuContrat() {
        PluginTestSupport fixture = new PluginTestSupport(7);
        Resource person = fixture.resource("Sam", "{\"quota_hebdo_minutes\":600}", fixture.unavailableDays(1, 7));
        fixture.work(person, 0, 8, 480);
        fixture.rule(plugin, new ContractLoadConstraint.Params(), 1);
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 120);
    }

    @Test void deuxReglesConcurrentesMultiplientChaqueEcartUneSeuleFois() {
        PluginTestSupport fixture = new PluginTestSupport(7);
        Resource person = fixture.resource("Sam", "{\"code\":\"S\",\"quota_hebdo_minutes\":600}");
        fixture.work(person, 0, 8, 480);
        fixture.rule(plugin, new ContractLoadConstraint.Params(), 1);
        fixture.rule(plugin, "{\"eq\":{\"path\":\"/code\",\"value\":\"S\"}}", new ContractLoadConstraint.Params(), 3);
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 480);
    }

    @Test void uneRessourceSansQuotaEstIgnoreeEtUnQuotaInvalideEstSignale() {
        PluginTestSupport absent = new PluginTestSupport(7);
        absent.resource("Sans contrat", "{}");
        absent.rule(plugin, new ContractLoadConstraint.Params(), 1);
        PluginTestSupport.assertScore(plugin, absent.schedule(), 0);

        PluginTestSupport invalid = new PluginTestSupport(7);
        invalid.resource("Sam", "{\"quota_hebdo_minutes\":\"600\"}");
        var rule = invalid.rule(plugin, new ContractLoadConstraint.Params(), 1);
        assertThatThrownBy(() -> plugin.validate(invalid.schedule(), rule))
                .isInstanceOf(IncompatibleConfigException.class).hasMessageContaining("Sam");
    }

    @Test void uneNuitDeDimancheALundiEstRepartieEntreLesContratsHebdomadaires() {
        PluginTestSupport fixture = new PluginTestSupport(LocalDate.of(2026, 10, 11), 2);
        Resource person = fixture.resource("Sam", "{\"quota_hebdo_minutes\":300}");
        fixture.work(person, 0, 22, 240);
        fixture.work(person, 1, 8, 240);
        var general = fixture.rule(plugin, new ContractLoadConstraint.Params(), 1);
        fixture.rule(plugin, new ContractLoadConstraint.Params(), 3);

        assertThat(plugin.validate(fixture.schedule(), general)).hasSize(2)
                .extracting(violation -> violation.context().get("totalMinutes")).containsExactlyInAnyOrder(120, 360);
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 960);
    }

    @Test void uneCibleContractuelleNullePenaliseLeTravailMaisPasUneSemaineVide() {
        PluginTestSupport fixture = new PluginTestSupport(14);
        Resource person = fixture.resource("Sam", "{\"quota_hebdo_minutes\":0}");
        fixture.work(person, 0, 8, 60);
        var rule = fixture.rule(plugin, new ContractLoadConstraint.Params(), 1);
        assertThat(plugin.validate(fixture.schedule(), rule)).singleElement()
                .satisfies(violation -> assertThat(violation.penalty()).isEqualTo(60));
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 60);
    }
}
