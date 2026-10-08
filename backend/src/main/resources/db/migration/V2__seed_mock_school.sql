-- ============================================================================
--  Jeu de données mocké — profil SCHOOL
--
--  Ressources fictives en attendant le connecteur vers Progression. Tout est
--  idempotent : les inserts sont conditionnés à l'absence du tenant de démo.
-- ============================================================================

do $$
declare
    v_tenant   uuid := '10000000-0000-0000-0000-000000000001';
    v_t_ens    uuid := '20000000-0000-0000-0000-000000000001';
    v_t_salle  uuid := '20000000-0000-0000-0000-000000000002';
    v_t_groupe uuid := '20000000-0000-0000-0000-000000000003';
    v_model    uuid := '30000000-0000-0000-0000-000000000001';
    v_scenario uuid := '40000000-0000-0000-0000-000000000001';
begin

if exists (select 1 from tenant where id = v_tenant) then
    return;
end if;

-- ─── Tenant et types de ressources ──────────────────────────────────────────

insert into tenant (id, code, label, profile)
values (v_tenant, 'college-demo', 'Collège de démonstration', 'SCHOOL');

insert into resource_type (id, tenant_id, code, label, attr_schema) values
 (v_t_ens, v_tenant, 'enseignant', 'Enseignant', jsonb_build_object(
    'type', 'object',
    'required', jsonb_build_array('code', 'matieres', 'niveaux', 'statut'),
    'properties', jsonb_build_object(
      'code',                jsonb_build_object('type', 'string'),
      'matieres',            jsonb_build_object('type', 'array', 'items', jsonb_build_object('type', 'string')),
      'niveaux',             jsonb_build_object('type', 'array', 'items', jsonb_build_object('type', 'string')),
      'statut',              jsonb_build_object('enum', jsonb_build_array('titulaire', 'vacataire')),
      'quota_hebdo_minutes', jsonb_build_object('type', 'integer', 'minimum', 0)))),
 (v_t_salle, v_tenant, 'salle', 'Salle', jsonb_build_object(
    'type', 'object',
    'required', jsonb_build_array('code', 'capacite', 'usages'),
    'properties', jsonb_build_object(
      'code',      jsonb_build_object('type', 'string'),
      'capacite',  jsonb_build_object('type', 'integer', 'minimum', 1),
      'usages',    jsonb_build_object('type', 'array', 'items', jsonb_build_object('type', 'string')),
      'batiment',  jsonb_build_object('type', 'string')))),
 (v_t_groupe, v_tenant, 'groupe', 'Groupe d''élèves', jsonb_build_object(
    'type', 'object',
    'required', jsonb_build_array('code', 'effectif', 'niveau'),
    'properties', jsonb_build_object(
      'code',     jsonb_build_object('type', 'string'),
      'effectif', jsonb_build_object('type', 'integer', 'minimum', 1),
      'niveau',   jsonb_build_object('type', 'string'))));

-- ─── Modèle temporel ────────────────────────────────────────────────────────
-- max_gap_minutes = 20 : une séance de 2 h peut enjamber la récréation de 15 min,
-- jamais la pause déjeuner de 75 min (§10.3).

insert into time_model (id, tenant_id, label, horizon_kind, cycle_days, max_gap_minutes)
values (v_model, v_tenant, 'Semaine standard', 'CYCLIC', 7, 20);

-- Grille irrégulière volontaire : deux récréations et une pause déjeuner.
insert into slot_template (id, time_model_id, day_in_cycle, ordinal, start_time, end_time)
select gen_random_uuid(), v_model, d.day, s.ordinal, s.debut, s.fin
from generate_series(0, 4) as d(day)
cross join (values
    (0, time '08:00', time '09:00'),
    (1, time '09:00', time '10:00'),
    (2, time '10:15', time '11:15'),
    (3, time '11:15', time '12:15'),
    (4, time '13:30', time '14:30'),
    (5, time '14:30', time '15:30'),
    (6, time '15:45', time '16:45'),
    (7, time '16:45', time '17:45')
) as s(ordinal, debut, fin);

