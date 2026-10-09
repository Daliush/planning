-- Le cycle scolaire reste inchangé. Les dates ne concernent que les nouveaux horizons.
alter table slot_template drop constraint slot_template_range_check;
alter table slot_template add constraint slot_template_range_check
    check (start_time < end_time or (end_time = '00:00'::time and start_time > end_time));

alter table resource add constraint resource_id_scenario_unique unique (id, scenario_id);
create table absence (
    id uuid primary key,
    scenario_id uuid not null references scenario(id) on delete cascade,
    resource_id uuid not null,
    from_date date not null,
    to_date date not null,
    reason text not null,
    note text,
    constraint absence_resource_scenario_fk foreign key (resource_id, scenario_id)
        references resource(id, scenario_id) on delete cascade,
    constraint absence_dates_check check (from_date <= to_date),
    constraint absence_reason_check check (reason in ('LEAVE', 'SICKNESS', 'OTHER'))
);
create index absence_scenario_dates_idx on absence(scenario_id, from_date, to_date);

alter table assignment add column start_date date;
alter table assignment add column end_date date;
alter table assignment drop constraint assignment_range_check;
alter table assignment add constraint assignment_range_check check (
    (start_date is null and end_date is null and start_time < end_time)
    or (start_date is not null and end_date is not null
        and (start_date + start_time) < (end_date + end_time))
);
