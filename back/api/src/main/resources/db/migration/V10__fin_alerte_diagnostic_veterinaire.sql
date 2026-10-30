-- =====================================================================
-- V10 : date de fin d'une alerte, diagnostic retenu par le veterinaire
--
-- alerte.date_fin : facultative. Une alerte qui en porte une se clot
-- d'elle-meme le lendemain de cette date (tache planifiee), avec le meme
-- avis de fin aux eleveurs qu'une cloture manuelle.
--
-- signalement_maladie.maladie_confirmee_id : la maladie que le
-- veterinaire retient en cloturant le cas. Les statistiques comptent
-- celle-ci quand elle existe, la proposition du moteur sinon.
-- =====================================================================

ALTER TABLE alerte ADD COLUMN date_fin DATE NULL AFTER date_cloture;

ALTER TABLE signalement_maladie ADD COLUMN maladie_confirmee_id BIGINT NULL AFTER probabilite_alternative;
ALTER TABLE signalement_maladie ADD CONSTRAINT fk_signalement_maladie_confirmee
    FOREIGN KEY (maladie_confirmee_id) REFERENCES maladie (id);
