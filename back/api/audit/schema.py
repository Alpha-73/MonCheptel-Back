# -*- coding: utf-8 -*-
"""Confronte les entites JPA au schema reellement produit par les migrations.

POURQUOI CE CONTROLE EXISTE
---------------------------
`spring.jpa.hibernate.ddl-auto` vaut `validate`. Au demarrage, Hibernate
verifie que chaque table et chaque colonne qu'il attend existe reellement.
Une seule qui manque, et l'application NE DEMARRE PAS — avec un message
qui arrive au pire moment, c'est-a-dire devant un jury.

Les autres controles de cette suite lisent le code Java. Celui-ci le
confronte a la base : c'est le seul qui puisse attraper un oubli de
migration.

CE QU'IL NE FAIT PAS
--------------------
Il ne verifie pas les types ni la nullabilite. Hibernate `validate` ne le
fait pas non plus de facon fiable d'un dialecte a l'autre. Ce controle
repond a une seule question, celle qui empeche de demarrer : la colonne
existe-t-elle ?

USAGE
-----
    python3 audit/schema.py <socket-mariadb> <base>
"""
import io
import pathlib
import re
import subprocess
import sys

RACINE = pathlib.Path(__file__).resolve().parent.parent / 'src/main/java/ml/moncheptel/api'


def serpent(nom):
    """camelCase -> camel_case, la strategie de nommage par defaut de Spring."""
    return re.sub(r'([a-z0-9])([A-Z])', r'\1_\2', nom).lower()


def lire_entites():
    """Rend {table: {colonnes attendues}} d'apres les annotations JPA."""
    attendu = {}
    for f in sorted((RACINE / 'modele').glob('*.java')):
        s = io.open(f, encoding='utf-8').read()
        if '@Entity' not in s:
            continue
        nom_classe = f.stem
        m = re.search(r'@Table\(\s*name\s*=\s*"([^"]+)"', s)
        table = m.group(1) if m else serpent(nom_classe)

        # Heritage JOINED : la sous-classe a sa propre table, et sa cle
        # primaire porte le nom donne par @PrimaryKeyJoinColumn.
        mp = re.search(r'@PrimaryKeyJoinColumn\(\s*name\s*=\s*"([^"]+)"', s)
        colonnes = {mp.group(1)} if mp else {'id'}

        # EntiteBase apporte id, date_creation, date_modification aux racines.
        if 'extends EntiteBase' in s:
            colonnes |= {'id', 'date_creation', 'date_modification'}

        # Champs persistants : on saute les collections et les transitoires.
        #
        # Les annotations sont relues en REMONTANT depuis la declaration
        # jusqu'a la precedente, et non par une expression qui tenterait de
        # les capturer d'un bloc. La premiere version faisait cela, avec
        # `@[\w.]+\([^)]*\)` — qui ne sait pas lire une parenthese
        # imbriquee. Or @CollectionTable en contient une :
        #
        #     @CollectionTable(name = "eleveur_filiere",
        #                      joinColumns = @JoinColumn(name = "eleveur_id"))
        #
        # La chaine d'annotations etait donc tronquee, @ElementCollection
        # perdu, et le @Column(name = "filiere") de la table de collection
        # attribue a la table principale. L'audit annoncait trois colonnes
        # absentes qui existaient toutes.
        #
        # Un faux positif dans un audit est pire qu'une absence de controle :
        # on prend l'habitude de l'ignorer, et le vrai defaut passe avec.
        precedent = 0
        for mc in re.finditer(
                r'private\s+[\w<>,\[\] .]+\s+(\w+)\s*[;=]', s):
            annos, champ = s[precedent:mc.start()], mc.group(1)
            precedent = mc.end()
            if '@Transient' in annos or '@ElementCollection' in annos:
                continue
            if '@OneToMany' in annos or '@ManyToMany' in annos:
                continue        # cote inverse : aucune colonne ici
            if 'static' in mc.group(0):
                continue
            mj = re.search(r'@JoinColumn\(\s*name\s*=\s*"([^"]+)"', annos)
            mn = re.search(r'@Column\(([^)]*)\)', annos)
            if mj:
                colonnes.add(mj.group(1))
            elif mn and 'name' in mn.group(1):
                colonnes.add(re.search(r'name\s*=\s*"([^"]+)"', mn.group(1)).group(1))
            elif '@ManyToOne' in annos or '@OneToOne' in annos:
                colonnes.add(serpent(champ) + '_id')
            else:
                colonnes.add(serpent(champ))

        attendu.setdefault(table, set()).update(colonnes)

        # Tables de collection d'elements.
        for mcol in re.finditer(
                r'@CollectionTable\(\s*\n?\s*name\s*=\s*"([^"]+)"[^)]*'
                r'joinColumns\s*=\s*@JoinColumn\(\s*name\s*=\s*"([^"]+)"', s):
            attendu.setdefault(mcol.group(1), set()).update({mcol.group(2)})
    return attendu


def lire_base(socket, base):
    sql = ("SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS "
           "WHERE TABLE_SCHEMA='%s';" % base)
    sortie = subprocess.run(['mariadb', '--socket=' + socket, '-N', '-B', '-e', sql],
                            capture_output=True, text=True, check=True).stdout
    reel = {}
    for ligne in sortie.strip().splitlines():
        t, c = ligne.split('\t')
        reel.setdefault(t, set()).add(c)
    return reel


def main():
    socket, base = sys.argv[1], sys.argv[2]
    attendu, reel = lire_entites(), lire_base(socket, base)

    problemes = []
    for table, colonnes in sorted(attendu.items()):
        if table not in reel:
            problemes.append("TABLE ABSENTE : %s" % table)
            continue
        for c in sorted(colonnes - reel[table]):
            problemes.append("COLONNE ABSENTE : %s.%s" % (table, c))

    print("=== 6. entites JPA contre schema reel ===")
    print("%d tables attendues par les entites, %d tables en base"
          % (len(attendu), len(reel)))
    inutiles = sorted(set(reel) - set(attendu) - {'flyway_schema_history'})
    if inutiles:
        print("tables en base sans entite (normal pour les tables de liaison) : %s"
              % ', '.join(inutiles))
    print()
    if problemes:
        print("%d PROBLEME(S) — l'application NE DEMARRERA PAS :\n" % len(problemes))
        for p in problemes:
            print("  " + p)
        return 1
    print("Aucun probleme : chaque table et chaque colonne attendue par une")
    print("entite existe dans le schema produit par les migrations.")
    return 0


if __name__ == '__main__':
    sys.exit(main())
