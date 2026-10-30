"""Deux risques supplementaires, verifiables statiquement :
  6. @EntityGraph(attributePaths=...) doit nommer un vrai attribut
  7. @PreAuthorize("hasRole('X')") doit nommer une valeur reelle de Role
     (une faute ici ne casse pas le demarrage : elle refuse TOUT en silence)
"""
import re, json, pathlib, sys
RACINE = pathlib.Path('/home/claude/moncheptel/api/src/main/java/ml/moncheptel/api')
PLAT = json.load(open('modele.json'))['plat']
def code(f):
    s = f.read_text()
    s = re.sub(r'/\*.*?\*/', ' ', s, flags=re.S)
    return re.sub(r'//[^\n]*', ' ', s)

pb, n6, n7 = [], 0, 0

# 6. @EntityGraph
for f in sorted((RACINE / 'repository').glob('*.java')):
    s = code(f)
    m = re.search(r'extends JpaRepository<(\w+),', s)
    if not m: continue
    entite = m.group(1)
    for mm in re.finditer(r'@EntityGraph\(attributePaths\s*=\s*(\{[^}]*\}|"[^"]*")', s):
        for att in re.findall(r'"([^"]+)"', mm.group(1)):
            n6 += 1
            cur, ok = entite, True
            for p in att.split('.'):
                if cur not in PLAT or p not in PLAT[cur]:
                    pb.append((f.name, f"@EntityGraph nomme '{att}' : {cur} n'a pas d'attribut '{p}'"))
                    ok = False; break
                t = PLAT[cur][p]['type']
                mt = re.match(r'(?:Set|List|Collection)<(\w+)>', t)
                cur = mt.group(1) if mt else t

# 7. roles dans @PreAuthorize
roles = set(re.findall(r'^\s{4}([A-Z_]+)\s*[(,;]',
            (RACINE / 'modele/enumeration/Role.java').read_text(), re.M))
for f in sorted(RACINE.rglob('*.java')):
    s = code(f)
    for mm in re.finditer(r'has(?:Any)?Role\(([^)]*)\)', s):
        for r in re.findall(r"'([^']+)'", mm.group(1)):
            n7 += 1
            if r not in roles:
                pb.append((f.stem, f"hasRole('{r}') : Role n'a pas cette valeur (valeurs : {sorted(roles)})"))

print(f"{n6} chemins @EntityGraph, {n7} roles cites dans @PreAuthorize")
print(f"Role declare : {sorted(roles)}\n")
if pb:
    print(f"{len(pb)} PROBLEME(S) :")
    for c, m in pb: print(f"  {c:36} {m}")
    sys.exit(1)
print("Aucun probleme.")
