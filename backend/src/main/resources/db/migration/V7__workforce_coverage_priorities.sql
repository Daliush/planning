-- Corrige uniquement les espaces métier qui possèdent encore toute la politique
-- V2 par défaut. Une modification experte de poids ou de règle préserve l'espace.
-- Les résultats précédents restent historiques ; les scénarios corrigés sont
-- remis en brouillon afin de demander un recalcul avec les nouvelles priorités.
with old_weights(constraint_id, level, weight) as (
    values
        ('resource.conflict', 'HARD', 1000),
        ('resource.availability', 'HARD', 1000),
        ('booking.unassigned', 'HARD', 1000),
        ('resource.discouraged_slot', 'SOFT', 1),
        ('resource.consistency', 'HARD', 0),
        ('rest.min', 'HARD', 10),
        ('work.days_max', 'HARD', 10),
        ('load.max', 'HARD', 1),
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
), candidates as (
    select s.id
    from scenario s
    join tenant t on t.id = s.tenant_id and t.profile = s.profile
    join time_model tm on tm.id = s.time_model_id and tm.tenant_id = s.tenant_id
    where s.profile in ('RESTAURANT', 'COMPANY')
      and tm.horizon_kind = 'DATED'
      and s.status in ('DRAFT', 'SOLVED')
      and (select count(*) from constraint_weight w where w.scenario_id = s.id) = 18
      and not exists (
          select 1 from constraint_weight w
          where w.scenario_id = s.id and not exists (
              select 1 from old_weights ow
              where ow.constraint_id = w.constraint_id
                and ow.level = w.level and ow.weight = w.weight
          )
      )
      and not exists (
          select 1 from resource r
          join resource_type rt on rt.id = r.type_id
          where r.scenario_id = s.id
            and (rt.code <> 'employee' or rt.tenant_id <> s.tenant_id)
      )
), base_configs(type, params) as (
    values
        ('rest.min', '{"minMinutes":660}'::jsonb),
        ('work.days_max', '{"maxDays":6}'::jsonb),
        ('load.max', '{"maxMinutes":600,"period":"DAY"}'::jsonb),
        ('load.contract', '{}'::jsonb),
        ('load.balance', '{}'::jsonb),
        ('fairness.weekend', '{}'::jsonb)
), expected_configs as (
    select c.id as scenario_id, b.type,
           '{"eq":{"path":"/type","value":"employee"}}'::jsonb as scope,
           b.params, 1 as weight, true as enabled
    from candidates c cross join base_configs b
    union all
    select c.id, 'load.max',
           jsonb_build_object('eq', jsonb_build_object('path', '/code', 'value', r.attributes->'code')),
           jsonb_build_object('maxMinutes', r.attributes->'quota_hebdo_minutes', 'period', 'WEEK'),
           1, true
    from candidates c join resource r on r.scenario_id = c.id
), eligible as (
    select c.id from candidates c
    where (select count(*) from constraint_config cc where cc.scenario_id = c.id)
        = (select count(*) from expected_configs ec where ec.scenario_id = c.id)
      and not exists (
          select 1 from expected_configs ec
          where ec.scenario_id = c.id and not exists (
              select 1 from constraint_config cc
              where cc.scenario_id = ec.scenario_id and cc.type = ec.type
                and cc.scope = ec.scope and cc.params = ec.params
                and cc.weight = ec.weight and cc.enabled = ec.enabled
          )
      )
), updated_weights as (
    update constraint_weight w
    set weight = case when w.constraint_id = 'booking.unassigned' then 1 else 1000 end
    from eligible e
    where w.scenario_id = e.id
      and w.constraint_id in ('booking.unassigned', 'rest.min', 'work.days_max', 'load.max')
    returning w.scenario_id
)
update scenario s set status = 'DRAFT'
where s.status = 'SOLVED' and s.id in (select scenario_id from updated_weights);
