#!/bin/sh
# Audit statique de demarrage — a relancer apres toute modification du back.
# Chaque controle a ete valide en y injectant de vraies fautes : voir AUDIT.md.
#
# `set -e` : le premier volet qui trouve un probleme ARRETE le script, et le
# message final n'est donc jamais imprime a tort.
set -e
cd "$(dirname "$0")"

echo "=== 1. modele JPA ==="            ; python3 modele.py   | tail -3
echo; echo "=== 2. requetes @Query ==="  ; python3 jpql.py
echo; echo "=== 3. methodes derivees ==="; python3 derivees.py
echo; echo "=== 4. cablage Spring ==="   ; python3 cablage.py
echo; echo "=== 5. graphes et roles ===" ; python3 divers.py

# ── 6. entites contre schema reel ───────────────────────────────────────
# Ne tourne que si une base de reference est fournie :
#     bash audit/auditer.sh /tmp/mc.sock ma_base
# C'est le seul controle qui confronte le code A LA BASE plutot qu'a
# lui-meme, donc le seul qui attrape un oubli de migration — la seule
# classe de defaut qui empeche l'application de demarrer.
if [ -n "$1" ] && [ -n "$2" ]; then
    echo; python3 schema.py "$1" "$2"
fi

# ── 7. mots reserves MySQL ──────────────────────────────────────────────
# Ajoute le 4 octobre, apres le 500 sur POST /api/signalements. Le volet 6
# verifie que la colonne EXISTE ; celui-ci verifie qu'Hibernate saura
# l'ECRIRE. `precision` existait bien en base, cite entre accents graves
# par la migration — mais le mapping ne portait pas la citation, et
# l'INSERT partait avec un mot reserve nu.
#
# Le chemin est relatif : `cd` ci-dessus a deja place le shell dans audit/.
# La premiere version ecrivait "$(dirname "$0")/motsreserves.py", ce qui
# cherchait audit/audit/motsreserves.py et echouait toujours.
echo; python3 motsreserves.py

# ── 8. couverture des routes par le plan de test ────────────────────────
# Ajoute le 5 octobre. Les sept volets ci-dessus confrontent le code a
# lui-meme ou au schema ; celui-ci le confronte au PLAN DE TEST, et
# repond a la seule question qu'aucun autre ne pose : de quelle partie
# du back ne sait-on rien ?
#
# Il s'efface sans echouer si le plan n'est pas a cote du projet, comme
# le volet 6 sans base de reference. Et il rend toujours 0 : une
# couverture incomplete est un constat a lire, pas une faute qui doit
# arreter l'audit par `set -e`.
echo; python3 couverture.py

# ── 9. constantes d'enumeration citees par le plan de test ──────────────
# Ajoute le 5 octobre. Le volet 2 verifie les constantes citees dans le
# CODE ; celui-ci verifie celles citees dans le PLAN, qui n'est pas
# compile et que rien ne relisait.
#
# Contrairement au volet 8, il rend 1 et ARRETE l'audit : une couverture
# incomplete est un constat a lire, une constante inventee est une faute.
# Mesure du 5 octobre : un seul 'FACILE' au lieu d'ACCESSIBLE a coute
# douze scenarios et une execution entiere.
echo; python3 enumsplan.py

echo; echo "AUDIT COMPLET : aucun probleme."
