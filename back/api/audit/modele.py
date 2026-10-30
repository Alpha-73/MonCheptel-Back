"""Extrait de vrai modele JPA depuis les sources Java : champs, types,
associations, heritage. Sert de verite pour valider les requetes."""
import re, pathlib, json

RACINE = pathlib.Path('/home/claude/moncheptel/api/src/main/java/ml/moncheptel/api')

def lire_entites():
    ent = {}
    for f in (RACINE / 'modele').glob('*.java'):
        s = f.read_text()
        est_entite = '@Entity' in s
        est_super = '@MappedSuperclass' in s
        if not (est_entite or est_super):
            continue
        nom = f.stem
        parent = None
        m = re.search(r'class\s+' + nom + r'\s+extends\s+(\w+)', s)
        if m and m.group(1) not in ('Object',):
            parent = m.group(1)
        champs = {}
        # champs persistants : private <type> <nom>  (hors @Transient)
        for mm in re.finditer(
                r'((?:@\w+(?:\((?:[^()]|\([^()]*\))*\))?\s*)*)private\s+([\w<>,\s\.]+?)\s+(\w+)\s*[;=]', s):
            ann, typ, champ = mm.group(1), mm.group(2).strip(), mm.group(3)
            if '@Transient' in ann:
                continue
            champs[champ] = {
                'type': typ,
                'assoc': ('ManyToOne' if '@ManyToOne' in ann else
                          'OneToMany' if '@OneToMany' in ann else
                          'ManyToMany' if '@ManyToMany' in ann else
                          'ElementCollection' if '@ElementCollection' in ann else None),
            }
        ent[nom] = {'parent': parent, 'champs': champs,
                    'entite': est_entite, 'super': est_super}
    return ent

def aplatir(ent):
    """Resout l'heritage : une entite voit les champs de ses parents."""
    def tous(nom, vus=None):
        vus = vus or set()
        if nom in vus or nom not in ent:
            return {}
        vus.add(nom)
        c = dict(ent[nom]['champs'])
        p = ent[nom]['parent']
        if p:
            c.update({k: v for k, v in tous(p, vus).items() if k not in c})
        return c
    return {n: tous(n) for n in ent}

def cible(type_java):
    """Type d'une association : Set<Competence> -> Competence."""
    m = re.match(r'(?:Set|List|Collection)<(\w+)>', type_java)
    return m.group(1) if m else type_java

if __name__ == '__main__':
    ent = lire_entites()
    plat = aplatir(ent)
    entites = [n for n in ent if ent[n]['entite']]
    print(f"{len(entites)} entites, {len(ent) - len(entites)} classe(s) mere(s)")
    for n in sorted(entites):
        p = ent[n]['parent']
        print(f"  {n:22} {len(plat[n]):2} champs" + (f"  (herite de {p})" if p else ""))
    json.dump({'brut': ent, 'plat': plat}, open('modele.json', 'w'), indent=1)
