"""Valide les @Query contre le vrai modele JPA : entites, alias, chemins de
proprietes, constantes d'enumeration, parametres nommes."""
import re, json, pathlib, sys

RACINE = pathlib.Path('/home/claude/moncheptel/api/src/main/java/ml/moncheptel/api')
M = json.load(open('modele.json'))
PLAT, BRUT = M['plat'], M['brut']
ENTITES = {n for n in BRUT if BRUT[n]['entite']}

def enums_disponibles():
    d = {}
    for f in (RACINE / 'modele' / 'enumeration').glob('*.java'):
        s = f.read_text()
        vals = set(re.findall(r'^\s{4}([A-Z][A-Z0-9_]*)\s*[(,;]', s, re.M))
        d[f.stem] = vals
    return d
ENUMS = enums_disponibles()

def cible(t):
    m = re.match(r'(?:Set|List|Collection)<(\w+)>', t)
    return m.group(1) if m else t

def extraire_requetes(src, fichier):
    """Rend (requete, parametres_@Param, ligne).

    Une seule passe, sans doublon : on avance dans le fichier, et pour chaque
    @Query on lit soit un text block, soit une suite de chaines concatenees.
    Les @Param sont releves UNIQUEMENT dans la signature qui suit, bornee au
    premier ';' ou '{' — sans quoi ils deborderaient sur la methode suivante.
    """
    res = []
    for m in re.finditer(r'@Query\(', src):
        i = m.end()
        # text block ?
        mt = re.match(r'\s*"""(.*?)"""', src[i:], re.S)
        if mt:
            q = mt.group(1); apres = i + mt.end()
        else:
            mc = re.match(r'\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)', src[i:], re.S)
            if not mc:
                continue
            q = ' '.join(re.findall(r'"((?:[^"\\]|\\.)*)"', mc.group(1)))
            apres = i + mc.end()
        # la signature : du @Query jusqu'au premier ; ou {
        fin = len(src)
        for car in (';', '{'):
            k = src.find(car, apres)
            if k != -1:
                fin = min(fin, k)
        signature = src[apres:fin]
        params = set(re.findall(r'@Param\("(\w+)"\)', signature))
        res.append((' '.join(q.split()), params, src[:m.start()].count('\n') + 1))
    return res

def resoudre(chemin, alias_vers_entite):
    """Verifie un chemin a.b.c ; rend (ok, message)."""
    parties = chemin.split('.')
    if parties[0] not in alias_vers_entite:
        return True, None            # pas un alias : parametre, fonction, litteral
    courant = alias_vers_entite[parties[0]]
    for i, p in enumerate(parties[1:], 1):
        if courant not in PLAT:
            return True, None        # type non-entite : on arrete la verification
        champs = PLAT[courant]
        if p not in champs:
            return False, f"{courant} n'a pas de propriete '{p}' (dans {chemin})"
        courant = cible(champs[p]['type'])
    return True, None