-- ─── Scénario ───────────────────────────────────────────────────────────────

insert into scenario (id, tenant_id, label, time_model_id, status)
values (v_scenario, v_tenant, 'Rentrée 2026', v_model, 'DRAFT');

-- Deux semaines de vacances pour montrer la projection calendaire.
insert into calendar_week (scenario_id, week_number, worked, cycle_offset)
select v_scenario, w, w not in (44, 45, 52, 1, 8, 9, 16, 17), 0
from generate_series(36, 53) as w
union all
select v_scenario, w, w not in (1, 8, 9, 16, 17), 0
from generate_series(1, 27) as w;

-- ─── Enseignants ────────────────────────────────────────────────────────────

insert into resource (id, scenario_id, type_id, label, default_effect, attributes) values
 ('50000000-0000-0000-0000-000000000001', v_scenario, v_t_ens, 'Jean Dupont',    'UNAVAILABLE',
  '{"code":"JDU","matieres":["maths"],"niveaux":["6","5"],"statut":"vacataire","quota_hebdo_minutes":480}'),
 ('50000000-0000-0000-0000-000000000002', v_scenario, v_t_ens, 'Sophie Martin',  'AVAILABLE',
  '{"code":"SMA","matieres":["maths"],"niveaux":["6","5"],"statut":"titulaire","quota_hebdo_minutes":1080}'),
 ('50000000-0000-0000-0000-000000000003', v_scenario, v_t_ens, 'Nadia Benali',   'AVAILABLE',
  '{"code":"NBE","matieres":["maths"],"niveaux":["6","5"],"statut":"titulaire","quota_hebdo_minutes":1080}'),
 ('50000000-0000-0000-0000-000000000004', v_scenario, v_t_ens, 'Claire Petit',   'AVAILABLE',
  '{"code":"CPE","matieres":["francais"],"niveaux":["6","5"],"statut":"titulaire","quota_hebdo_minutes":1080}'),
 ('50000000-0000-0000-0000-000000000005', v_scenario, v_t_ens, 'Marc Durand',    'AVAILABLE',
  '{"code":"MDU","matieres":["francais"],"niveaux":["6","5"],"statut":"titulaire","quota_hebdo_minutes":1080}'),
 ('50000000-0000-0000-0000-000000000006', v_scenario, v_t_ens, 'Alice Moreau',   'AVAILABLE',
  '{"code":"AMO","matieres":["histoire-geo"],"niveaux":["6","5"],"statut":"titulaire","quota_hebdo_minutes":1080}'),
 ('50000000-0000-0000-0000-000000000007', v_scenario, v_t_ens, 'Paul Lambert',   'AVAILABLE',
  '{"code":"PLA","matieres":["histoire-geo"],"niveaux":["6","5"],"statut":"titulaire","quota_hebdo_minutes":1080}'),
 ('50000000-0000-0000-0000-000000000008', v_scenario, v_t_ens, 'Emma Rousseau',  'AVAILABLE',
  '{"code":"ERO","matieres":["anglais"],"niveaux":["6","5"],"statut":"titulaire","quota_hebdo_minutes":1080}'),
 ('50000000-0000-0000-0000-000000000009', v_scenario, v_t_ens, 'Hugo Girard',    'AVAILABLE',
  '{"code":"HGI","matieres":["anglais"],"niveaux":["6","5"],"statut":"titulaire","quota_hebdo_minutes":1080}'),
 ('50000000-0000-0000-0000-00000000000a', v_scenario, v_t_ens, 'Léa Fontaine',   'AVAILABLE',
  '{"code":"LFO","matieres":["physique"],"niveaux":["6","5"],"statut":"titulaire","quota_hebdo_minutes":1080}'),
 ('50000000-0000-0000-0000-00000000000b', v_scenario, v_t_ens, 'Yann Mercier',   'AVAILABLE',
  '{"code":"YME","matieres":["physique"],"niveaux":["6","5"],"statut":"titulaire","quota_hebdo_minutes":1080}');

