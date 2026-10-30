-- =====================================================================
-- V6 : accents du referentiel des types d'elevage
-- =====================================================================
--
-- V3 a saisi les libelles sans accents (« Chevre sahelienne »). Ils
-- s'affichent tels quels a l'eleveur : liste « Type d'élevage » de l'ecran
-- d'ajout, carnet, simulateur, page Logistique.
--
-- On CORRIGE ICI au lieu de modifier V3 : Flyway a deja applique V3 sur
-- les bases existantes et refuserait de demarrer si son contenu changeait
-- (somme de controle).
--
-- Chaque UPDATE vise l'ancienne valeur exacte : rejoue sur une base ou un
-- administrateur a deja renomme le type, il ne touche a rien.
--
-- Les libelles des symptomes et des maladies (V3) restent a corriger ;
-- ce n'est pas l'objet de cette migration.

UPDATE type_elevage
   SET libelle = 'Chèvre sahélienne'
 WHERE filiere = 'CAPRINS' AND libelle = 'Chevre sahelienne';

UPDATE type_elevage
   SET libelle = 'Bélier d''embouche'
 WHERE filiere = 'OVINS' AND libelle = 'Belier d''embouche';

UPDATE type_elevage
   SET vaccins_obligatoires = 'PPR annuel, déparasitage'
 WHERE vaccins_obligatoires = 'PPR annuel, deparasitage';

UPDATE type_elevage
   SET vaccins_obligatoires = 'Fièvre aphteuse, charbon'
 WHERE vaccins_obligatoires = 'Fievre aphteuse, charbon';

-- Postes de cout : seuls ceux qui portaient un mot accentue.
UPDATE poste_cout SET libelle = 'Litière, mise en place'  WHERE libelle = 'Litiere, mise en place';
UPDATE poste_cout SET libelle = 'Eau et énergie'          WHERE libelle = 'Eau et energie';
UPDATE poste_cout SET libelle = 'Vaccins et déparasitage' WHERE libelle = 'Vaccins et deparasitage';
UPDATE poste_cout SET libelle = 'Abri et clôture'         WHERE libelle = 'Abri et cloture';
UPDATE poste_cout SET libelle = 'Étable et clôture'       WHERE libelle = 'Etable et cloture';