problemes, nb_requetes = [], 0
# COMPTE, PAS CONSTANTE. Le 5 octobre, le message final annoncait « dans 24
# repositories » : un litteral que j'avais ecrit a la main et jamais remis a
# jour. Il y en avait 22 a porter une @Query, sur 26 fichiers. Le nombre de
# requetes, lui, etait bien calcule.
#
# Un chiffre fige dans un audit est pire qu'un chiffre absent : il a l'air
# mesure. Celui-ci est desormais derive des fichiers reellement parcourus.
fichiers_avec_requete = set()
for f in sorted((RACINE / 'repository').glob('*.java')):
    src = f.read_text()
    m = re.search(r'extends JpaRepository<(\w+),', src)
    entite_defaut = m.group(1) if m else None
    for q, params, ligne in extraire_requetes(src, f.name):
        nb_requetes += 1
        fichiers_avec_requete.add(f.name)
        ctx = f"{f.name}:{ligne}"

        # 1. alias declares dans from / join
        alias = {}
        for mm in re.finditer(r'\bfrom\s+(\w+)\s+(\w+)', q, re.I):
            alias[mm.group(2)] = mm.group(1)
        for mm in re.finditer(r'\bjoin\s+(?:fetch\s+)?([\w\.]+)\s+(\w+)\b', q, re.I):
            src_join, al = mm.group(1), mm.group(2)
            if al.lower() in ('on','fetch','where','and','or','left','inner','join'):
                continue
            if '.' in src_join:
                base = src_join.split('.')[0]
                if base in alias:
                    ok, msg = resoudre(src_join, alias)
                    if not ok:
                        problemes.append((ctx, msg)); continue
                    cur = alias[base]
                    for p in src_join.split('.')[1:]:
                        cur = cible(PLAT[cur][p]['type']) if cur in PLAT and p in PLAT[cur] else cur
                    alias[al] = cur
            else:
                alias[al] = src_join
        # join fetch sans alias : join fetch x.y
        for mm in re.finditer(r'\bjoin\s+fetch\s+([\w\.]+)(?=\s|$)', q, re.I):
            ok, msg = resoudre(mm.group(1), alias)
            if not ok:
                problemes.append((ctx, msg))

        # 2. entites nommees existent
        for e in set(alias.values()):
            # Un alias qui se resout en ENUMERATION est legitime : c'est une
            # jointure sur une @ElementCollection, par exemple
            #     join e.filieresPratiquees f
            # ou f designe une constante de Filiere, pas une entite.
            # Sans cette tolerance, le controle signalait a tort toute
            # agregation « par filiere » — et un faux positif dans un audit est
            # pire qu'une absence de controle : on prend l'habitude de l'ignorer.
            if e not in ENTITES and e not in PLAT and e not in ENUMS:
                problemes.append((ctx, f"entite inconnue dans from/join : {e}"))

        # 3. constantes d'enumeration pleinement qualifiees
        for mm in re.finditer(r'ml\.moncheptel\.api\.modele\.enumeration\.(\w+)\.(\w+)', q):
            enum, val = mm.group(1), mm.group(2)
            if enum not in ENUMS:
                problemes.append((ctx, f"enumeration inconnue : {enum}"))
            elif val not in ENUMS[enum]:
                problemes.append((ctx, f"{enum} n'a pas de valeur {val} (valeurs : {sorted(ENUMS[enum])})"))

        # 4. chemins de proprietes
        q_sans_enum = re.sub(r'ml\.moncheptel\.api\.modele\.enumeration\.\w+\.\w+', 'ENUM', q)
        for mm in re.finditer(r'\b([a-z]\w*(?:\.[a-zA-Z]\w*)+)\b', q_sans_enum):
            chemin = mm.group(1)
            if chemin.split('.')[0] not in alias:
                continue
            ok, msg = resoudre(chemin, alias)
            if not ok:
                problemes.append((ctx, msg))

        # 5. parametres nommes
        utilises = set(re.findall(r':(\w+)', q))
        manquants = utilises - params
        inutiles = params - utilises
        if manquants:
            problemes.append((ctx, f":{'/:'.join(sorted(manquants))} utilise sans @Param correspondant"))
        if inutiles:
            problemes.append((ctx, f"@Param inutilise : {sorted(inutiles)}"))

TOTAL_FICHIERS = len(list((RACINE / 'repository').glob('*.java')))
print(f"{nb_requetes} requetes @Query analysees dans "
      f"{len(fichiers_avec_requete)} repositories sur {TOTAL_FICHIERS} "
      f"(les requetes nativeQuery ne sont pas analysables contre le modele)")
print(f"{len(ENTITES)} entites, {len(ENUMS)} enumerations comme referentiel\n")
if problemes:
    print(f"{len(problemes)} PROBLEME(S) :\n")
    for ctx, msg in problemes:
        print(f"  {ctx:44} {msg}")
    sys.exit(1)
else:
    print("Aucun probleme : toutes les entites, tous les alias, tous les chemins")
    print("de proprietes, toutes les constantes d'enumeration et tous les")
    print("parametres nommes se resolvent contre le modele reel.")
