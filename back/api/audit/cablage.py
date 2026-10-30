"""Quatre classes de defaut de demarrage que ni javac ni les tests ne voient :
  1. projections dont les accesseurs ne correspondent pas aux alias du select
  2. cycles de dependances entre beans
  3. @Modifying appele hors transaction
  4. collisions de routes HTTP
  5. @Transactional sur une classe que Spring doit proxifier en CGLIB
"""
import re, json, pathlib, collections, sys

RACINE = pathlib.Path('/home/claude/moncheptel/api/src/main/java/ml/moncheptel/api')
pb = []

def code(f):
    """Source SANS commentaires : un @Transactional cite dans une javadoc
    n'est pas une annotation. Sans ce nettoyage, l'analyseur signalait
    FiltreJwt pour le commentaire qui explique justement son absence."""
    s = f.read_text()
    s = re.sub(r'/\*.*?\*/', ' ', s, flags=re.S)      # /* ... */ et javadoc
    s = re.sub(r'//[^\n]*', ' ', s)                    # // ...
    return s

# ── 1. projections ────────────────────────────────────────────────────────
projections = {}
for f in (RACINE / 'repository' / 'projection').glob('*.java'):
    s = code(f)
    getters = set()
    for m in re.finditer(r'(?:^|\s)([\w<>\.]+)\s+(get\w+|is\w+)\s*\(\s*\)\s*;', s):
        n = m.group(2)
        n = n[3:] if n.startswith('get') else n[2:]
        getters.add(n[0].lower() + n[1:])
    projections[f.stem] = getters

def requetes_de(src):
    res = []
    for m in re.finditer(r'@Query\(', src):
        i = m.end()
        mt = re.match(r'\s*"""(.*?)"""', src[i:], re.S)
        if mt: q, apres = mt.group(1), i + mt.end()
        else:
            mc = re.match(r'\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)', src[i:], re.S)
            if not mc: continue
            q = ' '.join(re.findall(r'"((?:[^"\\]|\\.)*)"', mc.group(1))); apres = i + mc.end()
        fin = min([k for k in (src.find(';', apres), src.find('{', apres)) if k != -1] or [len(src)])
        res.append((' '.join(q.split()), src[apres:fin], src[:m.start()].count('\n') + 1))
    return res

for f in sorted((RACINE / 'repository').glob('*.java')):
    src = code(f)
    for q, sig, ligne in requetes_de(src):
        for proj, getters in projections.items():
            if proj not in sig: continue
            alias = set(re.findall(r'\bas\s+(\w+)', q, re.I))
            manquants = getters - alias
            if manquants:
                pb.append((f"{f.name}:{ligne}",
                           f"{proj} attend {sorted(manquants)} : le select n'a pas d'alias 'as' correspondant"))
            en_trop = alias - getters
            if en_trop:
                pb.append((f"{f.name}:{ligne}",
                           f"alias {sorted(en_trop)} sans accesseur dans {proj}"))

# ── 2. cycles de dependances ──────────────────────────────────────────────
beans, deps = {}, collections.defaultdict(set)
for f in RACINE.rglob('*.java'):
    s = code(f)
    if not re.search(r'@(Component|Service|RestController|Configuration|RestControllerAdvice)\b', s):
        continue
    beans[f.stem] = f
for nom, f in beans.items():
    s = code(f)
    m = re.search(r'public\s+' + nom + r'\(([^{]*?)\)\s*\{', s, re.S)
    if not m: continue
    for t in re.findall(r'\b([A-Z]\w+)\s+\w+\s*(?:,|$)', m.group(1)):
        if t in beans: deps[nom].add(t)
# implementations d'interfaces -> le bean concret
impl = {}
for nom, f in beans.items():
    for i in re.findall(r'class\s+' + nom + r'[^{]*implements\s+([\w,\s]+)\{', code(f)):
        for it in i.split(','):
            impl.setdefault(it.strip(), set()).add(nom)
for nom in list(deps):
    for d in list(deps[nom]):
        pass
# resoudre les interfaces citees en parametre
for nom, f in beans.items():
    s = code(f)
    m = re.search(r'public\s+' + nom + r'\(([^{]*?)\)\s*\{', s, re.S)
    if not m: continue
    for t in re.findall(r'\b([A-Z]\w+)\s+\w+\s*(?:,|$)', m.group(1)):
        if t in impl:
            deps[nom] |= impl[t]

