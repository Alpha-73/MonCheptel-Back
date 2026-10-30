-- =====================================================================
-- Donnees de reference
--
-- Separees du schema : V1 et V2 sont la structure, V3 est le contenu
-- initial. Un correctif de donnees se releve alors sans toucher au schema.
--
-- ---------------------------------------------------------------------
-- PROVENANCE DES CHIFFRES — a lire avant de s'en servir
-- ---------------------------------------------------------------------
--
-- TRACABLE : tout le referentiel VOLAILLE vient des ecrans
--   « Admin - Referentiel fiche » et « Investir - Detail volaille » de la
--   maquette. Les cinq totaux qu'affichent ces ecrans se recalculent au
--   franc pres a partir des lignes ci-dessous : installation 26 000,
--   cycle 47 593, depart de zero 73 593, aliment 59,4 kg, reste 2 407.
--
-- NON VALIDE : les POIDS de la table maladie_symptome. La maquette donne
--   les maladies et leurs symptomes, jamais leur ponderation. Ces valeurs
--   sont vraisemblables, elles ne sont pas etablies. ELLES DOIVENT ETRE
--   RELUES PAR UN VETERINAIRE avant toute mise en service reelle.
--   Elles ne changent presque rien sur des symptomes specifiques, mais
--   elles sont la SEULE chose qui departage deux maladies sur des
--   symptomes communs (abattement, perte d'appetit).
--
-- NON VALIDE : le detail des couts BOVINS, OVINS et CAPRINS. Seuls les
--   TOTAUX par cycle viennent de la maquette (« Investir - Options » :
--   48 000, 80 000, 260 000). Leur decomposition en postes est une
--   repartition plausible, pas un releve de prix.
--
-- Les lignes non validees sont signalees par « -- [A VALIDER] ».
-- =====================================================================

SET NAMES utf8mb4;

-- ── zones ────────────────────────────────────────────────────────────
-- Communes et cercles du Mali. Koutiala est celle de la maquette.

INSERT INTO zone (nom, region, date_creation) VALUES
  ('Bamako',    'District de Bamako', NOW(6)),
  ('Koutiala',  'Sikasso',            NOW(6)),
  ('Sikasso',   'Sikasso',            NOW(6)),
  ('Segou',     'Segou',              NOW(6)),
  ('Mopti',     'Mopti',              NOW(6)),
  ('Kayes',     'Kayes',              NOW(6)),
  ('Koulikoro', 'Koulikoro',          NOW(6)),
  ('Gao',       'Gao',                NOW(6)),
  ('Tombouctou','Tombouctou',         NOW(6)),
  ('Kidal',     'Kidal',              NOW(6));

-- ── symptomes ────────────────────────────────────────────────────────
-- Les dix premiers sont exactement ceux de l'ecran « Signalement -
-- Symptomes », dans SON ordre : les plus frequents d'abord, pour que
-- l'eleveur n'ait pas a defiler. Les quatre suivants sont exiges par les
-- maladies bovines et ovines du catalogue.

INSERT INTO symptome (libelle, ordre, date_creation) VALUES
  ('Toux',                  1,  NOW(6)),
  ('Diarrhee verdatre',     2,  NOW(6)),
  ('Diarrhee avec sang',    3,  NOW(6)),
  ('Torticolis',            4,  NOW(6)),
  ('Abattement',            5,  NOW(6)),
  ('Chute de ponte',        6,  NOW(6)),
  ('Morts subites',         7,  NOW(6)),
  ('Fievre',                8,  NOW(6)),
  ('Boiterie',              9,  NOW(6)),
  ('Perte d''appetit',      10, NOW(6)),
  ('Vesicules buccales',    11, NOW(6)),
  ('Diarrhee',              12, NOW(6)),
  ('Deshydratation',        13, NOW(6)),
  ('Ecoulement nasal',      14, NOW(6));

-- ── maladies ─────────────────────────────────────────────────────────
-- Les six de l'ecran « Admin - Maladies catalogue », avec leur gravite et
-- leur filiere telles qu'il les affiche.

INSERT INTO maladie (nom, gravite, symptomes_texte, traitement,
                     oriente_vers_veterinaire, active, date_creation) VALUES
  ('Maladie de Newcastle', 'ELEVE',
   'Toux, diarrhee verdatre, torticolis, morts subites',
   'Aucun traitement curatif. La prevention repose sur la vaccination (J7 et J21). '
   'Isoler les sujets malades, suspendre tout mouvement d''animaux, appeler un veterinaire.',
   TRUE, TRUE, NOW(6)),

  ('Coccidiose', 'MOYEN',
   'Diarrhee avec sang, abattement, perte d''appetit',
   'Anticoccidien dans l''eau de boisson, dose selon la notice du produit, pendant 5 jours. '
   'Renouveler l''eau chaque jour. Si les signes persistent apres 3 jours, contacter un veterinaire.',
   FALSE, TRUE, NOW(6)),

  ('Bronchite infectieuse', 'MOYEN',
   'Toux, chute de ponte',
   'Pas de traitement specifique. Rechauffer le batiment, aerer sans courant d''air, '
   'soutenir par des vitamines. Consulter si la chute de ponte depasse une semaine.',
   FALSE, TRUE, NOW(6)),

  ('Fievre aphteuse', 'ELEVE',
   'Vesicules buccales, boiterie, fievre',
   'Maladie a declaration obligatoire. Isoler immediatement, ne deplacer aucun animal, '
   'prevenir le service veterinaire.',
   TRUE, TRUE, NOW(6)),

  ('Diarrhee neonatale', 'MOYEN',
   'Diarrhee, deshydratation, abattement',
   'Rehydratation orale en priorite, plusieurs fois par jour. Maintenir le veau au sec et au chaud. '
   'Consulter si la diarrhee persiste au-dela de 48 heures.',
   FALSE, TRUE, NOW(6)),

  ('Peste des petits ruminants', 'ELEVE',
   'Fievre, ecoulement nasal, diarrhee',
   'Maladie a declaration obligatoire. Isoler le troupeau, prevenir le service veterinaire. '
   'La vaccination est le seul moyen de protection.',
   TRUE, TRUE, NOW(6));

-- Le traitement des maladies marquees `oriente_vers_veterinaire` ne sort
-- JAMAIS vers un eleveur : MaladieMapper.versReponseEleveur le remplace
-- par null. Il est stocke pour l'administration et les veterinaires.

INSERT INTO maladie_filiere (maladie_id, filiere)
SELECT id, 'VOLAILLE' FROM maladie
 WHERE nom IN ('Maladie de Newcastle', 'Coccidiose', 'Bronchite infectieuse');
INSERT INTO maladie_filiere (maladie_id, filiere)
SELECT id, 'BOVINS' FROM maladie
 WHERE nom IN ('Fievre aphteuse', 'Diarrhee neonatale');
INSERT INTO maladie_filiere (maladie_id, filiere)
SELECT id, 'OVINS' FROM maladie
 WHERE nom IN ('Peste des petits ruminants', 'Fievre aphteuse');
INSERT INTO maladie_filiere (maladie_id, filiere)
SELECT id, 'CAPRINS' FROM maladie
 WHERE nom IN ('Peste des petits ruminants', 'Fievre aphteuse');

-- ── ponderation maladie / symptome ───────────────────────────────────
--
-- [A VALIDER] AUCUN de ces poids ne vient de la maquette ni d'une source
-- veterinaire. Ils encodent une intuition : un symptome tres evocateur
-- d'une maladie pese lourd, un symptome banal pese peu.
--
-- Ce qu'ils changent reellement, mesure sur le moteur :
--   - symptomes specifiques (toux + diarrhee verdatre + torticolis) : le
--     classement est le meme avec ou sans eux. Newcastle sort en tete.
--   - symptomes communs (abattement seul, perte d'appetit) : sans les
--     poids, deux maladies sortent a egalite. Les poids sont alors la
--     SEULE chose qui departage.
--
-- Ils ne sont donc pas supprimables, et ils ne sont pas fiables. Un
-- veterinaire doit relire ces 24 lignes.

INSERT INTO maladie_symptome (maladie_id, symptome_id, poids, date_creation)
SELECT m.id, s.id, v.poids, NOW(6)
FROM (
  -- Newcastle : le torticolis est le signe nerveux caracteristique.
  SELECT 'Maladie de Newcastle' AS maladie, 'Torticolis'         AS symptome, 0.95 AS poids
  UNION ALL SELECT 'Maladie de Newcastle', 'Diarrhee verdatre',   0.85
  UNION ALL SELECT 'Maladie de Newcastle', 'Morts subites',       0.80
  UNION ALL SELECT 'Maladie de Newcastle', 'Toux',                0.60
  UNION ALL SELECT 'Maladie de Newcastle', 'Abattement',          0.40
  UNION ALL SELECT 'Maladie de Newcastle', 'Chute de ponte',      0.35

  -- Coccidiose : le sang dans les fientes est le signe qui la distingue.
  UNION ALL SELECT 'Coccidiose',           'Diarrhee avec sang',  0.95
  UNION ALL SELECT 'Coccidiose',           'Abattement',          0.55
  UNION ALL SELECT 'Coccidiose',           'Perte d''appetit',    0.50
  UNION ALL SELECT 'Coccidiose',           'Diarrhee',            0.45

  -- Bronchite infectieuse : respiratoire et ponte, sans signe nerveux.
  UNION ALL SELECT 'Bronchite infectieuse','Toux',                0.90
  UNION ALL SELECT 'Bronchite infectieuse','Chute de ponte',      0.85
  UNION ALL SELECT 'Bronchite infectieuse','Ecoulement nasal',    0.55
  UNION ALL SELECT 'Bronchite infectieuse','Abattement',          0.30

  -- Fievre aphteuse : les vesicules buccales sont pathognomoniques.
  UNION ALL SELECT 'Fievre aphteuse',      'Vesicules buccales',  0.95
  UNION ALL SELECT 'Fievre aphteuse',      'Boiterie',            0.80
  UNION ALL SELECT 'Fievre aphteuse',      'Fievre',              0.60
  UNION ALL SELECT 'Fievre aphteuse',      'Perte d''appetit',    0.40

  -- Diarrhee neonatale du veau.
  UNION ALL SELECT 'Diarrhee neonatale',   'Diarrhee',            0.90
  UNION ALL SELECT 'Diarrhee neonatale',   'Deshydratation',      0.85
  UNION ALL SELECT 'Diarrhee neonatale',   'Abattement',          0.45

  -- Peste des petits ruminants.
  UNION ALL SELECT 'Peste des petits ruminants', 'Ecoulement nasal', 0.85
  UNION ALL SELECT 'Peste des petits ruminants', 'Fievre',           0.75
  UNION ALL SELECT 'Peste des petits ruminants', 'Diarrhee',         0.60
) AS v
JOIN maladie  m ON m.nom     = v.maladie
JOIN symptome s ON s.libelle = v.symptome;

-- ── types d'elevage ──────────────────────────────────────────────────
--
-- VOLAILLE : entierement traçable a « Admin - Referentiel fiche ».
-- Chaque parametre y est affiche, chaque cout unitaire aussi.

INSERT INTO type_elevage (filiere, libelle, duree_cycle_jours, mode_evaluation,
        capacite_reference, espace_par_sujet_m2, temperature_min, temperature_max,
        aliment_par_sujet_kg, eau_par_sujet_jour_l, vaccins_obligatoires,
        taux_mortalite, difficulte, actif, date_creation)
VALUES
  ('VOLAILLE', 'Poulet de chair', 45, 'PAR_PERIODE',
   100, 0.100, 25, 30, 1.800, 0.170, 'Newcastle J7 et J21',
   0.0800, 'ACCESSIBLE', TRUE, NOW(6)),

  -- [A VALIDER] Les trois suivants : seul le TOTAL du cycle vient de la
  -- maquette (« Investir - Options 50 000 »). Les parametres logistiques
  -- sont plausibles, pas releves.
  ('CAPRINS', 'Chevre sahelienne', NULL, 'PAR_ENTREE',
   10, 1.500, NULL, NULL, 1.200, 4.000, 'PPR annuel, deparasitage',
   0.0500, 'ACCESSIBLE', TRUE, NOW(6)),

  ('OVINS', 'Belier d''embouche', 90, 'PAR_PERIODE',
   10, 2.000, NULL, NULL, 2.500, 5.000, 'PPR annuel, deparasitage',
   0.0400, 'MOYEN', TRUE, NOW(6)),

  ('BOVINS', 'Veau d''embouche', 180, 'PAR_PERIODE',
   5, 6.000, NULL, NULL, 6.000, 30.000, 'Fievre aphteuse, charbon',
   0.0300, 'EXIGEANT', TRUE, NOW(6));

-- taux_mortalite est une FRACTION : 0,0800 pour les 8 % affiches.
-- duree_cycle_jours est NULL pour la chevre : mode PAR_ENTREE, suivi
-- individuel, pas de cycle qui se clot.

-- ── postes de cout ───────────────────────────────────────────────────
--
-- Rappel du calcul (PosteCout.calculer) :
--   PAR_SUJET    -> sujets ACHETES x coefficient x montant
--   PAR_CAPACITE -> capacite de reference x coefficient x montant
--   FORFAIT      -> montant
--
-- Le coefficient porte la QUANTITE par unite de base (1,8 kg d'aliment
-- par sujet ; 0,1 m² de batiment par place), le montant porte le PRIX
-- d'une unite (345 F le kilo, 1 800 F le m²). C'est cette decomposition
-- qui permet a l'ecran d'afficher « 59,4 kg x 345 FCFA » plutot qu'un
-- total opaque — et donc a un eleveur de contester le prix du kilo.

INSERT INTO poste_cout (type_elevage_id, libelle, montant, unite, phase,
                        mode_quantite, coefficient, ordre, date_creation)
SELECT t.id, v.libelle, v.montant, v.unite, v.phase, v.mode_quantite,
       v.coefficient, v.ordre, NOW(6)
FROM (
  -- Poulet de chair — INSTALLATION : 100 x 0,1 x 1 800 + 6 000 + 2 000 = 26 000
  SELECT 'Poulet de chair' AS type, 'Logement'                  AS libelle,
         1800 AS montant, 'm2'    AS unite, 'INSTALLATION' AS phase,
         'PAR_CAPACITE'   AS mode_quantite, 0.1000 AS coefficient, 1 AS ordre
  UNION ALL SELECT 'Poulet de chair', 'Abreuvoirs et mangeoires',
         6000, 'forfait', 'INSTALLATION', 'FORFAIT',      1.0000, 2
  UNION ALL SELECT 'Poulet de chair', 'Litiere, mise en place',
         2000, 'forfait', 'INSTALLATION', 'FORFAIT',      1.0000, 3

  -- Poulet de chair — CYCLE, pour 33 sujets achetes :
  --   33 x 600 + 33 x 1,8 x 345 + 33 x 100 + 2 500 + 1 500 = 47 593
  UNION ALL SELECT 'Poulet de chair', 'Achat des sujets',
          600, 'sujet',   'CYCLE',        'PAR_SUJET',    1.0000, 4
  UNION ALL SELECT 'Poulet de chair', 'Aliment',
          345, 'kg',      'CYCLE',        'PAR_SUJET',    1.8000, 5
  UNION ALL SELECT 'Poulet de chair', 'Vaccins et soins',
          100, 'sujet',   'CYCLE',        'PAR_SUJET',    1.0000, 6
  UNION ALL SELECT 'Poulet de chair', 'Eau et energie',
         2500, 'forfait', 'CYCLE',        'FORFAIT',      1.0000, 7
  UNION ALL SELECT 'Poulet de chair', 'Transport',
         1500, 'forfait', 'CYCLE',        'FORFAIT',      1.0000, 8

  -- [A VALIDER] Chevre sahelienne — CYCLE pour 1 sujet : total 48 000
  UNION ALL SELECT 'Chevre sahelienne', 'Achat de l''animal',
        35000, 'sujet',   'CYCLE',        'PAR_SUJET',    1.0000, 1
  UNION ALL SELECT 'Chevre sahelienne', 'Aliment complementaire',
         8000, 'forfait', 'CYCLE',        'FORFAIT',      1.0000, 2
  UNION ALL SELECT 'Chevre sahelienne', 'Vaccins et deparasitage',
         3000, 'forfait', 'CYCLE',        'FORFAIT',      1.0000, 3
  UNION ALL SELECT 'Chevre sahelienne', 'Transport',
         2000, 'forfait', 'CYCLE',        'FORFAIT',      1.0000, 4
  UNION ALL SELECT 'Chevre sahelienne', 'Abri et cloture',
        15000, 'forfait', 'INSTALLATION', 'FORFAIT',      1.0000, 5

  -- [A VALIDER] Belier d'embouche — CYCLE pour 1 sujet : total 80 000
  UNION ALL SELECT 'Belier d''embouche', 'Achat de l''animal',
        55000, 'sujet',   'CYCLE',        'PAR_SUJET',    1.0000, 1
  UNION ALL SELECT 'Belier d''embouche', 'Aliment d''embouche',
        18000, 'forfait', 'CYCLE',        'FORFAIT',      1.0000, 2
  UNION ALL SELECT 'Belier d''embouche', 'Vaccins et deparasitage',
         4000, 'forfait', 'CYCLE',        'FORFAIT',      1.0000, 3
  UNION ALL SELECT 'Belier d''embouche', 'Transport',
         3000, 'forfait', 'CYCLE',        'FORFAIT',      1.0000, 4
  UNION ALL SELECT 'Belier d''embouche', 'Abri et cloture',
        25000, 'forfait', 'INSTALLATION', 'FORFAIT',      1.0000, 5

  -- [A VALIDER] Veau d'embouche — CYCLE pour 1 sujet : total 260 000
  UNION ALL SELECT 'Veau d''embouche', 'Achat de l''animal',
       180000, 'sujet',   'CYCLE',        'PAR_SUJET',    1.0000, 1
  UNION ALL SELECT 'Veau d''embouche', 'Aliment d''embouche',
        60000, 'forfait', 'CYCLE',        'FORFAIT',      1.0000, 2
  UNION ALL SELECT 'Veau d''embouche', 'Vaccins et soins',
        12000, 'forfait', 'CYCLE',        'FORFAIT',      1.0000, 3
  UNION ALL SELECT 'Veau d''embouche', 'Transport',
         8000, 'forfait', 'CYCLE',        'FORFAIT',      1.0000, 4
  UNION ALL SELECT 'Veau d''embouche', 'Etable et cloture',
        60000, 'forfait', 'INSTALLATION', 'FORFAIT',      1.0000, 5
) AS v
JOIN type_elevage t ON t.libelle = v.type;

-- ── calendrier sanitaire ─────────────────────────────────────────────
-- « Newcastle J7 et J21 » : la maquette le dit sur la fiche du referentiel
-- ET sur l'ecran d'investissement (« Vaccins et soins — Newcastle J7 et
-- J21 »). Ces deux lignes engendrent les rappels a la creation d'un lot.

INSERT INTO calendrier_sanitaire (type_elevage_id, libelle, type_evenement,
        jour_du_cycle, obligatoire, ordre, date_creation)
SELECT t.id, v.libelle, v.type_evenement, v.jour, TRUE, v.ordre, NOW(6)
FROM (
  SELECT 'Poulet de chair' AS type, 'Vaccination Newcastle (J7)'  AS libelle,
         'VACCINATION' AS type_evenement,  7 AS jour, 1 AS ordre
  UNION ALL SELECT 'Poulet de chair', 'Vaccination Newcastle (J21)',
         'VACCINATION', 21, 2
) AS v
JOIN type_elevage t ON t.libelle = v.type;