-- ─── Salles ─────────────────────────────────────────────────────────────────

insert into resource (id, scenario_id, type_id, label, default_effect, attributes) values
 ('60000000-0000-0000-0000-000000000001', v_scenario, v_t_salle, 'Salle B12', 'AVAILABLE',
  '{"code":"B12","capacite":30,"usages":["cours"],"batiment":"A"}'),
 ('60000000-0000-0000-0000-000000000002', v_scenario, v_t_salle, 'Salle B13', 'AVAILABLE',
  '{"code":"B13","capacite":30,"usages":["cours"],"batiment":"A"}'),
 ('60000000-0000-0000-0000-000000000003', v_scenario, v_t_salle, 'Salle B14', 'AVAILABLE',
  '{"code":"B14","capacite":28,"usages":["cours"],"batiment":"A"}'),
 ('60000000-0000-0000-0000-000000000004', v_scenario, v_t_salle, 'Salle C01', 'AVAILABLE',
  '{"code":"C01","capacite":32,"usages":["cours"],"batiment":"B"}'),
 ('60000000-0000-0000-0000-000000000005', v_scenario, v_t_salle, 'Labo 1',    'AVAILABLE',
  '{"code":"LAB1","capacite":30,"usages":["cours","tp"],"batiment":"B"}'),
 ('60000000-0000-0000-0000-000000000006', v_scenario, v_t_salle, 'Labo 2',    'AVAILABLE',
  '{"code":"LAB2","capacite":30,"usages":["cours","tp"],"batiment":"B"}');

-- ─── Groupes ────────────────────────────────────────────────────────────────

insert into resource (id, scenario_id, type_id, label, default_effect, attributes) values
 ('70000000-0000-0000-0000-000000000001', v_scenario, v_t_groupe, '6e A', 'AVAILABLE', '{"code":"6A","effectif":28,"niveau":"6"}'),
 ('70000000-0000-0000-0000-000000000002', v_scenario, v_t_groupe, '6e B', 'AVAILABLE', '{"code":"6B","effectif":26,"niveau":"6"}'),
 ('70000000-0000-0000-0000-000000000003', v_scenario, v_t_groupe, '5e A', 'AVAILABLE', '{"code":"5A","effectif":30,"niveau":"5"}'),
 ('70000000-0000-0000-0000-000000000004', v_scenario, v_t_groupe, '5e B', 'AVAILABLE', '{"code":"5B","effectif":24,"niveau":"5"}');

-- ─── Disponibilités ─────────────────────────────────────────────────────────
-- Deux archétypes opposés (§6.4) : Jean est « indisponible sauf », la salle B12
-- est « disponible sauf ».

-- Jean, vacataire : uniquement mardi et mercredi (exemple de référence du SDD).
insert into resource_availability (id, resource_id, effect, day_in_cycle, start_time, end_time, priority) values
 (gen_random_uuid(), '50000000-0000-0000-0000-000000000001', 'AVAILABLE', 1, null, null, 0),
 (gen_random_uuid(), '50000000-0000-0000-0000-000000000001', 'AVAILABLE', 2, null, null, 0);

-- Sophie préfère ne pas travailler le vendredi après-midi : permis mais pénalisé.
insert into resource_availability (id, resource_id, effect, day_in_cycle, start_time, end_time, priority)
values (gen_random_uuid(), '50000000-0000-0000-0000-000000000002', 'DISCOURAGED', 4, time '13:00', null, 0);

-- La B12 ferme le vendredi après-midi.
insert into resource_availability (id, resource_id, effect, day_in_cycle, start_time, end_time, priority)
values (gen_random_uuid(), '60000000-0000-0000-0000-000000000001', 'UNAVAILABLE', 4, time '13:00', null, 0);

