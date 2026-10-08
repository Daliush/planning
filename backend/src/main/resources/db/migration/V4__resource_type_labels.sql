-- ============================================================================
--  Libellés de types utilisables dans une phrase.
--
--  L'interface compose des phrases du genre « Les enseignants doivent assurer au
--  moins 10 h » : le libellé du type s'y retrouve au pluriel. « Groupe d'élèves »
--  produisait « Les groupe d'élèvess ». Un nom simple s'accorde proprement.
-- ============================================================================

update planning.resource_type set label = 'Groupe' where code = 'groupe';
