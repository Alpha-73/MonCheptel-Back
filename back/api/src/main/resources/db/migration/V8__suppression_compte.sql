-- =====================================================================
-- V8 : suppression d'un compte eleveur ou veterinaire par l'administration
--
-- Quand un veterinaire est supprime, ce qu'il a saisi chez les eleveurs
-- (actes au carnet sanitaire, mouvements) reste chez eux, sans auteur.
-- auteur_id devient donc facultatif. Le detail de la suppression est dans
-- SuppressionCompteService.
-- =====================================================================

ALTER TABLE evenement_sanitaire MODIFY auteur_id BIGINT NULL;
ALTER TABLE mouvement_cheptel MODIFY auteur_id BIGINT NULL;
