-- Données fictives de développement, indépendantes des migrations Flyway.
-- Exécuter avec psql après V1..V8 : -v week_start=2026-10-12 -f ce-fichier.sql.
-- Sans paramètre : semaine complète à partir du prochain lundi.
-- Identifiants déterministes par semaine. Un espace déjà chargé est laissé intact,
-- y compris ses modifications et résultats : aucun DELETE ni remise à zéro.
\set ON_ERROR_STOP on
\if :{?week_start}
\else
    select (date_trunc('week', current_date)::date + 7)::text as week_start \gset
\endif

begin;
set local search_path = planning, public;
set local lock_timeout = '5s';
create temporary table demo_period on commit drop as select :'week_start'::date as week_start;
select pg_advisory_xact_lock(hashtext('planning-realistic-v1:' || :'week_start'));

create temporary table demo_staff (
    profile text, code text, label text, skills text[], contract_minutes int,
    overtime_minutes int, absence_daily_minutes int, reinforcement_day int
) on commit drop;
insert into demo_staff values
    ('RESTAURANT', 'elodie', 'Élodie Martin', array['responsable','salle'], 2100, 0, 420, null),
    ('RESTAURANT', 'karim', 'Karim Benali', array['cuisine'], 2100, 0, 420, null),
    ('RESTAURANT', 'nora', 'Nora Petit', array['cuisine'], 2100, 180, 420, null),
    ('RESTAURANT', 'louise', 'Louise Bernard', array['salle'], 2100, 0, 420, null),
    ('RESTAURANT', 'hugo', 'Hugo Robert', array['salle'], 2100, 180, 420, null),
    ('RESTAURANT', 'ines', 'Inès Dubois', array['salle'], 2100, 0, 420, null),
    ('RESTAURANT', 'maya', 'Maya Laurent — renfort vendredi', array['salle'], 420, 0, 420, 4),
    ('COMPANY', 'camille', 'Camille Moreau', array['support'], 2100, 0, 420, null),
    ('COMPANY', 'jules', 'Jules Garcia', array['support'], 2100, 180, 420, null),
    ('COMPANY', 'sarah', 'Sarah Leroy', array['logistique'], 2100, 0, 420, null),
    ('COMPANY', 'mehdi', 'Mehdi Diallo', array['logistique'], 2100, 0, 420, null),
    ('COMPANY', 'claire', 'Claire Lefèvre', array['coordination'], 2100, 0, 420, null),
    ('COMPANY', 'thomas', 'Thomas Roux', array['accueil'], 2100, 0, 420, null),
    ('COMPANY', 'lila', 'Lila Simon — renfort mardi', array['support'], 420, 0, 420, 1);

-- Helper limité à la session : aucun ajout à l'API ou au schéma applicatif.
create or replace function pg_temp.add_demo_shift(
    scenario_uuid uuid, employee_type_uuid uuid, shift_date date,
    shift_label text, starts_at time, minutes int, roles jsonb
) returns void language plpgsql as $$
declare
    demand_uuid uuid := md5(scenario_uuid::text || ':' || shift_date::text || ':' || shift_label)::uuid;
    required_role jsonb;
begin
    insert into demand(id, scenario_id, label, duration_minutes, occurrences, attributes, placement)
    values(demand_uuid, scenario_uuid, shift_label, minutes, 1,
        jsonb_build_object('date', shift_date::text, 'service', shift_label, 'nextDay', false),
        jsonb_build_object('strategy', 'fixed', 'date', shift_date::text, 'startTime', starts_at::text));
    for required_role in select value from jsonb_array_elements(roles) loop
        insert into requirement(id, demand_id, role, type_id, quantity, criteria)
        values(md5(demand_uuid::text || ':' || (required_role->>'role'))::uuid,
            demand_uuid, required_role->>'role', employee_type_uuid,
            (required_role->>'quantity')::int,
            jsonb_build_object('contains', jsonb_build_object('path', '/competences', 'value', required_role->>'skill')));
    end loop;
end $$;

do $$
declare
    monday date := (select week_start from demo_period);
    variant record;
    staff record;
    workspace_code text;
    tenant_uuid uuid;
    scenario_uuid uuid;
    type_uuid uuid;
    model_uuid uuid;
    employee_uuid uuid;
    employee_code text;
    weekday int;
    opening_day int;
    closing_day int;
    opens_at time;
    closes_at time;
    service_roles jsonb;
    staff_scope jsonb := '{"eq":{"path":"/type","value":"employee"}}'::jsonb;
