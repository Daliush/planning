-- ============================================================================
--  Libellés lisibles sur les attributs des types de ressource.
--
--  `attr_schema` est le générateur d'interface (§6.3). Sans `title`, l'éditeur de
--  scope afficherait « quota_hebdo_minutes » brut à un utilisateur métier. Le
--  frontend sait humaniser une clé faute de mieux, mais un libellé explicite vaut
--  toujours mieux qu'une heuristique.
--
--  `x-unit` marque les durées, pour que le formulaire propose « 8 h 00 » plutôt
--  qu'un entier nu — même convention que les paramètres de plugin.
-- ============================================================================

update planning.resource_type
set attr_schema = jsonb_set(attr_schema, '{properties}', jsonb_build_object(
    'code',                jsonb_build_object('type', 'string', 'title', 'Code'),
    'matieres',            jsonb_build_object('type', 'array', 'title', 'Matières',
                                              'items', jsonb_build_object('type', 'string')),
    'niveaux',             jsonb_build_object('type', 'array', 'title', 'Niveaux',
                                              'items', jsonb_build_object('type', 'string')),
    'statut',              jsonb_build_object('title', 'Statut',
                                              'enum', jsonb_build_array('titulaire', 'vacataire')),
    'quota_hebdo_minutes', jsonb_build_object('type', 'integer', 'title', 'Quota hebdomadaire',
                                              'minimum', 0, 'x-unit', 'minutes')))
where code = 'enseignant';

update planning.resource_type
set attr_schema = jsonb_set(attr_schema, '{properties}', jsonb_build_object(
    'code',     jsonb_build_object('type', 'string', 'title', 'Code'),
    'capacite', jsonb_build_object('type', 'integer', 'title', 'Capacité', 'minimum', 1),
    'usages',   jsonb_build_object('type', 'array', 'title', 'Usages',
                                   'items', jsonb_build_object('type', 'string')),
    'batiment', jsonb_build_object('type', 'string', 'title', 'Bâtiment')))
where code = 'salle';

update planning.resource_type
set attr_schema = jsonb_set(attr_schema, '{properties}', jsonb_build_object(
    'code',     jsonb_build_object('type', 'string', 'title', 'Code'),
    'effectif', jsonb_build_object('type', 'integer', 'title', 'Effectif', 'minimum', 1),
    'niveau',   jsonb_build_object('type', 'string', 'title', 'Niveau')))
where code = 'groupe';
