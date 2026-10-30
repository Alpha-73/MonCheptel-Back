#!/usr/bin/env python3
"""Volet 9 : les constantes d'enumeration citees dans le PLAN DE TEST.

AJOUTE LE 5 OCTOBRE, apres la sixieme erreur du meme genre.

Le volet 2 verifie les constantes d'enumeration citees dans le CODE. Celui-ci
verifie celles citees dans le PLAN DE TEST, qui n'est pas compile et que rien
ne relisait.

LA MESURE QUI L'A MOTIVE. Le bloc J envoyait difficulte = 'FACILE'. Les
constantes reelles de NiveauDifficulte sont ACCESSIBLE, MOYEN, EXIGEANT :
FACILE n'existe pas et n'a jamais existe. Consequences de cette seule chaine :

  - la creation du type d'essai partait en 400, donc le type n'existait pas,
    donc ONZE scenarios (J30 a J39) se declaraient IGNORE ;
  - J40 rendait 400 au lieu de 404, parce que Jackson refuse le corps avant
    que la route ne cherche l'identifiant demande.

Soit douze scenarios perdus, et une execution entiere a refaire, pour une
constante inventee au lieu d'etre lue. C'etait la sixieme erreur d'ancrage de
ce genre dans le plan.

CE QUE CE VOLET ATTRAPE, ET CE QU'IL N'ATTRAPE PAS.

On deduit le type attendu de chaque champ en lisant les records de dto/requete
et les entites : `NiveauDifficulte difficulte` dit que le champ difficulte
porte un NiveauDifficulte. On verifie ensuite chaque `champ = 'VALEUR'` du
plan contre les constantes reelles de cette enumeration.

N'est pas attrape : une constante VALIDE employee sur le mauvais champ, si les
deux enumerations la partagent. C'est une faute plus rare que l'invention pure,
qui est celle que je commets.
"""
import pathlib
import re
import sys

ICI = pathlib.Path(__file__).resolve().parent
SRC = ICI.parent / "src" / "main" / "java" / "ml" / "moncheptel" / "api"
ENUMS = SRC / "modele" / "enumeration"

CANDIDATS = [
    ICI.parent / "test-back-complet.ps1",
    ICI.parent.parent / "test-back-complet.ps1",
    ICI.parent.parent.parent / "test-back-complet.ps1",
    pathlib.Path.home() / "test-back-complet.ps1",
]


def constantes_par_enum():
    """Nom d'enumeration -> ensemble de ses constantes."""
    tables = {}
    for f in sorted(ENUMS.glob("*.java")):
        src = f.read_text(encoding="utf-8")
        if not re.search(r"\benum\s+" + re.escape(f.stem) + r"\b", src):
            continue
        # Le corps s'arrete au premier ';' de premier niveau : apres lui
        # viennent les champs et methodes, dont les noms en majuscules
        # (constantes privees) ne sont pas des valeurs de l'enumeration.
        corps = src.split("{", 1)[1] if "{" in src else ""
        corps = corps.split(";", 1)[0]
        # Une constante est un identifiant majuscule en debut de declaration,
        # eventuellement suivi d'arguments de constructeur.
        valeurs = set(re.findall(r"(?m)^\s*([A-Z][A-Z0-9_]*)\s*[\(,;]", corps))
        if valeurs:
            tables[f.stem] = valeurs
    return tables


def champs_typees():
    """Nom de champ -> nom d'enumeration, lu dans les DTO et les entites."""
    noms = {f.stem for f in ENUMS.glob("*.java")}
    champs = {}
    dossiers = [SRC / "dto" / "requete", SRC / "dto" / "reponse", SRC / "modele"]
    for d in dossiers:
        if not d.is_dir():
            continue
        for f in d.rglob("*.java"):
            src = f.read_text(encoding="utf-8")
            for enum, champ in re.findall(
                    r"\b([A-Z][A-Za-z0-9]*)\s+([a-z][A-Za-z0-9]*)\s*[,;)=]", src):
                if enum in noms:
                    champs.setdefault(champ, set()).add(enum)
    return champs


def main():
    if not ENUMS.is_dir():
        print(f"  enumerations introuvables sous {ENUMS} : volet ignore.")
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

    tables = constantes_par_enum()
    champs = champs_typees()
    texte = plan.read_text(encoding="ascii", errors="replace")

    # champ = 'VALEUR' ou champ = "VALEUR", valeur en majuscules.
    motif = r"([a-z][A-Za-z0-9]*)\s*=\s*['\"]([A-Z][A-Z0-9_]*)['\"]"
    fautes = []
    verifies = 0
    sans_type = set()

    for champ, valeur in re.findall(motif, texte):
        enums = champs.get(champ)
        if not enums:
            sans_type.add(champ)
            continue
        # Le champ peut etre declare avec le meme nom dans plusieurs DTO ;
        # la valeur est acceptee si UNE de ces enumerations la porte.
        valides = set()
        for e in enums:
            valides |= tables.get(e, set())
        verifies += 1
        if valeur not in valides:
            fautes.append((champ, valeur, sorted(enums), sorted(valides)))

    print(f"{len(tables)} enumerations, {verifies} valeur(s) citee(s) "
          f"dans {plan.name} et rattachee(s) a un champ type")

    if fautes:
        print()
        print(f"  {len(fautes)} VALEUR(S) QUI N'EXISTENT PAS :")
        vues = set()
        for champ, valeur, enums, valides in fautes:
            if (champ, valeur) in vues:
                continue
            vues.add((champ, valeur))
            print(f"    {champ} = '{valeur}'")
            print(f"      type : {', '.join(enums)}")
            print(f"      reel : {', '.join(valides)}")
        print()
        print("  Une valeur inventee fait rendre 400 a la route, et tous les")
        print("  scenarios qui dependaient de l'objet cree se declarent")
        print("  IGNORE. Voir l'en-tete de ce fichier.")
        return 1

    if sans_type:
        print(f"  {len(sans_type)} champ(s) sans type d'enumeration connu, "
              f"non verifies.")

    print()
    print("  Aucun probleme : chaque constante citee par le plan existe")
    print("  dans l'enumeration de son champ.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
