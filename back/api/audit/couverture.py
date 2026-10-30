#!/usr/bin/env python3
"""Volet 8 : quelles routes du back ne sont appelees par AUCUN scenario.

AJOUTE LE 5 OCTOBRE, apres trois comptages de tete et deux erreurs.

Les sept autres volets confrontent le code A LUI-MEME ou au schema. Celui-ci
confronte le code AU PLAN DE TEST, et repond a une question que les autres ne
posent pas : de quelle partie du back ne sait-on rien ?

Pourquoi cette question compte sur ce projet precisement. Les trois defauts
critiques du 3 octobre - compteur de verrouillage annule par le rollback,
compteur de code annule de la meme facon, et [Mes informations] rendant 200
sans rien ecrire - plus celui du 4 octobre - [precision], mot reserve de MySQL
- passaient TOUS la compilation, l'audit statique et la validation du schema.
Le dernier a meme survecu au rejeu des cinq migrations, parce que ce rejeu se
faisait sur MariaDB, qui ne reserve pas ce mot.

Quatre fois sur quatre, seule l'execution les a reveles. Une route qu'aucun
scenario n'appelle est donc exactement une route sur laquelle aucune garantie
ne pese, quel que soit le nombre de controles statiques qu'elle traverse.

Mesure du 5 octobre, avant le bloc J : 78 routes declarees, 69 appelees, NEUF
jamais. Parmi elles, le garde-fou du correctif d'audit n 4 - celui qui refuse
de changer la filiere d'un type d'elevage employe - n'avait jamais ete execute.

Usage :
    python3 couverture.py [chemin/vers/test-back-complet.ps1]

Sans argument, le plan est cherche aux emplacements habituels. S'il est
introuvable, le volet s'efface sans faire echouer l'audit : c'est le meme
parti que le volet 6, qui ne tourne que si une base de reference est fournie.
"""
import pathlib
import re
import sys

ICI = pathlib.Path(__file__).resolve().parent
CTRL = ICI.parent / "src" / "main" / "java" / "ml" / "moncheptel" / "api" / "controleur"

# Emplacements habituels, du plus proche au plus lointain.
CANDIDATS = [
    ICI.parent / "test-back-complet.ps1",
    ICI.parent.parent / "test-back-complet.ps1",
    ICI.parent.parent.parent / "test-back-complet.ps1",
    pathlib.Path.home() / "test-back-complet.ps1",
]

VERBE = {"GetMapping": "GET", "PostMapping": "POST", "PutMapping": "PUT",
         "DeleteMapping": "DELETE", "PatchMapping": "PATCH"}


def normaliser(chemin):
    """Ramene code et script a une forme comparable.

    {id} dans une annotation et $ID_LOT dans le script designent la meme
    route : les deux deviennent *. Un identifiant litteral aussi, sinon
    /api/cheptel/999999 du script ne rencontrerait jamais /api/cheptel/{id}.
    """
    c = re.sub(r"\{[^}]+\}", "*", chemin)
    c = re.sub(r"\$\{?[A-Za-z_][A-Za-z0-9_]*\}?", "*", c)
    c = re.sub(r"\$\([^)]*\)", "*", c)
    c = re.sub(r"/\d+", "/*", c)
    c = c.split("?")[0].rstrip("/")
    return c or "/"


def routes_declarees():
    routes = {}
    for f in sorted(CTRL.glob("*.java")):
        src = f.read_text(encoding="utf-8")
        m = re.search(r'@RequestMapping\(\s*"([^"]*)"', src)
        prefixe = m.group(1) if m else ""
        motif = r'@(' + "|".join(VERBE) + r')(?:\(\s*"([^"]*)"\s*\))?'
        for mm in re.finditer(motif, src):
            verbe = VERBE[mm.group(1)]
            plein = (prefixe + (mm.group(2) or "")) or "/"
            routes[(verbe, normaliser(plein))] = (verbe, plein, f.name)
    return routes


