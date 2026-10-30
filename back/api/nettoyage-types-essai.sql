-- A executer UNE FOIS, a la main, sur la base de developpement.
-- Retire de la liste eleveur les types d'essai laisses ACTIFS par les
-- executions de test-back-complet.ps1 anterieures au 6 octobre.
-- Suppression logique (actif = 0) : rien n'est efface, les lots d'essai
-- qui les emploient restent lisibles.
USE moncheptel_dev;
UPDATE type_elevage SET actif = 0 WHERE libelle LIKE '%d essai%' AND id > 0;
SELECT filiere, libelle, actif FROM type_elevage ORDER BY filiere, libelle;
