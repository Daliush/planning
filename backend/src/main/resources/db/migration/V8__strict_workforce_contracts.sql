-- Les plages récurrentes peuvent traverser minuit. Une journée entière reste
-- représentée par deux bornes NULL ; deux heures identiques sont ambiguës.
alter table resource_availability drop constraint resource_availability_range_check;
alter table resource_availability add constraint resource_availability_range_check
    check (start_time is null or end_time is null or start_time <> end_time);

-- La nouvelle politique concerne uniquement les espaces créés par la façade.
-- Toute modification experte du jeu de poids, ajout de règle ou modification
-- d'un scope préserve l'espace. Les réglages métier repos/jour/équilibrage gardent
-- leurs valeurs. Les scénarios publiés, archivés ou en cours de calcul sont exclus.
do $$
declare
    workforce_ids uuid[];
begin
    with old_weights(constraint_id, level, weight) as (
        values
            ('resource.conflict', 'HARD', 1000),
            ('resource.availability', 'HARD', 1000),
            ('booking.unassigned', 'HARD', 1),
            ('resource.discouraged_slot', 'SOFT', 1),
            ('resource.consistency', 'HARD', 0),
            ('rest.min', 'HARD', 1000),
            ('work.days_max', 'HARD', 1000),
            ('load.max', 'HARD', 1000),
            ('load.contract', 'SOFT', 1),
            ('load.balance', 'SOFT', 1),
            ('fairness.weekend', 'SOFT', 10),
            ('load.min', 'SOFT', 0),
            ('demand.spread', 'SOFT', 0),
            ('gaps.avoid', 'SOFT', 0),
            ('day.span_max', 'SOFT', 0),
            ('time.preferred_window', 'SOFT', 0),
            ('time.weekly_regularity', 'HARD', 0),
            ('sequence.consecutive_max', 'SOFT', 0)
    ), old_policies as (
        select policy, ow.constraint_id, ow.level,
               case when policy = 2 then case ow.constraint_id
                   when 'booking.unassigned' then 1000
                   when 'rest.min' then 10 when 'work.days_max' then 10
                   when 'load.max' then 1 else ow.weight end
                   else ow.weight end as weight
        from old_weights ow cross join (values (2), (7)) versions(policy)
    ), employees as (
        select r.*, rt.code as type_code, rt.tenant_id as type_tenant_id,
               case when jsonb_typeof(r.attributes->'code') = 'string'
                    then r.attributes->>'code' end as employee_code,
               case when jsonb_typeof(r.attributes->'quota_hebdo_minutes') = 'number'
                          and r.attributes->>'quota_hebdo_minutes' ~ '^[0-9]{1,5}$'
                    then (r.attributes->>'quota_hebdo_minutes')::int end as contract_minutes,
               case when r.attributes->'heures_supp_hebdo_minutes' is null
                          or r.attributes->'heures_supp_hebdo_minutes' = 'null'::jsonb then 0
                    when jsonb_typeof(r.attributes->'heures_supp_hebdo_minutes') = 'number'
                         and r.attributes->>'heures_supp_hebdo_minutes' ~ '^[0-9]{1,5}$'
                    then (r.attributes->>'heures_supp_hebdo_minutes')::int end as overtime_minutes,
               case when r.attributes->'minutes_absence_jour' is null
                          or r.attributes->'minutes_absence_jour' = 'null'::jsonb then 0
                    when jsonb_typeof(r.attributes->'minutes_absence_jour') = 'number'
                         and r.attributes->>'minutes_absence_jour' ~ '^[0-9]{1,4}$'
                    then (r.attributes->>'minutes_absence_jour')::int end as absence_minutes
        from resource r join resource_type rt on rt.id = r.type_id
    ), base_configs as (
        select cc.id, cc.scenario_id, cc.type from constraint_config cc
        where cc.scope = '{"eq":{"path":"/type","value":"employee"}}'::jsonb
          and cc.weight = 1 and (cc.enabled or cc.type = 'load.balance')
          and (
              (cc.type = 'rest.min' and cc.params = jsonb_build_object('minMinutes', cc.params->'minMinutes')
               and case when jsonb_typeof(cc.params->'minMinutes') = 'number'
                             and cc.params->>'minMinutes' ~ '^[0-9]{1,4}$'
                        then (cc.params->>'minMinutes')::int between 0 and 2880
                             and (cc.params->>'minMinutes')::int % 30 = 0 else false end)
              or (cc.type = 'work.days_max' and cc.params = '{"maxDays":6}'::jsonb)
              or (cc.type = 'load.max' and cc.params = jsonb_build_object(
                      'maxMinutes', cc.params->'maxMinutes', 'period', 'DAY')
                  and case when jsonb_typeof(cc.params->'maxMinutes') = 'number'
                                and cc.params->>'maxMinutes' ~ '^[0-9]{1,4}$'
                           then (cc.params->>'maxMinutes')::int between 30 and 1440
                                and (cc.params->>'maxMinutes')::int % 30 = 0 else false end)
              or (cc.type in ('load.contract', 'load.balance', 'fairness.weekend') and cc.params = '{}'::jsonb)
          )
    ), employee_configs as (
        select cc.id, cc.scenario_id, e.id as resource_id
        from constraint_config cc join employees e on e.scenario_id = cc.scenario_id
        where cc.type = 'load.max' and cc.weight = 1 and cc.enabled
          and cc.scope = jsonb_build_object('eq', jsonb_build_object('path', '/code', 'value', e.employee_code))
          and cc.params = jsonb_build_object('maxMinutes', e.contract_minutes, 'period', 'WEEK')
    )
    select array_agg(s.id) into workforce_ids
    from scenario s
    join tenant t on t.id = s.tenant_id and t.profile = s.profile
    join time_model tm on tm.id = s.time_model_id and tm.tenant_id = s.tenant_id
    where s.profile in ('RESTAURANT', 'COMPANY') and tm.horizon_kind = 'DATED'
      and t.code = 'workforce-' || t.id::text and s.status in ('DRAFT', 'SOLVED')
      and (select count(*) from constraint_weight w where w.scenario_id = s.id) = 18
      and exists (
          select 1 from (values (2), (7)) versions(policy)
          where not exists (
              select 1 from old_policies op where op.policy = versions.policy and not exists (
                  select 1 from constraint_weight w
                  where w.scenario_id = s.id and w.constraint_id = op.constraint_id
                    and w.level = op.level and w.weight = op.weight
              )
          )
      )
      and not exists (
          select 1 from employees e where e.scenario_id = s.id and not coalesce(
              e.type_code = 'employee' and e.type_tenant_id = s.tenant_id and e.employee_code <> ''
              and e.contract_minutes between 0 and 10080 and e.contract_minutes % 30 = 0
              and e.overtime_minutes between 0 and 10080 and e.overtime_minutes % 30 = 0
              and e.contract_minutes + e.overtime_minutes <= 10080
              and e.absence_minutes between 0 and 1440 and e.absence_minutes % 30 = 0, false)
      )
      and not exists (
          select 1 from employees e where e.scenario_id = s.id
          group by e.employee_code having count(*) > 1
      )
      and (select count(distinct bc.type) from base_configs bc where bc.scenario_id = s.id) = 6
      and (select count(*) from constraint_config cc where cc.scenario_id = s.id)
          = 6 + (select count(*) from employees e where e.scenario_id = s.id)
      and not exists (
          select 1 from constraint_config cc where cc.scenario_id = s.id
            and not exists (select 1 from base_configs bc where bc.id = cc.id)
            and not exists (select 1 from employee_configs ec where ec.id = cc.id)
      )
      and not exists (
          select 1 from employees e where e.scenario_id = s.id
            and not exists (select 1 from employee_configs ec where ec.resource_id = e.id)
      );

    if workforce_ids is null then return; end if;

    update resource r set attributes = r.attributes || jsonb_build_object(
        'heures_supp_hebdo_minutes', coalesce(nullif(r.attributes->'heures_supp_hebdo_minutes', 'null'::jsonb), '0'::jsonb),
        'minutes_absence_jour', coalesce(nullif(r.attributes->'minutes_absence_jour', 'null'::jsonb),
            to_jsonb(least(1440, round((r.attributes->>'quota_hebdo_minutes')::numeric / 150)::int * 30))))
    where r.scenario_id = any(workforce_ids);

    insert into constraint_weight(scenario_id, constraint_id, level, weight)
    select scenario_id, policy.constraint_id, policy.level, policy.weight
    from unnest(workforce_ids) scenarios(scenario_id) cross join (values
        ('booking.unassigned', 'HARD', 1), ('load.min', 'HARD', 1),
        ('resource.conflict', 'HARD', 10000), ('resource.availability', 'HARD', 10000),
        ('rest.min', 'HARD', 10000), ('work.days_max', 'HARD', 10000), ('load.max', 'HARD', 10000)
    ) policy(constraint_id, level, weight)
    on conflict (scenario_id, constraint_id) do update set level = excluded.level, weight = excluded.weight;

    delete from constraint_config cc using resource r
    where r.scenario_id = any(workforce_ids) and cc.scenario_id = r.scenario_id
      and cc.type in ('load.min', 'load.max') and cc.params->>'period' = 'WEEK'
      and cc.scope = jsonb_build_object('eq', jsonb_build_object('path', '/code', 'value', r.attributes->>'code'));

    insert into constraint_config(id, scenario_id, type, scope, params, weight, enabled)
    select md5(r.id::text || ':contract-min:v8')::uuid, r.scenario_id, 'load.min',
           jsonb_build_object('eq', jsonb_build_object('path', '/code', 'value', r.attributes->>'code')),
           jsonb_build_object('minMinutes', (r.attributes->>'quota_hebdo_minutes')::int,
                             'period', 'WEEK', 'deductAbsences', true), 1, true
    from resource r where r.scenario_id = any(workforce_ids);

    insert into constraint_config(id, scenario_id, type, scope, params, weight, enabled)
    select md5(r.id::text || ':contract-max:v8')::uuid, r.scenario_id, 'load.max',
           jsonb_build_object('eq', jsonb_build_object('path', '/code', 'value', r.attributes->>'code')),
           jsonb_build_object('maxMinutes', (r.attributes->>'quota_hebdo_minutes')::int
                                            + (r.attributes->>'heures_supp_hebdo_minutes')::int,
                             'period', 'WEEK', 'deductAbsences', true), 1, true
    from resource r where r.scenario_id = any(workforce_ids);

    update constraint_config set params = '{"deductAbsences":true}'::jsonb
    where scenario_id = any(workforce_ids) and type = 'load.contract';

    update scenario set status = 'DRAFT' where id = any(workforce_ids) and status = 'SOLVED';
end $$;