def appels_du_plan(plan):
    """Les routes appelees, et le nombre d'appels que l'on ne sait pas lire.

    L'ANGLE MORT, ET POURQUOI IL EST SIGNALE.

    On ne reconnait qu'un chemin ecrit en CHAINE LITTERALE. Un appel de la
    forme `Invoke-Api DELETE $chemin` range le chemin dans une variable, et
    resoudre une variable PowerShell depuis un script Python serait fragile :
    il faudrait suivre les affectations, les portees et les interpolations.

    Le danger n'est pas de rater l'appel, c'est de rater l'appel EN SILENCE.
    Mesure du 5 octobre, premiere execution de ce volet : il a declare
    DELETE /api/admin/veterinaires/{id}/competences/{maladieId} non couverte
    alors que deux scenarios l'appelaient - par variable. Un faux positif
    dans un audit est pire qu'une absence de controle : on cherche un defaut
    qui n'existe pas, et on finit par ne plus croire l'audit du tout.

    On compte donc ces appels et on les annonce, pour que le chiffre de
    couverture soit lu avec sa marge d'erreur plutot que comme une verite.
    """
    appels = set()
    texte = plan.read_text(encoding="ascii", errors="replace")
    motif = r"Invoke-Api\s+(GET|POST|PUT|DELETE|PATCH)\s+['\"]([^'\"]+)['\"]"
    for mm in re.finditer(motif, texte):
        appels.add((mm.group(1), normaliser(mm.group(2))))

    # Meme verbe, mais suivi d'autre chose qu'un guillemet : variable,
    # sous-expression, concatenation.
    opaque = r"Invoke-Api\s+(?:GET|POST|PUT|DELETE|PATCH)\s+(?!['\"])\S"
    illisibles = len(re.findall(opaque, texte))
    return appels, illisibles


def main():
    if not CTRL.is_dir():
        print(f"  controleurs introuvables sous {CTRL} : volet ignore.")
        return 0

    if len(sys.argv) > 1:
        plan = pathlib.Path(sys.argv[1])
        if not plan.is_file():
            print(f"  plan de test introuvable : {plan}")
            return 1
    else:
        plan = next((c for c in CANDIDATS if c.is_file()), None)
        if plan is None:
            print("  plan de test introuvable a cote du projet : volet ignore.")
            print("  (passez son chemin en argument pour le forcer)")
            return 0

    routes = routes_declarees()
    appels, illisibles = appels_du_plan(plan)
    jamais = sorted(v for k, v in routes.items() if k not in appels)
    inconnus = sorted(a for a in appels if a not in routes)

    print(f"{len(routes)} routes declarees, "
          f"{len(routes) - len(jamais)} exercees par {plan.name}")

    if illisibles:
        print()
        print(f"  MARGE D'ERREUR : {illisibles} appel(s) passent leur chemin")
        print("  par une variable et ne sont pas lisibles ici. Une route")
        print("  listee ci-dessous peut donc etre couverte malgre tout.")
        print("  Ecrivez le chemin en clair dans le plan pour lever le doute.")

    if jamais:
        print()
        print(f"  {len(jamais)} ROUTE(S) QU'AUCUN SCENARIO N'APPELLE :")
        for verbe, chemin, fichier in jamais:
            print(f"    {verbe:7} {chemin:56} {fichier}")
        print()
        print("  Ce n'est pas une faute de code : c'est la part du back sur")
        print("  laquelle aucune garantie ne pese. Voir l'en-tete de ce")
        print("  fichier pour pourquoi, sur ce projet, cela suffit a inquieter.")

    if inconnus:
        print()
        print("  Appels du plan sans route correspondante - a verifier a la")
        print("  main, la normalisation peut echouer sur un chemin construit :")
        for verbe, chemin in inconnus:
            print(f"    {verbe:7} {chemin}")

    if not jamais:
        print()
        print("  Aucun probleme : chaque route declaree est appelee par au")
        print("  moins un scenario.")

    # Volontairement 0 meme avec des routes non couvertes : `set -e` dans
    # auditer.sh arreterait l'audit, et une couverture incomplete est un
    # constat a lire, pas une faute qui doit masquer les volets suivants.
    return 0


if __name__ == "__main__":
    sys.exit(main())
