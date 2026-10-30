-- =====================================================================
-- V13 : auteur d'une maladie ajoutee par un veterinaire (audit du 10 octobre)
-- =====================================================================
--
-- Un veterinaire peut ajouter une maladie au catalogue ; elle sert tout de
-- suite au diagnostic. L'administration doit savoir QUI l'a ajoutee pour
-- pouvoir la verifier ou la retirer.
--
-- Texte et non cle etrangere : la trace survit a la suppression du compte
-- (V8). Nul pour le catalogue de reference et les ajouts de
-- l'administration.

ALTER TABLE maladie ADD COLUMN ajoutee_par VARCHAR(120) NULL;
