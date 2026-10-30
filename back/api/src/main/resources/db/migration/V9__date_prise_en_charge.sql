-- =====================================================================
-- V9 : date de prise en charge d'un signalement par le veterinaire
--
-- Le « délai moyen de prise en charge » des statistiques se mesurait
-- jusqu'a l'orientation, qui est automatique : il valait toujours 0 min.
-- On date desormais le moment ou le veterinaire prend le cas en main.
-- Les cas deja traites recoivent leur date de traitement, la seule trace
-- d'une prise en charge qu'ils portent.
-- =====================================================================

ALTER TABLE signalement_maladie ADD COLUMN date_prise_en_charge DATETIME(6) NULL AFTER date_orientation;

UPDATE signalement_maladie SET date_prise_en_charge = date_traitement
 WHERE statut = 'TRAITE' AND date_traitement IS NOT NULL;