begin
    if extract(isodow from monday) <> 1 then
        raise exception 'week_start doit être un lundi : %', monday;
    end if;
    for variant in select * from (values
        ('restaurant-reference', 'RESTAURANT', 'Démo réaliste — Le Comptoir — Équilibré'),
        ('restaurant-tensions', 'RESTAURANT', 'Démo réaliste — Le Comptoir — Deux tensions'),
        ('company-reference', 'COMPANY', 'Démo réaliste — Atelier Horizon — Équilibré')
    ) as examples(code, profile, label) loop
        workspace_code := 'demo-realistic-v1-' || variant.code || '-' || monday::text;
        tenant_uuid := md5(workspace_code || ':tenant')::uuid;
        scenario_uuid := md5(workspace_code || ':scenario')::uuid;
        type_uuid := md5(workspace_code || ':employee-type')::uuid;
        model_uuid := md5(workspace_code || ':time-model')::uuid;
        if exists(select 1 from tenant where id = tenant_uuid) then
            raise notice 'Démo déjà présente, conservée : % (%)', variant.label, monday;
            continue;
        end if;
        insert into tenant(id, code, label, profile)
        values(tenant_uuid, workspace_code, variant.label || ' — ' || monday::text, variant.profile);
        insert into resource_type(id, tenant_id, code, label, attr_schema)
        values(type_uuid, tenant_uuid, 'employee', 'Personnel', '{}');
        insert into time_model(id, tenant_id, label, horizon_kind, horizon_from, horizon_to)
        values(model_uuid, tenant_uuid, 'Semaine du ' || monday::text, 'DATED', monday, monday + 6);
        insert into slot_template(id, time_model_id, day_in_cycle, ordinal, start_time, end_time)
        select md5(model_uuid::text || ':' || day || ':' || slot)::uuid, model_uuid, day, slot,
            (time '00:00' + slot * interval '30 minutes')::time,
            (time '00:00' + (slot + 1) * interval '30 minutes')::time
        from generate_series(0, 6) day cross join generate_series(0, 47) slot;
        insert into scenario(id, tenant_id, label, time_model_id, profile)
        values(scenario_uuid, tenant_uuid, variant.label || ' — Semaine du ' || monday::text, model_uuid, variant.profile);

        -- Politique identique aux espaces workforce créés par l'API actuelle.
        insert into constraint_weight(scenario_id, constraint_id, level, weight)
        select scenario_uuid, policy.type, policy.level, policy.weight from (values
            ('resource.conflict', 'HARD', 10000), ('resource.availability', 'HARD', 10000),
            ('booking.unassigned', 'HARD', 1), ('resource.discouraged_slot', 'SOFT', 1),
            ('resource.consistency', 'HARD', 0), ('rest.min', 'HARD', 10000),
            ('work.days_max', 'HARD', 10000), ('load.max', 'HARD', 10000), ('load.min', 'HARD', 1),
            ('load.contract', 'SOFT', 1), ('load.balance', 'SOFT', 1), ('fairness.weekend', 'SOFT', 10),
            ('demand.spread', 'SOFT', 0), ('gaps.avoid', 'SOFT', 0), ('day.span_max', 'SOFT', 0),
            ('time.preferred_window', 'SOFT', 0), ('time.weekly_regularity', 'HARD', 0),
            ('sequence.consecutive_max', 'SOFT', 0)
        ) policy(type, level, weight);
        insert into constraint_config(id, scenario_id, type, scope, params)
        select md5(scenario_uuid::text || ':policy:' || policy.type || ':' || policy.params)::uuid,
            scenario_uuid, policy.type, staff_scope, policy.params::jsonb from (values
            ('rest.min', '{"minMinutes":660}'), ('work.days_max', '{"maxDays":6}'),
            ('load.max', '{"maxMinutes":600,"period":"DAY"}'),
            ('load.contract', '{"deductAbsences":true}'), ('load.balance', '{}'), ('fairness.weekend', '{}')
        ) policy(type, params);

        opening_day := case when variant.profile = 'RESTAURANT' then 2 else 0 end;
        closing_day := case when variant.profile = 'RESTAURANT' then 6 else 4 end;
        opens_at := case when variant.profile = 'RESTAURANT' then time '11:30' else time '09:00' end;
        closes_at := case when variant.profile = 'RESTAURANT' then time '21:30' else time '19:00' end;
        for staff in select * from demo_staff where profile = variant.profile loop
            employee_uuid := md5(scenario_uuid::text || ':employee:' || staff.code)::uuid;
            employee_code := employee_uuid::text;
            insert into resource(id, scenario_id, type_id, label, default_effect, attributes)
            values(employee_uuid, scenario_uuid, type_uuid, staff.label, 'UNAVAILABLE', jsonb_build_object(
                'code', employee_code, 'competences', to_jsonb(staff.skills),
                'quota_hebdo_minutes', staff.contract_minutes, 'heures_supp_hebdo_minutes', staff.overtime_minutes,
                'minutes_absence_jour', staff.absence_daily_minutes));
            for weekday in opening_day..closing_day loop
                if staff.reinforcement_day is null or staff.reinforcement_day = weekday then
                    insert into resource_availability(id, resource_id, effect, day_in_cycle, start_time, end_time)
                    values(md5(employee_uuid::text || ':available:' || weekday)::uuid,
                        employee_uuid, 'AVAILABLE', weekday, opens_at, closes_at);
                end if;
            end loop;
            insert into constraint_config(id, scenario_id, type, scope, params)
            values(md5(employee_uuid::text || ':contract-min')::uuid, scenario_uuid, 'load.min',
                jsonb_build_object('eq', jsonb_build_object('path', '/code', 'value', employee_code)),
                jsonb_build_object('minMinutes', staff.contract_minutes, 'period', 'WEEK', 'deductAbsences', true)),
                (md5(employee_uuid::text || ':contract-max')::uuid, scenario_uuid, 'load.max',
                jsonb_build_object('eq', jsonb_build_object('path', '/code', 'value', employee_code)),
                jsonb_build_object('maxMinutes', staff.contract_minutes + staff.overtime_minutes,
                    'period', 'WEEK', 'deductAbsences', true));
        end loop;
        if variant.profile = 'RESTAURANT' then
            service_roles := '[{"role":"Responsable","quantity":1,"skill":"responsable"},
                {"role":"Cuisine","quantity":2,"skill":"cuisine"},{"role":"Salle","quantity":3,"skill":"salle"}]';
            for weekday in 2..6 loop
                perform pg_temp.add_demo_shift(scenario_uuid, type_uuid, monday + weekday,
                    'Préparation et service du midi', time '11:30', 210, service_roles);
                perform pg_temp.add_demo_shift(scenario_uuid, type_uuid, monday + weekday,
                    'Préparation et service du soir', time '18:00', 210, service_roles);
            end loop;
            -- 3 h de mise en place supplémentaires : Hugo est le seul renfort salle
            -- disponible le samedi avec une marge suffisante dans la référence
            -- équilibrée (10 h au total ce jour).
            perform pg_temp.add_demo_shift(scenario_uuid, type_uuid, monday + 5,
                'Mise en place du banquet', time '15:00', 180,
                '[{"role":"Renfort salle","quantity":1,"skill":"salle"}]');
            employee_uuid := md5(scenario_uuid::text || ':employee:ines')::uuid;
            insert into absence(id, scenario_id, resource_id, from_date, to_date, reason, note)
            values(md5(employee_uuid::text || ':leave')::uuid, scenario_uuid, employee_uuid,
                monday + 4, monday + 4, 'LEAVE', 'Congé fictif vendredi : 7 h déduites, remplacement par Maya.');
            if variant.code = 'restaurant-tensions' then
                -- Problème 1 : aucune compétence pâtisserie dans cette équipe.
                perform pg_temp.add_demo_shift(scenario_uuid, type_uuid, monday + 5,
                    'Desserts du banquet — compétence manquante', time '15:00', 120,
                    '[{"role":"Pâtisserie","quantity":1,"skill":"patisserie"}]');
                -- Problème 2 : 1 h d'indisponibilité bloque tout le poste indivisible
                -- du dimanche soir. Élodie récupère 3 h en salle au banquet ;
                -- il manque encore 30 minutes à son contrat de 35 h.
                employee_uuid := md5(scenario_uuid::text || ':employee:elodie')::uuid;
                insert into resource_availability(id, resource_id, effect, day_in_cycle, start_time, end_time, priority)
                values(md5(employee_uuid::text || ':sunday-unavailable')::uuid,
                    employee_uuid, 'UNAVAILABLE', 6, '18:00', '19:00', 10);
            end if;
        else
            service_roles := '[{"role":"Support","quantity":2,"skill":"support"},
                {"role":"Logistique","quantity":2,"skill":"logistique"},
                {"role":"Coordination","quantity":1,"skill":"coordination"},
                {"role":"Accueil","quantity":1,"skill":"accueil"}]';
            for weekday in 0..4 loop
                perform pg_temp.add_demo_shift(scenario_uuid, type_uuid, monday + weekday,
                    'Équipe du matin', time '09:00', 210, service_roles);
                perform pg_temp.add_demo_shift(scenario_uuid, type_uuid, monday + weekday,
                    'Équipe de l’après-midi', time '13:30', 210, service_roles);
            end loop;
            perform pg_temp.add_demo_shift(scenario_uuid, type_uuid, monday + 2,
                'Support — clôture mensuelle', time '17:00', 120,
                '[{"role":"Support supplémentaire","quantity":1,"skill":"support"}]');
            employee_uuid := md5(scenario_uuid::text || ':employee:camille')::uuid;
            insert into absence(id, scenario_id, resource_id, from_date, to_date, reason, note)
            values(md5(employee_uuid::text || ':sickness')::uuid, scenario_uuid, employee_uuid,
                monday + 1, monday + 1, 'SICKNESS', 'Maladie fictive mardi : 7 h déduites, remplacement par Lila.');
        end if;
        raise notice 'Démo créée : % (%)', variant.label, monday;
    end loop;
end $$;
commit;

select t.label, s.id as scenario_id, tm.horizon_from, tm.horizon_to,
    (select count(*) from resource r where r.scenario_id = s.id) as employees,
    (select count(*) from demand d where d.scenario_id = s.id) as shifts
from planning.tenant t join planning.scenario s on s.tenant_id = t.id
join planning.time_model tm on tm.id = s.time_model_id
where t.code like 'demo-realistic-v1-%' and tm.horizon_from = :'week_start'::date
order by t.code;