couleur, cycles = {}, []
def visiter(n, chemin):
    couleur[n] = 'gris'
    for d in sorted(deps.get(n, ())):
        if couleur.get(d) == 'gris':
            cycles.append(chemin[chemin.index(d):] + [d] if d in chemin else chemin + [d])
        elif couleur.get(d) != 'noir':
            visiter(d, chemin + [d])
    couleur[n] = 'noir'
for n in sorted(beans):
    if couleur.get(n) != 'noir':
        visiter(n, [n])
for c in cycles:
    pb.append(("cycle de beans", " -> ".join(c)))

# ── 3. @Modifying hors transaction ────────────────────────────────────────
modifiants = set()
for f in (RACINE / 'repository').glob('*.java'):
    s = code(f)
    for m in re.finditer(r'@Modifying\b', s):
        # jusqu'au premier ';' : la declaration complete de la methode
        fin = s.find(';', m.end())
        if fin == -1:
            continue
        decl = s[m.end():fin]
        # retirer les chaines : un count( dans le JPQL n'est pas un nom de methode
        decl = re.sub(r'"(?:[^"\\]|\\.)*"', '""', decl)
        # le nom cherche n'est jamais precede d'un @ : cela ecarte @Query( et @Param(
        noms = re.findall(r'(?<![@\w])(\w+)\s*\(', decl)
        if noms:
            modifiants.add(noms[-1])
for f in (RACINE / 'service').glob('*.java'):
    s = code(f)
    # decouper en methodes
    for m in re.finditer(r'((?:@\w+(?:\([^)]*\))?\s*)*)public\s+[\w<>\[\],\.\s]+?\s+(\w+)\s*\([^)]*\)\s*\{', s):
        debut = m.end()
        prof, i = 1, debut
        while i < len(s) and prof:
            prof += (s[i] == '{') - (s[i] == '}'); i += 1
        corps, ann, meth = s[debut:i], m.group(1), m.group(2)
        for mod in modifiants:
            if re.search(r'\.' + mod + r'\s*\(', corps) and '@Transactional' not in ann:
                pb.append((f"{f.stem}.{meth}",
                           f"appelle {mod}() qui est @Modifying, sans @Transactional sur la methode"))

# ── 4. collisions de routes ───────────────────────────────────────────────
routes = collections.defaultdict(list)
for f in (RACINE / 'controleur').glob('*.java'):
    s = code(f)
    mc = re.search(r'@RequestMapping\("([^"]*)"\)', s)
    base = mc.group(1) if mc else ''
    for m in re.finditer(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\("([^"]*)"\))?', s):
        verbe, chemin = m.group(1), m.group(2) or ''
        plein = (base + chemin) or '/'
        gabarit = re.sub(r'\{\w+\}', '{}', plein)
        routes[(verbe, gabarit)].append(f.stem)
for (verbe, ch), ctrls in sorted(routes.items()):
    if len(ctrls) > 1:
        pb.append(("collision de route", f"{verbe.upper()} {ch} declaree par {ctrls}"))

# ── 5. @Transactional sur une classe proxifiee en CGLIB ───────────────────
CADRES_FINALS = ('OncePerRequestFilter','GenericFilterBean','HttpFilter',
                 'AbstractAuthenticationProcessingFilter','HandlerInterceptorAdapter')
for f in RACINE.rglob('*.java'):
    s = code(f)
    for cadre in CADRES_FINALS:
        if f'extends {cadre}' in s and '@Transactional' in s:
            pb.append((f.stem, f"@Transactional sur une classe qui etend {cadre} : "
                               "CGLIB ne peut pas redefinir ses methodes final"))
    if re.search(r'public\s+final\s+class', s) and '@Transactional' in s:
        pb.append((f.stem, "@Transactional sur une classe final : proxy CGLIB impossible"))

# ── rapport ───────────────────────────────────────────────────────────────
print(f"{len(projections)} projections, {len(beans)} beans, "
      f"{len(modifiants)} methodes @Modifying, {len(routes)} routes\n")
if pb:
    print(f"{len(pb)} PROBLEME(S) :\n")
    for c, m in pb: print(f"  {c:34} {m}")
    sys.exit(1)
print("Aucun probleme sur les cinq classes verifiees.")
