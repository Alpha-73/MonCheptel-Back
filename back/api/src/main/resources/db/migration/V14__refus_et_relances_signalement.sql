-- =====================================================================
-- V14 : le veterinaire accepte ou refuse un cas ; relances sans reponse
-- (audit des regles metier du 29 octobre)
-- =====================================================================
--
-- Avant : un cas confie (ORIENTE) ne pouvait qu'etre accepte. Pour le
-- rendre, le veterinaire devait se declarer indisponible, et tous ses cas
-- en attente repartaient d'un coup. Sans reponse, le cas restait confie
-- indefiniment, meme grave.
--
-- 1. signalement_refus : l'historique des refus. Un veterinaire qui a
--    refuse un cas n'est plus propose pour ce cas, et l'administration voit
--    qui l'a refuse et pourquoi. Le nom est copie : la trace survit a la
--    suppression du compte (V8), qui remet veterinaire_id a NULL.
--
-- 2. Trois dates sur signalement_maladie pour les relances :
--    - date_relance_veterinaire : rappel envoye au veterinaire qui n'a pas
--      repondu (2 h pour un cas urgent, 12 h sinon) ;
--    - date_relance_eleveur : l'eleveur a ete invite a changer de
--      veterinaire (double du delai), ou a en choisir un pour un cas urgent
--      reste sans veterinaire ;
--    - date_remise_en_attente : le cas est revenu « sans veterinaire »
--      (refus, depart, changement de zone) ; le delai repart de la.
-- =====================================================================

CREATE TABLE signalement_refus (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    signalement_id    BIGINT        NOT NULL,
    veterinaire_id    BIGINT        NULL,
    veterinaire_nom   VARCHAR(120)  NOT NULL,
    motif             VARCHAR(500)  NULL,
    date_refus        DATETIME(6)   NOT NULL,
    date_creation     DATETIME(6)   NOT NULL,
    date_modification DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_refus_signalement (signalement_id),
    CONSTRAINT fk_refus_signalement FOREIGN KEY (signalement_id)
        REFERENCES signalement_maladie (id) ON DELETE CASCADE,
    CONSTRAINT fk_refus_veterinaire FOREIGN KEY (veterinaire_id)
        REFERENCES veterinaire (utilisateur_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE signalement_maladie
    ADD COLUMN date_relance_veterinaire DATETIME(6) NULL AFTER date_prise_en_charge,
    ADD COLUMN date_relance_eleveur     DATETIME(6) NULL AFTER date_relance_veterinaire,
    ADD COLUMN date_remise_en_attente   DATETIME(6) NULL AFTER date_relance_eleveur;

-- Les cas deja en base ne declenchent pas une salve de relances au premier
-- passage de la tache : ils sont marques comme deja relances.
UPDATE signalement_maladie
   SET date_relance_veterinaire = NOW(6),
       date_relance_eleveur     = NOW(6)
 WHERE statut IN ('NOUVEAU', 'ORIENTE');

-- 3. Rappels ecartes PAR LA SORTIE de l'unite (audit du 29 octobre,
--    point 7). Annuler la sortie qui avait termine l'unite les rouvre ;
--    ceux que l'eleveur avait ignores lui-meme restent ignores. Les rappels
--    deja ecartes avant V14 ne sont pas distinguables : ils restent ignores.
ALTER TABLE rappel_sanitaire
    ADD COLUMN ecarte_a_la_sortie BIT NOT NULL DEFAULT 0;