-- Pas de cours pour les élèves le mercredi après-midi.
insert into resource_availability (id, resource_id, effect, day_in_cycle, start_time, end_time, priority)
select gen_random_uuid(), r.id, 'UNAVAILABLE', 2, time '13:00', null, 0
from resource r
where r.scenario_id = v_scenario and r.type_id = v_t_groupe;

-- ─── Demandes ───────────────────────────────────────────────────────────────

insert into demand (id, scenario_id, label, duration_minutes, occurrences, attributes, placement)
select gen_random_uuid(),
       v_scenario,
       m.libelle || ' — ' || g.libelle,
       m.duree,
       m.occurrences,
       jsonb_build_object(
           'matiere',     m.matiere,
           'niveau',      g.niveau,
           'groupe',      g.code,
           'effectif',    g.effectif,
           'usage_salle', m.usage_salle),
       '{"strategy":"free"}'::jsonb
from (values
        ('6A', '6e A', '6', 28),
        ('6B', '6e B', '6', 26),
        ('5A', '5e A', '5', 30),
        ('5B', '5e B', '5', 24)
     ) as g(code, libelle, niveau, effectif)
cross join (values
        ('maths',        'Mathématiques',       60,  4, 'cours'),
        ('francais',     'Français',            60,  4, 'cours'),
        ('histoire-geo', 'Histoire-Géographie', 60,  3, 'cours'),
        ('anglais',      'Anglais',             60,  3, 'cours'),
        ('physique',     'Physique-Chimie',     60,  1, 'cours'),
        ('physique',     'TP Physique-Chimie',  120, 1, 'tp')
     ) as m(matiere, libelle, duree, occurrences, usage_salle);

-- ─── Postes à pourvoir ──────────────────────────────────────────────────────
-- Les qualifications et la capacité sont des critères d'éligibilité, jamais des
-- contraintes : c'est structurellement impossible (§2.4, §12.2).

insert into requirement (id, demand_id, role, type_id, quantity, criteria)
select gen_random_uuid(), d.id, 'enseignant', v_t_ens, 1,
       jsonb_build_object('and', jsonb_build_array(
           jsonb_build_object('contains', jsonb_build_object('path', '/matieres', 'value', d.attributes ->> 'matiere')),
           jsonb_build_object('contains', jsonb_build_object('path', '/niveaux',  'value', d.attributes ->> 'niveau'))))
from demand d where d.scenario_id = v_scenario;

insert into requirement (id, demand_id, role, type_id, quantity, criteria)
select gen_random_uuid(), d.id, 'salle', v_t_salle, 1,
       jsonb_build_object('and', jsonb_build_array(
           jsonb_build_object('gte',      jsonb_build_object('path', '/capacite', 'value', (d.attributes ->> 'effectif')::int)),
           jsonb_build_object('contains', jsonb_build_object('path', '/usages',   'value', d.attributes ->> 'usage_salle'))))
from demand d where d.scenario_id = v_scenario;

insert into requirement (id, demand_id, role, type_id, quantity, criteria)
select gen_random_uuid(), d.id, 'groupe', v_t_groupe, 1,
       jsonb_build_object('eq', jsonb_build_object('path', '/code', 'value', d.attributes ->> 'groupe'))
from demand d where d.scenario_id = v_scenario;

-- ─── Poids ① — arbitrage de politique entre contraintes ─────────────────────

