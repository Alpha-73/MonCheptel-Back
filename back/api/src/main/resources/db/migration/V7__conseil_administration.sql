-- =====================================================================
-- V7 : conseils publies directement par l'administration
--
-- Ecran « Admin - Conseil creer » : « Un conseil créé ici est publié
-- directement, sans passer par la modération. » Son auteur n'est pas un
-- veterinaire : auteur_id devient facultatif. La cle etrangere vers
-- veterinaire est conservee pour tous les autres conseils.
-- =====================================================================

ALTER TABLE conseil MODIFY auteur_id BIGINT NULL;
