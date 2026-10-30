-- =====================================================================
-- V12 : accents des symptomes, des maladies et des zones du referentiel
-- =====================================================================
--
-- Suite de V6. V3 a saisi ces libelles sans accents (« Diarrhee
-- verdatre », « Fievre aphteuse », « Segou ») ; ils s'affichent tels quels
-- a l'eleveur (liste des symptomes du signalement) et au veterinaire.
--
-- Les constantes et les identifiants ne changent pas : seul le texte
-- affiche est corrige. Chaque UPDATE vise l'ancienne valeur EXACTE : sur une
-- base ou l'administration a deja renomme une ligne, il ne touche a rien.

SET NAMES utf8mb4;

UPDATE symptome SET libelle = 'Diarrhée verdâtre'  WHERE libelle = 'Diarrhee verdatre';
UPDATE symptome SET libelle = 'Diarrhée avec sang' WHERE libelle = 'Diarrhee avec sang';
UPDATE symptome SET libelle = 'Fièvre'             WHERE libelle = 'Fievre';
UPDATE symptome SET libelle = 'Perte d''appétit'   WHERE libelle = 'Perte d''appetit';
UPDATE symptome SET libelle = 'Vésicules buccales' WHERE libelle = 'Vesicules buccales';
UPDATE symptome SET libelle = 'Diarrhée'           WHERE libelle = 'Diarrhee';
UPDATE symptome SET libelle = 'Déshydratation'     WHERE libelle = 'Deshydratation';
UPDATE symptome SET libelle = 'Écoulement nasal'   WHERE libelle = 'Ecoulement nasal';

UPDATE maladie SET nom = 'Fièvre aphteuse'      WHERE nom = 'Fievre aphteuse';
UPDATE maladie SET nom = 'Diarrhée néonatale'   WHERE nom = 'Diarrhee neonatale';

UPDATE zone SET nom = 'Ségou', region = 'Ségou' WHERE nom = 'Segou' AND region = 'Segou';