insert into constraint_weight (scenario_id, constraint_id, level, weight) values
 (v_scenario, 'resource.conflict',         'HARD',   1),
 (v_scenario, 'resource.availability',     'HARD',   1),
 (v_scenario, 'resource.consistency',      'HARD',   1),
 (v_scenario, 'booking.unassigned',        'HARD',   1),
 (v_scenario, 'load.max',                  'HARD',   1),
 (v_scenario, 'time.weekly_regularity',    'HARD',   1),
 (v_scenario, 'demand.spread',             'SOFT', 200),
 (v_scenario, 'gaps.avoid',                'SOFT',  50),
 (v_scenario, 'resource.discouraged_slot', 'SOFT',  30),
 (v_scenario, 'time.preferred_window',     'SOFT',  20),
 (v_scenario, 'load.min',                  'SOFT',  20),
 (v_scenario, 'load.balance',              'SOFT',  10),
 (v_scenario, 'day.span_max',              'SOFT',  10),
 (v_scenario, 'sequence.consecutive_max',  'SOFT',  10);

-- ─── Règles configurées (poids ② dans la colonne weight) ────────────────────

insert into constraint_config (id, scenario_id, type, scope, params, weight) values
 -- Quotas journaliers. Jean est enseignant ET vacataire : les deux règles
 -- s'appliquent simultanément, c'est une intersection et non une hiérarchie (§6.11).
 (gen_random_uuid(), v_scenario, 'load.max',
  '{"eq":{"path":"/type","value":"enseignant"}}',
  '{"maxMinutes":360,"period":"DAY"}', 1),
 (gen_random_uuid(), v_scenario, 'load.max',
  '{"eq":{"path":"/statut","value":"vacataire"}}',
  '{"maxMinutes":240,"period":"DAY"}', 3),
 (gen_random_uuid(), v_scenario, 'load.max',
  '{"eq":{"path":"/type","value":"groupe"}}',
  '{"maxMinutes":420,"period":"DAY"}', 1),

 -- Le même enseignant et la même salle sur toutes les séances d'un cours.
 (gen_random_uuid(), v_scenario, 'resource.consistency',
  '{"eq":{"path":"/type","value":"enseignant"}}', '{}', 1),

 -- Les trous des élèves coûtent quatre fois ceux des professeurs (§13.4).
 -- La tolérance absorbe la récréation de 15 min.
 (gen_random_uuid(), v_scenario, 'gaps.avoid',
  '{"eq":{"path":"/type","value":"groupe"}}',
  '{"toleranceMinutes":20}', 4),
 (gen_random_uuid(), v_scenario, 'gaps.avoid',
  '{"eq":{"path":"/type","value":"enseignant"}}',
  '{"toleranceMinutes":20}', 1),

 -- Étaler les séances d'un même cours sur des jours différents.
 (gen_random_uuid(), v_scenario, 'demand.spread', '{}', '{"maxPerDay":1}', 1),

 -- Équilibrer la charge entre professeurs d'une même matière.
 (gen_random_uuid(), v_scenario, 'load.balance',
  '{"contains":{"path":"/matieres","value":"maths"}}', '{}', 1),
 (gen_random_uuid(), v_scenario, 'load.balance',
  '{"contains":{"path":"/matieres","value":"francais"}}', '{}', 1),

 -- Un titulaire doit avoir au moins 6 h dans le cycle.
 (gen_random_uuid(), v_scenario, 'load.min',
  '{"eq":{"path":"/statut","value":"titulaire"}}',
  '{"minMinutes":360,"period":"CYCLE"}', 1),

 -- Amplitude et enchaînements.
 (gen_random_uuid(), v_scenario, 'day.span_max',
  '{"eq":{"path":"/type","value":"enseignant"}}',
  '{"maxSpanMinutes":480}', 1),
 (gen_random_uuid(), v_scenario, 'sequence.consecutive_max',
  '{"eq":{"path":"/type","value":"enseignant"}}',
  '{"maxConsecutiveMinutes":240,"toleranceMinutes":20}', 1),

 -- Préférence horaire portée par la demande, pas par la ressource.
 (gen_random_uuid(), v_scenario, 'time.preferred_window',
  '{"eq":{"path":"/matiere","value":"maths"}}',
  '{"from":"08:00","to":"12:15"}', 2);

end $$;
