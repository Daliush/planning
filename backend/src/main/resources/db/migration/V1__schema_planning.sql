-- ============================================================================
--  Moteur de planification — schéma initial (SDD §6)
--
--  INVARIANT §5.3 : aucun index de créneau en base. On ne stocke que des durées
--  en minutes et des heures réelles ; la grille est un paramètre modifiable et
--  la conversion se fait au chargement.
-- ============================================================================

-- ─── Référentiel racine ─────────────────────────────────────────────────────

create table tenant (
    id         uuid primary key,
    code       text        not null unique,
    label      text        not null,
    profile    text        not null,
    created_at timestamptz not null default now(),
    constraint tenant_profile_check
        check (profile in ('SCHOOL', 'COMPANY', 'RESTAURANT'))
);

create table resource_type (
    id          uuid primary key,
    tenant_id   uuid not null references tenant (id) on delete cascade,
    code        text not null,
    label       text not null,
    attr_schema jsonb not null default '{}'::jsonb,
    constraint resource_type_code_unique unique (tenant_id, code)
);

-- ─── Temps ──────────────────────────────────────────────────────────────────

create table time_model (
    id               uuid primary key,
    tenant_id        uuid not null references tenant (id) on delete cascade,
    label            text not null,
    horizon_kind     text not null,
    cycle_days       int,
    horizon_from     date,
    horizon_to       date,
    max_gap_minutes  int  not null default 0,
    constraint time_model_kind_check
        check (horizon_kind in ('CYCLIC', 'DATED')),
    -- Les colonnes requises dépendent du mode : le CHECK rend impossible un
    -- modèle cyclique sans longueur de cycle, ou daté sans bornes.
    constraint time_model_cyclic_check
        check (horizon_kind <> 'CYCLIC' or cycle_days is not null and cycle_days > 0),
    constraint time_model_dated_check
        check (horizon_kind <> 'DATED'
               or (horizon_from is not null and horizon_to is not null
                   and horizon_from <= horizon_to)),
    constraint time_model_gap_check check (max_gap_minutes >= 0)
);

create table slot_template (
    id            uuid primary key,
    time_model_id uuid not null references time_model (id) on delete cascade,
    day_in_cycle  int  not null,
    ordinal       int  not null,
    start_time    time not null,
    end_time      time not null,
    constraint slot_template_unique unique (time_model_id, day_in_cycle, ordinal),
    constraint slot_template_day_check check (day_in_cycle >= 0),
    constraint slot_template_range_check check (start_time < end_time)
);

create index slot_template_model_idx on slot_template (time_model_id, day_in_cycle, ordinal);

-- ─── Scénario ───────────────────────────────────────────────────────────────

create table scenario (
    id            uuid primary key,
    tenant_id     uuid not null references tenant (id) on delete cascade,
    label         text not null,
    time_model_id uuid not null references time_model (id),
    status        text not null default 'DRAFT',
    parent_id     uuid references scenario (id),
    created_at    timestamptz not null default now(),
    constraint scenario_status_check
        check (status in ('DRAFT', 'SOLVING', 'SOLVED', 'PUBLISHED', 'ARCHIVED'))
);

create index scenario_tenant_idx on scenario (tenant_id);

-- Projection du cycle sur le calendrier réel. Ne participe pas à la résolution
-- (§6.8) : le solveur ne voit jamais cette table.
create table calendar_week (
    scenario_id  uuid not null references scenario (id) on delete cascade,
    week_number  int  not null,
    worked       boolean not null default true,
    cycle_offset int  not null default 0,
    primary key (scenario_id, week_number),
    constraint calendar_week_number_check check (week_number between 1 and 53)
);

-- ─── Ressources ─────────────────────────────────────────────────────────────

create table resource (
    id             uuid primary key,
    scenario_id    uuid not null references scenario (id) on delete cascade,
    type_id        uuid not null references resource_type (id),
    label          text not null,
    default_effect text not null default 'AVAILABLE',
    attributes     jsonb not null default '{}'::jsonb,
    constraint resource_default_effect_check
        check (default_effect in ('AVAILABLE', 'UNAVAILABLE', 'DISCOURAGED'))
);

create index resource_scenario_idx on resource (scenario_id);
create index resource_type_idx on resource (type_id);

-- Motif récurrent sur le cycle, compilé en BitSet au chargement (§6.5).
-- Une colonne NULL signifie « pas de restriction sur cet axe ».
create table resource_availability (
    id           uuid primary key,
    resource_id  uuid not null references resource (id) on delete cascade,
    effect       text not null,
    day_in_cycle int,
    start_time   time,
    end_time     time,
    priority     int  not null default 0,
    constraint resource_availability_effect_check
        check (effect in ('AVAILABLE', 'UNAVAILABLE', 'DISCOURAGED')),
    constraint resource_availability_day_check
        check (day_in_cycle is null or day_in_cycle >= 0),
    constraint resource_availability_range_check
        check (start_time is null or end_time is null or start_time < end_time)
);

