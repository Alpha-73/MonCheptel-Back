"""Valide les methodes DERIVEES de Spring Data (findByXAndY...) contre le
modele. Spring les resout au demarrage : une propriete mal nommee fait
echouer la creation du repository."""
import re, json, pathlib, sys

RACINE = pathlib.Path('/home/claude/moncheptel/api/src/main/java/ml/moncheptel/api')
M = json.load(open('modele.json'))
PLAT, BRUT = M['plat'], M['brut']

MOTS_CLES = ['IgnoreCase','OrderBy','Asc','Desc','Between','LessThanEqual','LessThan',
             'GreaterThanEqual','GreaterThan','Before','After','IsNull','NotNull',
             'IsNotNull','NotIn','In','Like','StartingWith','EndingWith','Containing',
             'IsTrue','IsFalse','True','False','Not','Distinct','Top','First','And','Or']
PREFIXES = ['findAllBy','findAll','findBy','find','readBy','getBy','queryBy',
            'countBy','count','existsBy','exists','deleteBy','delete','removeBy','remove']

def cible(t):
    m = re.match(r'(?:Set|List|Collection|Optional|Page|Slice)<(\w+)>', t)
    return m.group(1) if m else t

def resoudre_propriete(entite, mot):
    """Resout un segment type 'ProprietaireIdAndCode' -> chemins imbriques.
    Rend (ok, message). Essaie la plus longue correspondance d'abord."""
    if not mot:
        return True, None
    champs = PLAT.get(entite, {})
    # candidats : champ dont le nom capitalise prefixe le mot
    candidats = sorted(champs, key=len, reverse=True)
    for c in candidats:
        cap = c[0].upper() + c[1:]
        if mot == cap:
            return True, None
        if mot.startswith(cap):
            reste = mot[len(cap):]
            suivant = cible(champs[c]['type'])
            if suivant in PLAT:
                return resoudre_propriete(suivant, reste)
            # type simple : le reste doit etre vide
            return (reste == '', None if reste == '' else
                    f"{entite}.{c} est un {champs[c]['type']} : '{reste}' n'a pas de sens apres")
    return False, f"{entite} n'a aucune propriete correspondant a '{mot}'"

problemes, nb = [], 0
for f in sorted((RACINE / 'repository').glob('*.java')):
    src = f.read_text()
    m = re.search(r'extends JpaRepository<(\w+),', src)
    if not m:
        continue
    entite = m.group(1)
    # methodes SANS @Query : ce sont les derivees
    for mm in re.finditer(r'(@Query|@Modifying|@Lock|@EntityGraph)?[^;{}]*?\b([\w<>,\s\.]+?)\s+(\w+)\s*\(([^)]*)\)\s*;', src):
        avant = src[max(0, mm.start()-400):mm.start()]
        if '@Query' in avant.split(';')[-1]:
            continue
        nom = mm.group(3)
        if not any(nom.startswith(p) for p in PREFIXES):
            continue
        nb += 1
        # retirer le prefixe
        reste = nom
        for p in PREFIXES:
            if nom.startswith(p):
                reste = nom[len(p):]; break
        # couper sur OrderBy
        if 'OrderBy' in reste:
            criteres, tri = reste.split('OrderBy', 1)
        else:
            criteres, tri = reste, ''
        # les criteres, decoupes sur And / Or
        segments = re.split(r'And(?=[A-Z])|Or(?=[A-Z])', criteres) if criteres else []
        for seg in segments:
            if not seg: continue
            # enlever les mots-cles de comparaison en suffixe
            for k in sorted(MOTS_CLES, key=len, reverse=True):
                if seg.endswith(k) and len(seg) > len(k):
                    seg = seg[:-len(k)]; break
            ok, msg = resoudre_propriete(entite, seg)
            if not ok:
                problemes.append((f"{f.name}  {nom}", msg))
        # le tri
        for t in re.split(r'(?<=Asc)|(?<=Desc)', tri):
            t = t.replace('Asc','').replace('Desc','')
            if not t: continue
            ok, msg = resoudre_propriete(entite, t)
            if not ok:
                problemes.append((f"{f.name}  {nom}", f"tri : {msg}"))

print(f"{nb} methodes derivees analysees\n")
if problemes:
    print(f"{len(problemes)} PROBLEME(S) :")
    for c, m in problemes: print(f"  {c:56} {m}")
    sys.exit(1)
print("Aucun probleme : chaque segment de chaque nom de methode se resout")
print("en une propriete reelle de l'entite (chemins imbriques compris).")
