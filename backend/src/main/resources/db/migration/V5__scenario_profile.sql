-- ============================================================================
--  La verticale métier d'un scénario.
--
--  Le tenant porte déjà un profil, mais il est NOT NULL et décrit l'organisation.
--  Ce qu'il manquait est un réglage par planning, et surtout la possibilité de
--  n'en choisir aucun : une école qui veut poser un attribut d'entreprise sur
--  une ressource doit pouvoir voir tout le vocabulaire. NULL est donc une valeur
--  utile — « ne rien filtrer » — et non une donnée manquante.
--
--  INVARIANT : aucun plugin, aucun critère et aucune contrainte ne lit cette
--  colonne. Elle ne choisit que les champs proposés par l'interface. Un profil
--  qui influencerait la résolution serait une valeur métier en dur dans le
--  noyau, ce que le §2.6 interdit.
--
--  Les scénarios existants héritent du profil de leur tenant : c'est ce qu'ils
--  décrivaient déjà de fait.
-- ============================================================================

alter table planning.scenario
    add column profile text;

alter table planning.scenario
    add constraint scenario_profile_check
        check (profile is null or profile in ('SCHOOL', 'COMPANY', 'RESTAURANT'));

update planning.scenario s
set profile = t.profile
from planning.tenant t
where t.id = s.tenant_id;