create index resource_availability_resource_idx
    on resource_availability (resource_id, priority);

-- ─── Demande ────────────────────────────────────────────────────────────────

create table demand (
    id               uuid primary key,
    scenario_id      uuid not null references scenario (id) on delete cascade,
    label            text not null,
    duration_minutes int  not null,
    occurrences      int  not null default 1,
    -- `attributes` est lu par les scopes de contraintes, `placement` par le
    -- builder. Les séparer évite qu'un scope matche une clé de placement (§6.9).
    attributes       jsonb not null default '{}'::jsonb,
    placement        jsonb not null default '{"strategy":"free"}'::jsonb,
    constraint demand_duration_check check (duration_minutes > 0),
    constraint demand_occurrences_check check (occurrences > 0)
);

create index demand_scenario_idx on demand (scenario_id);

create table requirement (
    id        uuid primary key,
    demand_id uuid not null references demand (id) on delete cascade,
    -- INVARIANT §6.10 : le rôle (fonction occupée) reste distinct du type
    -- (nature de la ressource), même quand ils coïncident.
    role      text not null,
    type_id   uuid not null references resource_type (id),
    quantity  int  not null default 1,
    criteria  jsonb not null default '{}'::jsonb,
    constraint requirement_quantity_check check (quantity > 0)
);

create index requirement_demand_idx on requirement (demand_id);

-- ─── Règles ─────────────────────────────────────────────────────────────────

-- Ces lignes ne se compilent pas : elles restent des faits vivants pendant
-- toute la résolution (§2.3, exception unique).
create table constraint_config (
    id          uuid primary key,
    scenario_id uuid not null references scenario (id) on delete cascade,
    type        text not null,
    scope       jsonb not null default '{}'::jsonb,
    params      jsonb not null default '{}'::jsonb,
    weight      int  not null default 1,
    enabled     boolean not null default true,
    constraint constraint_config_weight_check check (weight >= 0)
);

create index constraint_config_scenario_idx on constraint_config (scenario_id, type);

-- Poids ① : l'arbitrage de politique entre types de contraintes.
-- `level` + `weight` plutôt que deux colonnes hard/soft : l'exclusivité
-- devient structurelle (§6.12).
create table constraint_weight (
    scenario_id   uuid not null references scenario (id) on delete cascade,
    constraint_id text not null,
    level         text not null,
    weight        int  not null,
    primary key (scenario_id, constraint_id),
    constraint constraint_weight_level_check check (level in ('HARD', 'SOFT')),
    constraint constraint_weight_weight_check check (weight >= 0)
);

-- ─── Résultat ───────────────────────────────────────────────────────────────

create table solve_job (
    id                 uuid primary key,
    scenario_id        uuid not null references scenario (id) on delete cascade,
    status             text not null default 'QUEUED',
    time_limit_seconds int  not null,
    hard_score         int,
    soft_score         int,
    created_at         timestamptz not null default now(),
    started_at         timestamptz,
    finished_at        timestamptz,
    error_code         text,
    error_detail       text,
    -- Le rapport d'explicabilité, figé au moment où le job se termine : il décrit
    -- cette solution-là, pas l'état courant du référentiel (§16.6).
    report             jsonb,
    constraint solve_job_status_check
        check (status in ('QUEUED', 'RUNNING', 'DONE', 'FAILED', 'CANCELLED')),
    constraint solve_job_limit_check check (time_limit_seconds > 0)
);

create index solve_job_scenario_idx on solve_job (scenario_id, created_at desc);
create index solve_job_status_idx on solve_job (status) where status in ('QUEUED', 'RUNNING');

-- Le résultat : une liste plate. « L'emploi du temps de la 6e A » et « celui de
-- Jean » sont deux filtres sur cette table, pas deux tables (§6.14).
create table assignment (
    id           uuid primary key,
    solve_job_id uuid not null references solve_job (id) on delete cascade,
    demand_id    uuid not null references demand (id) on delete cascade,
    occurrence   int  not null,
    resource_id  uuid references resource (id) on delete cascade,
    role         text not null,
    day_in_cycle int  not null,
    -- INVARIANT §6.14 : heure réelle, jamais un index de créneau.
    start_time   time not null,
    end_time     time not null,
    pinned       boolean not null default false,
    constraint assignment_range_check check (start_time < end_time)
);

create index assignment_job_idx on assignment (solve_job_id);
create index assignment_resource_idx on assignment (solve_job_id, resource_id);
create index assignment_demand_idx on assignment (solve_job_id, demand_id, occurrence);
