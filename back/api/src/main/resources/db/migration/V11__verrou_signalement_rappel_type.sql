-- =====================================================================
-- V11 : verrou optimiste des signalements, type d'acte des rappels libres
--
-- signalement_maladie.version : verrou optimiste (@Version). Une cloture
-- et une reattribution simultanees ne s'ecrasent plus : la seconde
-- ecriture echoue et l'API rend 409 « Ce cas vient d'etre modifie ».
--
-- rappel_sanitaire.type_evenement : l'acte que le rappel attend. Un
-- rappel « dans N jours » cree depuis un soin n'a pas de ligne de
-- calendrier ; sans ce type, aucun acte ne pouvait le solder. Les rappels
-- libres existants sont rattrapes d'apres leur libelle (« Rappel
-- vaccination », etc.), ceux du protocole d'apres leur calendrier.
-- =====================================================================

ALTER TABLE signalement_maladie ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE rappel_sanitaire
    ADD COLUMN type_evenement ENUM('CONSULTATION','MALADIE','TRAITEMENT','VACCINATION') NULL
    AFTER libelle;

UPDATE rappel_sanitaire r
   JOIN calendrier_sanitaire c ON c.id = r.calendrier_id
   SET r.type_evenement = c.type_evenement
 WHERE r.calendrier_id IS NOT NULL;

UPDATE rappel_sanitaire SET type_evenement = 'VACCINATION'
 WHERE calendrier_id IS NULL AND LOWER(libelle) = 'rappel vaccination';
UPDATE rappel_sanitaire SET type_evenement = 'TRAITEMENT'
 WHERE calendrier_id IS NULL AND LOWER(libelle) = 'rappel traitement';
UPDATE rappel_sanitaire SET type_evenement = 'MALADIE'
 WHERE calendrier_id IS NULL AND LOWER(libelle) = 'rappel maladie';
UPDATE rappel_sanitaire SET type_evenement = 'CONSULTATION'
 WHERE calendrier_id IS NULL AND LOWER(libelle) = 'rappel consultation';

-- Textes deja enregistres sans accents (audit eleveur F18, veterinaire B15).
UPDATE operation_financiere SET note = REPLACE(note, ' 1 tete(s)', ' 1 tête')
 WHERE note LIKE '% 1 tete(s)';
UPDATE operation_financiere SET note = REPLACE(note, ' tete(s)', ' têtes')
 WHERE note LIKE '% tete(s)';
UPDATE notification SET corps = REPLACE(corps, ' a repondu a votre signalement', ' a répondu à votre signalement')
 WHERE corps LIKE '% a repondu a votre signalement%';
