# =====================================================================
#  MonCheptel - TESTS DU BACK, SCRIPT UNIQUE
#  Les 172 scenarios du plan, en une seule execution.
# =====================================================================
#
#  LANCEMENT
#    1. demarrez l'application avec le profil dev, limite de debit coupee
#       (le script se connecte des dizaines de fois en quelques minutes) :
#         $env:MONCHEPTEL_LIMITE_DEBIT = 'false'   puis  .\lancer.ps1
#    2. GARDEZ LA CONSOLE DU SERVEUR VISIBLE : le bloc H y lit un code ;
#    3. ouvrez PowerShell dans le dossier qui contient ce fichier ;
#    4. tapez :   .\test-back-complet.ps1
#
#  Si Windows refuse d'executer le script :
#       powershell -ExecutionPolicy Bypass -File .\test-back-complet.ps1
#
#  Pour sauter les trois pauses qui demandent de lire la console :
#       .\test-back-complet.ps1 -SansPause
#  Les scenarios concernes sont alors marques A LA MAIN, pas en echec.
#
#
#  CE QUE CE SCRIPT NE TOUCHE PAS
#  ------------------------------
#  Le jeu de demonstration est lu, jamais modifie. Toutes les ecritures
#  se font sur des comptes et des lots JETABLES que le script cree, avec
#  un suffixe unique par execution.
#
#  C'est un ecart volontaire par rapport au plan livre, qui vendait
#  LOT-014 jusqu'a epuisement. Raison : le script doit pouvoir etre
#  REJOUE. Si la premiere execution detruit le jeu de demonstration, la
#  deuxieme ne donne plus les memes chiffres et plus rien n'est
#  comparable - or il y aura une deuxieme execution, apres correctifs.
#  Et la base du 13 octobre reste intacte.
#
#
#  CE QUI N'EST PAS VERIFIE PAR UN CHIFFRE EXACT, ET POURQUOI
#  ---------------------------------------------------------
#  Les dates du jeu de demonstration sont calculees PAR RAPPORT AU JOUR
#  DE LA MIGRATION. Une base migree le 3 octobre et une base migree le
#  28 septembre ne placent pas les memes operations dans le mois
#  courant : le solde du mois n'a donc pas de valeur attendue stable.
#
#  Le script verifie a la place l'invariant - solde = revenus - depenses
#  - et les ECARTS qu'il provoque lui-meme, dont il maitrise les dates.
#  Les montants lus sont affiches pour information, jamais compares.
#
#  Les effectifs, eux, sont calcules sur les mouvements et ne dependent
#  pas du jour : ils sont donc verifies au chiffre exact.
#
#  Compatible Windows PowerShell 5.1 et PowerShell 7.
# =====================================================================

param(
    [switch]$SansPause,
    [string]$Serveur = 'http://localhost:8080',
    [string]$JournalCourriel = ''
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$BASE = $Serveur.TrimEnd('/')

# Comptes de demonstration - identifiants depuis la migration V5.
$ELEVEUR_MAIL = 'balla.kone@example.ml'
$ELEVEUR_MDP  = 'eleveur2026'
$VETO_MAIL    = 'aissata.traore@example.ml'
$VETO_MDP     = 'veto2026'
$ADMIN_MAIL   = 'admin@moncheptel.ml'
$ADMIN_MDP    = 'admin2026'

# Suffixe unique : le script est rejouable sans recreer la base.
$MARQUE = (Get-Date -Format 'MMddHHmmss')

$script:Resultats = @()
$script:BlocCourant = ''

# ---------------------------------------------------------------------
#  Appel HTTP qui ne leve jamais : on veut LIRE le code, pas planter.
#
#  PowerShell 5.1 leve une exception sur tout code hors 2xx, et le corps
#  de la reponse n'est accessible que par le flux de l'exception.
#  PowerShell 7 range ce corps dans ErrorDetails. Les deux sont geres.
# ---------------------------------------------------------------------
# =====================================================================
#  LA CONVENTION DES CODES DE REPONSE DE CE BACK
# =====================================================================
#
#  Table relevee le 5 octobre dans GestionnaireExceptions, apres TROIS
#  scenarios du bloc J qui attendaient 400 la ou le back rend 422.
#  Ce n'etait pas trois fautes : c'etait une seule, faite trois fois,
#  parce que la regle n'etait ecrite nulle part.
#
#    RessourceIntrouvableException ............ 404
#    RegleMetierException .................... 422
#    ConflitException ........................ 409
#    AccesRefuseException / AccessDenied ..... 403
#    jeton absent, invalide, expire, revoque . 401   (audit global : etait 403)
#    mot de passe provisoire pas change ...... 403   (seuls moi, changement, deconnexion)
#    DataIntegrityViolationException ......... 409
#    limite de debit (connexion, oubli...) ... 429   (MONCHEPTEL_LIMITE_DEBIT=false
#                                                     pour derouler ce script entier)
#    MethodArgumentNotValidException ......... 400   (@NotNull, @Size...)
#    ServletRequestBindingException & co ..... 400   (parametre absent ou mal type)
#    HttpMessageNotReadableException ......... 400   (corps illisible, enum inconnue)
#    ConstraintViolationException ............ 400
#    tout le reste ........................... 500
#
#  LA LIGNE DE PARTAGE, en une phrase :
#
#      400 = la requete est MAL FORMEE.
#      422 = elle est bien formee et VIOLE UNE REGLE METIER.
#
#  Exemples pris dans ce plan :
#    - zoneId=abc .......... 400 : ce n'est pas un nombre (J20)
#    - page=-1 ............. 422 : c'est un nombre, et il est impossible (J03)
#    - postesCout vide ..... 400 : @NotEmpty refuse a la porte (J34)
#    - filiere d'un type employe .. 422 : la requete est valide, la
#      regle metier la refuse (J31)
#    - dates inversees ..... 422 : meme forme que ci-dessus (C05)
#
#  AVANT D'ECRIRE UN CODE ATTENDU : cherchez quelle exception la route
#  leve, puis lisez cette table. Ne la devinez pas.
# =====================================================================

function Invoke-Api {
    param(
        [string]$Methode,
        [string]$Chemin,
        $Corps = $null,
        [string]$Jeton = $null,
        [hashtable]$Entetes = $null
    )
    $h = @{ 'Accept' = 'application/json' }
    if ($Jeton)   { $h['Authorization'] = "Bearer $Jeton" }
    if ($Entetes) { foreach ($k in $Entetes.Keys) { $h[$k] = $Entetes[$k] } }

    $p = @{
        Uri         = "$BASE$Chemin"
        Method      = $Methode
        Headers     = $h
        ErrorAction = 'Stop'
    }
    if ($null -ne $Corps) {
        $p['Body']        = ($Corps | ConvertTo-Json -Depth 10 -Compress)
        $p['ContentType'] = 'application/json; charset=utf-8'
    }
    try { $p['UseBasicParsing'] = $true } catch { }

    $texte = $null
    try {
        $r     = Invoke-WebRequest @p
        # DECODAGE UTF-8 EXPLICITE (10 octobre). Le serveur repond
        # [application/json] sans charset ; PowerShell 5.1 decode alors le
        # corps en ISO-8859-1 et [Diarrhee verdatre] accentue devenait
        # [DiarrhAC)e...] dans le script seulement (D07, E02). L application,
        # elle, lit bien l UTF-8. On relit donc les octets bruts.
        if ($r.RawContentStream) {
            $texte = [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        } else {
            $texte = $r.Content
        }
        $code  = [int]$r.StatusCode
    }
    catch {
        $rep = $null
        if ($_.Exception -and $_.Exception.Response) { $rep = $_.Exception.Response }
        if ($null -eq $rep) { throw }          # serveur injoignable : on arrete
        $code = [int]$rep.StatusCode
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $texte = $_.ErrorDetails.Message
        }
        else {
            try {
                $flux = $rep.GetResponseStream()
                $lect = New-Object System.IO.StreamReader($flux)
                $texte = $lect.ReadToEnd()
            } catch { }
        }
    }

    $objet = $null
    if ($texte) { try { $objet = $texte | ConvertFrom-Json } catch { } }
    return [pscustomobject]@{ Code = $code; Corps = $objet; Texte = $texte }
}

# ---------------------------------------------------------------------
#  Un scenario. $CodeAttendu accepte plusieurs valeurs : certaines
#  regles se defendent legitimement par 409 ou par 422 selon le point
#  de vue, et exiger l'un des deux serait arbitraire.
# ---------------------------------------------------------------------
function Verifier {
    param(
        [string]$Numero,
        [string]$Intitule,
        [int[]]$CodeAttendu,
        $Reponse,
        [scriptblock]$Controle = $null
    )
    $ok = ($CodeAttendu -contains $Reponse.Code)
    $detail = ''

    if (-not $ok) {
        $detail = "code $($Reponse.Code) au lieu de $($CodeAttendu -join ' ou ')"
        if ($Reponse.Corps -and $Reponse.Corps.message) {
            $detail += " - [$($Reponse.Corps.message)]"
        }
        elseif ($Reponse.Texte) {
            $t = $Reponse.Texte
            if ($t.Length -gt 160) { $t = $t.Substring(0,160) + '...' }
            $detail += " - $t"
        }
    }
    elseif ($Controle) {
        $souci = & $Controle $Reponse.Corps
        if ($souci) { $ok = $false; $detail = $souci }
    }

    $script:Resultats += [pscustomobject]@{
        Numero = $Numero; Bloc = $script:BlocCourant; Intitule = $Intitule
        Statut = $(if ($ok) { 'OK' } else { 'ECART' }); Detail = $detail
    }
    if ($ok) {
        Write-Host ("  [OK]     {0,-5} {1}" -f $Numero, $Intitule) -ForegroundColor Green
    } else {
        Write-Host ("  [ECART]  {0,-5} {1}" -f $Numero, $Intitule) -ForegroundColor Red
        Write-Host ("           {0}" -f $detail) -ForegroundColor Yellow
    }
    return $ok
}

# Un scenario qu'on ne peut pas jouer : prerequis manquant, ou lecture
# de la console necessaire. Ce n'est PAS un echec, et il faut que la
# difference soit visible dans le bilan.
function Ignorer {
    param([string]$Numero, [string]$Intitule, [string]$Raison)
    $script:Resultats += [pscustomobject]@{
        Numero = $Numero; Bloc = $script:BlocCourant; Intitule = $Intitule
        Statut = 'IGNORE'; Detail = $Raison
    }
    Write-Host ("  [IGNORE] {0,-5} {1}" -f $Numero, $Intitule) -ForegroundColor DarkGray
    Write-Host ("           {0}" -f $Raison) -ForegroundColor DarkGray
}

function AFaireAMain {
    param([string]$Numero, [string]$Intitule, [string]$Comment)
    $script:Resultats += [pscustomobject]@{
        Numero = $Numero; Bloc = $script:BlocCourant; Intitule = $Intitule
        Statut = 'A LA MAIN'; Detail = $Comment
    }
    Write-Host ("  [MAIN]   {0,-5} {1}" -f $Numero, $Intitule) -ForegroundColor Magenta
}

# Une mesure relevee, affichee mais jamais comparee : voir l'en-tete.
function Noter {
    param([string]$Texte)
    Write-Host ("           . {0}" -f $Texte) -ForegroundColor DarkCyan
}

function Bloc {
    param([string]$Titre)
    $script:BlocCourant = $Titre
    Write-Host ''
    Write-Host ("=== {0} " -f $Titre).PadRight(70,'=') -ForegroundColor Cyan
}

function Demander {
    param([string]$Question)
    Write-Host ''
    Write-Host $Question -ForegroundColor White
    return (Read-Host '   votre saisie').Trim()
}

# ---------------------------------------------------------------------
#  LE CODE A SIX CHIFFRES EST LU DANS UN FICHIER, PLUS DANS LA CONSOLE.
#
#  En profil dev, ServiceCourrielJournal depose une ligne par envoi dans
#  target/courriels-dev.log :
#
#    2026-10-04T22:05:11Z | CODE DE REINITIALISATION | reinit-x@example.ml | Code : 482913   (valable 15 minutes)
#
#  Pourquoi : la console du serveur est le seul endroit ou le code
#  apparaissait, et elle devient illisible des qu'on travaille vraiment.
#  Le serveur du 4 octobre tournait depuis 20h58 ; apres trois executions
#  du plan, elle portait une douzaine de cadres, et il fallait DEVINER
#  lequel etait le bon. H03, H04, H05 et H12b ont echoue deux fois de
#  suite sur un code refuse - et je n'avais aucun moyen de distinguer la
#  mauvaise lecture d'un vrai defaut du serveur.
#
#  Le fichier leve l'ambiguite : on cherche la DERNIERE ligne qui porte
#  l'objet voulu ET l'adresse voulue. Plus personne ne lit rien.
#
#  La saisie a la main reste le repli : si le fichier est introuvable, le
#  script demande, comme avant.
# ---------------------------------------------------------------------
# ---------------------------------------------------------------------
#  LA TRACE D'UN 500 EST LUE PAR LE SCRIPT, PLUS DEMANDEE A LA MAIN.
#
#  En profil dev, GestionnaireExceptions depose chaque erreur inattendue
#  dans target/erreurs-dev.log, separee par une barre de signes egal.
#
#  Pourquoi : j'ai demande cette trace quatre fois de suite sans
#  l'obtenir, alors qu'elle etait ecrite depuis le debut. Demander a
#  quelqu'un de fouiller un fichier est un aller-retour de trop quand le
#  script peut le lire lui-meme - c'est exactement ce qui a regle la
#  lecture des codes de reinitialisation.
#
#  On ne garde que ce qui renseigne : le type d'exception, son message,
#  les lignes [Caused by], et les cadres de pile qui sont DANS
#  ml.moncheptel. Les quarante cadres de Spring et de Tomcat n'apprennent
#  rien et noieraient le CSV.
# ---------------------------------------------------------------------
function TraceRecente {
    param([int]$MaxLignes = 18)
    $chemins = @(
        (Join-Path (Get-Location) 'target\erreurs-dev.log'),
        (Join-Path (Get-Location) 'api\target\erreurs-dev.log'),
        (Join-Path (Get-Location) '..\api\target\erreurs-dev.log')
    )
    $f = $null
    foreach ($c in $chemins) {
        try { if (Test-Path -LiteralPath $c) { $f = $c; break } } catch { }
    }
    if (-not $f) { return 'fichier target\erreurs-dev.log introuvable' }
    Start-Sleep -Milliseconds 400
    try { $lignes = @(Get-Content -LiteralPath $f -ErrorAction Stop) }
    catch { return "erreurs-dev.log illisible : $($_.Exception.Message)" }
    if ($lignes.Count -eq 0) { return 'erreurs-dev.log vide' }

    # Dernier bloc : tout ce qui suit la derniere barre de separation.
    $debut = 0
    for ($i = 0; $i -lt $lignes.Count; $i++) {
        if ($lignes[$i] -like '=====*') { $debut = $i }
    }
    $bloc = @($lignes[$debut..($lignes.Count - 1)])

    $garde = @()
    $n = 0
    foreach ($l in $bloc) {
        $t = $l.Trim()
        if (-not $t) { continue }
        if ($t -like '=====*') { continue }          # barre de separation
        $utile = ($t -notlike 'at *') -or ($t -like '*ml.moncheptel*')
        if ($utile -and $n -lt $MaxLignes) { $garde += $t; $n++ }
    }
    if ($garde.Count -eq 0) { return 'dernier bloc vide' }
    return ($garde -join ' || ')
}

function TrouverJournal {
    param([string]$Explicite)
    $candidats = @()
    if ($Explicite) { $candidats += $Explicite }
    $candidats += @(
        (Join-Path (Get-Location) 'target\courriels-dev.log'),
        (Join-Path (Get-Location) 'api\target\courriels-dev.log'),
        (Join-Path (Get-Location) '..\api\target\courriels-dev.log')
    )
    foreach ($c in $candidats) {
        try { if (Test-Path -LiteralPath $c) { return (Resolve-Path -LiteralPath $c).Path } } catch { }
    }
    return $null
}

# Trace de la derniere recherche, pour que le script puisse EXPLIQUER un
# echec de lecture au lieu de retomber silencieusement sur l'invite.
$script:DERNIERE_RECHERCHE = ''

# Rend la derniere valeur deposee pour cette adresse et cet objet, ou $null.
function ValeurDuJournal {
    param([string]$Objet, [string]$Adresse, [string]$Etiquette)
    $script:DERNIERE_RECHERCHE = "objet [$Objet] adresse [$Adresse] etiquette [$Etiquette]"
    # Sur une base neuve, le journal n'existe pas encore au lancement du
    # script : il est cree au premier envoi. On le recherche donc ici.
    if (-not $script:FICHIER_COURRIELS) { $script:FICHIER_COURRIELS = TrouverJournal $JournalCourriel }
    if (-not $script:FICHIER_COURRIELS) {
        $script:DERNIERE_RECHERCHE += ' ; aucun fichier journal'
        return $null
    }
    # Le serveur ecrit la ligne pendant la transaction ; la reponse HTTP
    # arrive apres. Une courte attente couvre malgre tout le cas d'un
    # disque lent.
    Start-Sleep -Milliseconds 400
    try {
        $lignes = @(Get-Content -LiteralPath $script:FICHIER_COURRIELS -ErrorAction Stop)
    } catch {
        $script:DERNIERE_RECHERCHE += " ; fichier illisible : $($_.Exception.Message)"
        return $null
    }
    $script:DERNIERE_RECHERCHE += " ; $($lignes.Count) ligne(s) dans le fichier"

    # On rapporte CE QU'ON A VU, et non seulement l'echec. Sans cela, une
    # lecture infructueuse retombait en silence sur la saisie a la main, et
    # l'ecart qui s'ensuivait etait indechiffrable : H21 annoncait un
    # provisoire a six chiffres, c'est-a-dire un code de reinitialisation
    # saisi par erreur, sans que rien ne dise pourquoi le fichier n'avait
    # rien rendu.
    $surAdresse = @($lignes | Where-Object { $_ -like "*$Adresse*" })
    $surObjet   = @($lignes | Where-Object { $_ -like "*$Objet*" })
    $script:DERNIERE_RECHERCHE += ", $($surAdresse.Count) sur l adresse, $($surObjet.Count) sur l objet"

    $retenue = $null
    foreach ($l in $lignes) {
        if ($l -like "*$Objet*" -and $l -like "*$Adresse*") { $retenue = $l }
    }
    if (-not $retenue) {
        if ($surAdresse.Count) {
            $derniere = $surAdresse[$surAdresse.Count - 1]
            if ($derniere.Length -gt 200) { $derniere = $derniere.Substring(0,200) + '...' }
            $script:DERNIERE_RECHERCHE += " ; derniere ligne sur cette adresse : [$derniere]"
        }
        return $null
    }
    $i = $retenue.LastIndexOf($Etiquette)
    if ($i -lt 0) {
        $script:DERNIERE_RECHERCHE += " ; ligne trouvee mais etiquette absente : [$retenue]"
        return $null
    }
    $reste = $retenue.Substring($i + $Etiquette.Length)
    # [Code : 482913   (valable 15 minutes)] -> on coupe a la parenthese
    $j = $reste.IndexOf('(')
    if ($j -ge 0) { $reste = $reste.Substring(0, $j) }
    return $reste.Trim()
}

# =====================================================================
Write-Host ''
Write-Host 'MonCheptel - tests du back, script complet' -ForegroundColor White
Write-Host "Serveur : $BASE"
Write-Host "Marque de cette execution : $MARQUE"

# On distingue DEUX situations que la version precedente confondait sous
# un seul message :
#   - rien n'ecoute sur le port : le serveur est arrete ;
#   - quelque chose repond, mais pas 200 : le serveur tourne, et c'est la
#     sonde de sante qui a un souci.
# Le remede n'est pas le meme.
$joignable = $false
$sante = $null
try   { $sante = Invoke-Api GET '/actuator/health'; $joignable = $true }
catch { $joignable = $false }

if (-not $joignable) {
    Write-Host ''
    Write-Host "RIEN N'ECOUTE SUR $BASE." -ForegroundColor Red
    Write-Host ''
    Write-Host "L'application doit tourner dans une AUTRE fenetre PowerShell," -ForegroundColor Yellow
    Write-Host "et y rester. Si vous avez fait Ctrl+C pour recuperer l'invite," -ForegroundColor Yellow
    Write-Host 'le serveur s est arrete avec.' -ForegroundColor Yellow
    Write-Host ''
    Write-Host '  Fenetre 1 :  cd <...>\api   puis   .\lancer.ps1'
    Write-Host '               attendez [Started MonCheptelApplication]'
    Write-Host '               NE LA FERMEZ PAS, NE FAITES PAS Ctrl+C'
    Write-Host ''
    Write-Host '  Fenetre 2 :  .\test-back-complet.ps1'
    Write-Host ''
    Write-Host 'Pour verifier qui ecoute sur le port 8080 :'
    Write-Host '  Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue'
    Write-Host ''
    exit 1
}

if ($sante.Code -ne 200) {
    Write-Host ''
    Write-Host "Le serveur repond, mais /actuator/health rend $($sante.Code)." -ForegroundColor Yellow
    Write-Host 'On continue : le serveur est la, c est ce qui compte.' -ForegroundColor Yellow
    $script:Resultats += [pscustomobject]@{
        Numero = 'PRE'; Bloc = 'Prealable'; Intitule = '/actuator/health rend 200'
        Statut = 'ECART'; Detail = "code $($sante.Code) au lieu de 200"
    }
} else {
    Write-Host 'Serveur joignable, sante 200.' -ForegroundColor Green
}

$script:FICHIER_COURRIELS = TrouverJournal $JournalCourriel
if ($script:FICHIER_COURRIELS) {
    Write-Host "Journal des courriels : $script:FICHIER_COURRIELS" -ForegroundColor Green
    Write-Host 'Les codes a six chiffres y seront lus : aucune saisie a la main.' -ForegroundColor Green
} else {
    Write-Host ''
    Write-Host 'Journal des courriels introuvable.' -ForegroundColor Yellow
    Write-Host 'Cherche dans  .\target\  puis  .\api\target\  puis  ..\api\target\' -ForegroundColor Yellow
    Write-Host 'Deux causes possibles :' -ForegroundColor Yellow
    Write-Host '  . le correctif ServiceCourrielJournal n est pas applique ;' -ForegroundColor Yellow
    Write-Host '  . ou vous lancez ce script depuis un autre dossier. Dans ce cas :' -ForegroundColor Yellow
    Write-Host '        .\test-back-complet.ps1 -JournalCourriel C:\...\api\target\courriels-dev.log' -ForegroundColor Yellow
    Write-Host 'Le bloc H retombera sur la saisie a la main.' -ForegroundColor Yellow
}

# ---------------------------------------------------------------------
#  DECOUVERTE DES IDENTIFIANTS
#
#  Rien n'est code en dur. Les identifiants numeriques dependent de
#  l'ordre d'insertion des migrations ; les deviner serait exactement
#  l'erreur que ce script est cense detecter chez les autres.
# ---------------------------------------------------------------------
Bloc 'Decouverte des identifiants'

$r = Invoke-Api POST '/api/auth/connexion' @{ email = $ELEVEUR_MAIL; motDePasse = $ELEVEUR_MDP }
if ($r.Code -ne 200) {
    Write-Host ''
    Write-Host "Impossible de se connecter en eleveur ($ELEVEUR_MAIL)." -ForegroundColor Red
    Write-Host "Code $($r.Code). Le jeu de demonstration est-il bien en base," -ForegroundColor Red
    Write-Host 'et la migration V5 appliquee ? Sans cela rien ne peut etre teste.' -ForegroundColor Red
    exit 1
}
$JE = $r.Corps.jetonAcces                      # jeton eleveur
$RAFRAICHIR_E = $r.Corps.jetonRafraichissement
$ID_ELEVEUR = $r.Corps.utilisateurId

$r = Invoke-Api POST '/api/auth/connexion' @{ email = $VETO_MAIL; motDePasse = $VETO_MDP }
$JV = $null; if ($r.Code -eq 200) { $JV = $r.Corps.jetonAcces }
$r = Invoke-Api POST '/api/auth/connexion' @{ email = $ADMIN_MAIL; motDePasse = $ADMIN_MDP }
$JA = $null; if ($r.Code -eq 200) { $JA = $r.Corps.jetonAcces }

if (-not $JV) { Write-Host '  ATTENTION : connexion veterinaire impossible, les blocs F et I seront partiels.' -ForegroundColor Yellow }
if (-not $JA) { Write-Host '  ATTENTION : connexion admin impossible, les blocs F, H et I seront partiels.' -ForegroundColor Yellow }

# Zones
$zones = (Invoke-Api GET '/api/zones').Corps
$ZONE_ID = $null; $ZONE_AUTRE = $null
if ($zones -and $zones.Count -ge 1) {
    $ZONE_ID = $zones[0].id
    $k = $zones | Where-Object { $_.nom -match 'Koutiala' } | Select-Object -First 1
    if ($k) { $ZONE_ID = $k.id }
    $a = $zones | Where-Object { $_.id -ne $ZONE_ID } | Select-Object -First 1
    if ($a) { $ZONE_AUTRE = $a.id }
}
Write-Host ("  zones : {0} trouvees, zone de travail = {1}" -f $zones.Count, $ZONE_ID)

# Types d'elevage, par filiere.
#
# ON ECARTE LES TYPES D'ESSAI. Le scenario F24 cree un type d'elevage, et
# il n'existe aucune route pour le supprimer : chaque execution en laisse
# un de plus dans le referentiel. La premiere version prenait le PREMIER
# type de chaque filiere, et attrapait donc celui qu'une execution
# precedente avait cree.
#
# Mesure du 4 octobre, deuxieme execution : F23 annoncait capacite 50 au
# lieu de 100, mortalite 0,06 au lieu de 0,08 et 2 postes au lieu de 8 -
# c'etaient exactement les valeurs de la [Pondeuse d'essai] creee par F24
# a l'execution precedente. E01, E03, E05 et E05b s'effondraient de la
# meme facon. Le back n'avait rien fait de mal.
#
# On prefere donc le libelle de reference, et a defaut le premier type
# dont le libelle ne contient pas [essai].
#
# LIBELLES ACCENTUES DEPUIS V6. Ce fichier reste en ASCII pur : sans BOM,
# PowerShell 5.1 le lirait en ANSI et un " e " accent aigu ecrit en clair
# deviendrait deux caracteres faux. Les accents sont donc composes par code.
$LIB_CAPRINS = "Ch$([char]0x00E8)vre sah$([char]0x00E9)lienne"
$LIB_OVINS   = "B$([char]0x00E9)lier d'embouche"
$LIB_BOVINS  = "Veau d'embouche"
$REFERENCE = @{ 'VOLAILLE' = 'Poulet de chair'; 'BOVINS' = $LIB_BOVINS
                'OVINS' = $LIB_OVINS; 'CAPRINS' = $LIB_CAPRINS }
$TYPE = @{}
$TOUS_TYPES = @()
if ($JA) {
    $TOUS_TYPES = @((Invoke-Api GET '/api/admin/types-elevage' $null $JA).Corps)
    foreach ($f in @('VOLAILLE','BOVINS','OVINS','CAPRINS')) {
        $candidats = @($TOUS_TYPES | Where-Object { [string]$_.filiere -eq $f })
        $choisi = $candidats | Where-Object { $_.libelle -eq $REFERENCE[$f] } | Select-Object -First 1
        if (-not $choisi) {
            $choisi = $candidats | Where-Object { $_.libelle -notmatch 'essai' } | Select-Object -First 1
        }
        if (-not $choisi) { $choisi = $candidats | Select-Object -First 1 }
        if ($choisi) { $TYPE[$f] = $choisi }
    }
}
Write-Host ("  types d elevage : {0} au total, {1} de reference retenus" -f `
            $TOUS_TYPES.Count, $TYPE.Keys.Count)
foreach ($f in @('VOLAILLE','BOVINS','OVINS','CAPRINS')) {
    if ($TYPE.ContainsKey($f)) { Write-Host ("      {0,-9} {1}" -f $f, $TYPE[$f].libelle) }
}

# Unites de demonstration de l'eleveur
$UNITES = (Invoke-Api GET '/api/cheptel' $null $JE).Corps
function Unite($code) { $UNITES | Where-Object { $_.code -eq $code } | Select-Object -First 1 }
$LOT014 = Unite 'LOT-014'
$LOT009 = Unite 'LOT-009'
$LOT006 = Unite 'LOT-006'
$LOT003 = Unite 'LOT-003'
Write-Host ("  cheptel de demonstration : {0} unites" -f $UNITES.Count)

# Symptomes volaille, par libelle
$SYMPTOMES = (Invoke-Api GET '/api/signalements/symptomes?filiere=VOLAILLE' $null $JE).Corps
function Symptome($motif) {
    $s = $SYMPTOMES | Where-Object { $_.libelle -match $motif } | Select-Object -First 1
    if ($s) { return $s.id }
    return $null
}
$MALADIES_V = (Invoke-Api GET '/api/signalements/maladies?filiere=VOLAILLE' $null $JE).Corps
Write-Host ("  volaille : {0} symptomes, {1} maladies" -f $SYMPTOMES.Count, $MALADIES_V.Count)

# =====================================================================
Bloc 'A - Authentification'

$r = Invoke-Api POST '/api/auth/connexion' @{ email = $ELEVEUR_MAIL; motDePasse = $ELEVEUR_MDP }
Verifier 'A01' 'connexion eleveur' 200 $r {
    param($c)
    if (-not $c.jetonAcces) { return 'aucun jetonAcces' }
    if (($c.jetonAcces -split '\.').Count -ne 3) { return 'le jeton n a pas 3 segments' }
    if (-not $c.jetonRafraichissement) { return 'aucun jetonRafraichissement' }
    if ($c.role -ne 'ELEVEUR') { return "role $($c.role) au lieu de ELEVEUR" }
    if ($c.nom -notmatch 'Balla') { return "nom [$($c.nom)] inattendu" }
    if ($c.doitChangerMotDePasse) { return 'doitChangerMotDePasse devrait etre faux' }
    if ($c.expireDansSecondes -le 0) { return "expireDansSecondes = $($c.expireDansSecondes)" }
    $null
} | Out-Null

Ignorer 'A02' 'bouton Authorize de Swagger' 'geste d interface : sans objet dans un script'

Verifier 'A03' 'profil de l eleveur' 200 (Invoke-Api GET '/api/auth/moi' $null $JE) {
    param($c)
    if ($c.role -ne 'ELEVEUR') { return "role $($c.role)" }
    if (-not $c.email) { return 'aucune adresse' }
    if (-not $c.zone -or -not $c.zone.nom) { return 'aucune zone' }
    if ($c.filieresPratiquees.Count -ne 4) { return "$($c.filieresPratiquees.Count) filieres au lieu de 4" }
    $null
} | Out-Null

$mauvais = Invoke-Api POST '/api/auth/connexion' @{ email = $ELEVEUR_MAIL; motDePasse = 'mauvais' }
Verifier 'A04' 'mot de passe faux' 422 $mauvais | Out-Null
Verifier 'A05' 'adresse inconnue : MEME message qu en A04' 422 (Invoke-Api POST '/api/auth/connexion' @{ email = 'inconnu-xyz@example.ml'; motDePasse = 'x' }) {
    param($c)
    if ($c.message -ne $mauvais.Corps.message) {
        return "messages differents, les comptes sont enumerables : [$($c.message)] contre [$($mauvais.Corps.message)]"
    }
    $null
} | Out-Null
Verifier 'A06' 'adresse vide' 400 (Invoke-Api POST '/api/auth/connexion' @{ email = ''; motDePasse = 'x' }) | Out-Null

$MAIL_J = "jetable-$MARQUE@example.ml"
$MDP_J  = 'jetable2026'
$inscription = @{
    nom = "Compte Jetable $MARQUE"; email = $MAIL_J; telephone = '78000000'
    zoneId = $ZONE_ID; filieres = @('VOLAILLE'); motDePasse = $MDP_J
}
$r = Invoke-Api POST '/api/auth/inscription' $inscription
$inscriptionOk = Verifier 'A07' 'inscription d un compte jetable' 201 $r {
    param($c) if (-not $c.jetonAcces) { 'aucun jeton' } elseif ($c.role -ne 'ELEVEUR') { "role $($c.role)" } }
$JJ = $null; $ID_J = $null
if ($inscriptionOk) { $JJ = $r.Corps.jetonAcces; $ID_J = $r.Corps.utilisateurId }

Verifier 'A08' 'meme adresse rejouee' 409 (Invoke-Api POST '/api/auth/inscription' $inscription) | Out-Null
$c1 = $inscription.Clone(); $c1.email = "court-$MARQUE@example.ml"; $c1.motDePasse = 'court'
Verifier 'A09' 'mot de passe trop court' 400 (Invoke-Api POST '/api/auth/inscription' $c1) | Out-Null
$c2 = $inscription.Clone(); $c2.email = "sf-$MARQUE@example.ml"; $c2.filieres = @()
Verifier 'A10' 'aucune filiere' 400 (Invoke-Api POST '/api/auth/inscription' $c2) | Out-Null

if ($JJ) {
    Verifier 'A11' 'ancien mot de passe faux' 422 (Invoke-Api POST '/api/auth/mot-de-passe' @{
        ancienMotDePasse = 'faux'; nouveauMotDePasse = 'nouveau2026' } $JJ) | Out-Null
} else { Ignorer 'A11' 'ancien mot de passe faux' 'A07 a echoue : pas de compte jetable' }

# Deconnexion : sur une SESSION JETABLE, pas sur celle de l'eleveur de
# demonstration, dont le jeton sert a tout le reste du script.
if ($inscriptionOk) {
    $s = Invoke-Api POST '/api/auth/connexion' @{ email = $MAIL_J; motDePasse = $MDP_J }
    if ($s.Code -eq 200) {
        Verifier 'A12' 'deconnexion : revocation du jeton de rafraichissement' 204 (
            Invoke-Api POST '/api/auth/deconnexion' $null $s.Corps.jetonAcces `
                @{ 'X-Refresh-Token' = $s.Corps.jetonRafraichissement }) | Out-Null
    } else { Ignorer 'A12' 'deconnexion' 'reconnexion du compte jetable impossible' }
} else { Ignorer 'A12' 'deconnexion' 'A07 a echoue' }

# A13 : le verrouillage apres cinq essais. C'est LE correctif critique
# numero 1 du 3 octobre. La preuve n'est pas que le 5e essai echoue -
# un mauvais mot de passe echoue toujours - mais que le 6e echoue ALORS
# QU'IL EST BON.
if ($inscriptionOk) {
    $verrou = "verrou-$MARQUE@example.ml"
    $cv = $inscription.Clone(); $cv.email = $verrou
    $cr = Invoke-Api POST '/api/auth/inscription' $cv
    if ($cr.Code -eq 201) {
        for ($i = 1; $i -le 5; $i++) {
            $null = Invoke-Api POST '/api/auth/connexion' @{ email = $verrou; motDePasse = "faux$i" }
        }
        $bon = Invoke-Api POST '/api/auth/connexion' @{ email = $verrou; motDePasse = $MDP_J }
        Verifier 'A13' 'verrouillage : 6e essai refuse MEME avec le bon mot de passe' 422 $bon {
            param($c) if (-not $c.message) { 'aucun message' } else { $null } } | Out-Null
        if ($bon.Code -eq 200) {
            Noter 'DEFAUT CRITIQUE : le compteur d echecs est annule au rollback (correctif 1 absent)'
        }
    } else { Ignorer 'A13' 'verrouillage apres 5 essais' 'creation du compte de verrouillage impossible' }
} else { Ignorer 'A13' 'verrouillage apres 5 essais' 'A07 a echoue' }

# =====================================================================
Bloc 'B - Tableau de bord et cheptel'

# LE JEU DE DEMONSTRATION N'EST PLUS VERIFIE PAR UN INSTANTANE FIGE.
#
# J'avais ecrit, dans l'en-tete de ce script, que le jeu de demonstration
# etait [lu, jamais modifie]. C'ETAIT FAUX, et la deuxieme execution l'a
# montre : les lots, l'animal et le type d'elevage que le script cree le
# sont dans le compte de Balla Kone - le compte de demonstration lui-meme.
#
# Resultat : 11 unites a la premiere execution, 14 a la deuxieme, et cinq
# ecarts annonces sur un back parfaitement sain.
#
# Deux facons de reparer. Tout creer dans un compte neuf : on perd alors
# la verification du jeu de demonstration, qui est precisement ce qu'un
# jury regardera. Ou verifier ce qui NE BOUGE PAS : les unites de
# reference existent, portent les BONS effectifs, et le tableau de bord
# est COHERENT avec la liste du cheptel.
#
# La seconde est retenue, et c'est un test plus fort qu'un instantane :
# un instantane ne verifie qu'une egalite avec un chiffre ecrit a la
# main, la coherence confronte deux routes entre elles.
$CHEPTEL = @((Invoke-Api GET '/api/cheptel' $null $JE).Corps)
$volActifs = @($CHEPTEL | Where-Object { $_.filiere -eq 'VOLAILLE' -and $_.statut -eq 'ACTIF' })
$attenduActives = $volActifs.Count
$attenduTetes = 0
foreach ($u in $volActifs) { $attenduTetes += [int]$u.effectifRestant }

$NON_LUES_DEDIEE = (Invoke-Api GET '/api/notifications/non-lues' $null $JE)
$r = Invoke-Api GET '/api/tableau-de-bord' $null $JE
Verifier 'B01' 'tableau de bord COHERENT avec la liste du cheptel' 200 $r {
    param($c)
    $pb = @()
    if ($c.filiereAffichee -ne 'VOLAILLE') { $pb += "filiereAffichee $($c.filiereAffichee)" }
    if ($c.unitesActives -ne $attenduActives) {
        $pb += "unitesActives $($c.unitesActives), mais GET /api/cheptel en compte $attenduActives en volaille active"
    }
    if ($c.tetesRestantes -ne $attenduTetes) {
        $pb += "tetesRestantes $($c.tetesRestantes), mais la somme des effectifs du cheptel vaut $attenduTetes"
    }
    # COHERENCE, PLUS UN INSTANTANE.
    #
    # Mesure du 5 octobre : ce controle exigeait
    # notificationsNonLues >= 2, le compte du jeu de demonstration.
    # Or le scenario J05 marque TOUTES les notifications de Balla
    # Kone comme lues, et ca ne se defait pas : B01 echouait donc a
    # chaque execution suivante, et le back n'avait rien fait de mal.
    #
    # C'est exactement la faute que le commentaire d'en-tete de ce
    # bloc denonce, commise vingt lignes plus bas par la meme main.
    # On confronte donc le tableau de bord a la route dediee : deux
    # chemins de code doivent annoncer le meme nombre, quel qu'il
    # soit, et le test survit a toutes les executions.
    $dedie = 0
    [void][int]::TryParse([string]$NON_LUES_DEDIEE.Corps, [ref]$dedie)
    if ($NON_LUES_DEDIEE.Code -eq 200 -and [int]$c.notificationsNonLues -ne $dedie) {
        $pb += "notificationsNonLues $($c.notificationsNonLues), mais GET /api/notifications/non-lues en compte $dedie"
    }
    if (-not $c.soldeDuMois) { $pb += 'aucun soldeDuMois' }
    elseif ($c.soldeDuMois.solde -ne ($c.soldeDuMois.revenus - $c.soldeDuMois.depenses)) {
        $pb += "invariant rompu : solde $($c.soldeDuMois.solde) <> revenus $($c.soldeDuMois.revenus) - depenses $($c.soldeDuMois.depenses)"
    }
    if ($pb.Count) { return ($pb -join ' ; ') }
    $null
} | Out-Null
if ($r.Code -eq 200) {
    Noter ("volaille active : {0} unites, {1} tetes (calcule depuis le cheptel)" -f $attenduActives, $attenduTetes)
    Noter ("solde du mois : revenus {0}, depenses {1}, solde {2} (releve, non compare)" -f `
        $r.Corps.soldeDuMois.revenus, $r.Corps.soldeDuMois.depenses, $r.Corps.soldeDuMois.solde)
    Noter ("alertes {0}, rappels {1}, cloturees {2}, tetes sorties {3}" -f `
        $r.Corps.alertes.Count, $r.Corps.rappels.Count, $r.Corps.unitesCloturees, $r.Corps.tetesSorties)
}

# B01b : l'ordre des filieres doit etre STABLE entre deux demarrages.
# Hibernate materialise un @ElementCollection en HashSet, dont l'ordre
# d'iteration change a chaque JVM. D'ou filieresOrdonnees().
Verifier 'B01b' 'ordre des filieres : VOLAILLE, BOVINS, OVINS, CAPRINS' 200 $r {
    param($c)
    $attendu = 'VOLAILLE,BOVINS,OVINS,CAPRINS'
    $obtenu  = ($c.filieres -join ',')
    if ($obtenu -ne $attendu) { return "ordre [$obtenu] au lieu de [$attendu] - relancez le serveur et rejouez pour confirmer" }
    $null
} | Out-Null

$capActifs = @($CHEPTEL | Where-Object { $_.filiere -eq 'CAPRINS' -and $_.statut -eq 'ACTIF' })
$attenduCap = $capActifs.Count
$attenduCapTetes = 0
foreach ($u in $capActifs) { $attenduCapTetes += [int]$u.effectifRestant }

$r2 = Invoke-Api GET '/api/tableau-de-bord?filiere=CAPRINS' $null $JE
Verifier 'B02' 'tableau de bord filtre CAPRINS, solde inchange' 200 $r2 {
    param($c)
    $pb = @()
    if ($c.filiereAffichee -ne 'CAPRINS') { $pb += "filiereAffichee $($c.filiereAffichee)" }
    if ($c.unitesActives -ne $attenduCap) {
        $pb += "unitesActives $($c.unitesActives), mais le cheptel en compte $attenduCap en caprins actifs"
    }
    if ($c.tetesRestantes -ne $attenduCapTetes) {
        $pb += "tetesRestantes $($c.tetesRestantes), mais la somme vaut $attenduCapTetes"
    }
    # LE point du scenario : le solde ne se filtre PAS avec l'onglet.
    if ($r.Code -eq 200 -and $c.soldeDuMois.solde -ne $r.Corps.soldeDuMois.solde) {
        $pb += "le solde change avec le filtre ($($c.soldeDuMois.solde) contre $($r.Corps.soldeDuMois.solde)) : il doit rester toutes filieres"
    }
    if ($pb.Count) { return ($pb -join ' ; ') }
    $null
} | Out-Null

# Les ONZE unites de reference doivent TOUTES etre la. Les surnumeraires
# sont les residus des executions precedentes : on les compte et on les
# signale, sans en faire un echec.
Verifier 'B03' 'les 11 unites de reference sont toutes presentes' 200 ([pscustomobject]@{ Code = 200; Corps = $CHEPTEL; Texte = '' }) {
    param($c)
    $reference = @('LOT-003','LOT-006','LOT-009','LOT-014','BOV-009','BOV-047','BOV-051',
                   'OVI-022','OVI-023','CAP-011','CAP-012')
    $manquants = @()
    foreach ($code in $reference) {
        if (-not ($c | Where-Object { $_.code -eq $code })) { $manquants += $code }
    }
    if ($manquants.Count) { return "unite(s) de reference absente(s) : $($manquants -join ', ')" }
    $null
} | Out-Null
if ($CHEPTEL.Count -gt 11) {
    Noter ("{0} unites au total : {1} residu(s) d executions precedentes du script" -f `
        $CHEPTEL.Count, ($CHEPTEL.Count - 11))
}

Verifier 'B04' 'volaille active : LOT-014 180, LOT-009 60, LOT-006 80' 200 (Invoke-Api GET '/api/cheptel?filiere=VOLAILLE&statut=ACTIF' $null $JE) {
    param($c)
    $pb = @()
    foreach ($paire in @(@('LOT-014',180), @('LOT-009',60), @('LOT-006',80))) {
        $u = $c | Where-Object { $_.code -eq $paire[0] } | Select-Object -First 1
        if (-not $u) { $pb += "$($paire[0]) absent" }
        elseif ($u.effectifRestant -ne $paire[1]) { $pb += "$($paire[0]) a $($u.effectifRestant) tetes au lieu de $($paire[1])" }
    }
    foreach ($u in $c) {
        if ($u.filiere -ne 'VOLAILLE') { $pb += "$($u.code) est en $($u.filiere) dans un filtre VOLAILLE" }
        if ($u.statut -ne 'ACTIF') { $pb += "$($u.code) est $($u.statut) dans un filtre ACTIF" }
    }
    if ($pb.Count) { return ($pb -join ' ; ') }
    $null
} | Out-Null

Verifier 'B05' 'cheptel cloture : LOT-003 en fait partie' 200 (Invoke-Api GET '/api/cheptel?statut=CLOTURE' $null $JE) {
    param($c)
    if (-not ($c | Where-Object { $_.code -eq 'LOT-003' })) { return 'LOT-003 absent des unites cloturees' }
    foreach ($u in $c) { if ($u.statut -ne 'CLOTURE') { return "$($u.code) est $($u.statut) dans un filtre CLOTURE" } }
    $null
} | Out-Null

Verifier 'B06' 'cheptel bovins, dont BOV-009 vendu' 200 (Invoke-Api GET '/api/cheptel?filiere=BOVINS' $null $JE) {
    param($c)
    $v = $c | Where-Object { $_.code -eq 'BOV-009' } | Select-Object -First 1
    if (-not $v) { return 'BOV-009 absent' }
    if ($v.statut -ne 'VENDU') { return "BOV-009 est $($v.statut) au lieu de VENDU" }
    $null
} | Out-Null

if ($LOT014) {
    Verifier 'B07' 'fiche de LOT-014 : mouvements, carnet, rappels, rentabilite' 200 (Invoke-Api GET "/api/cheptel/$($LOT014.id)" $null $JE) {
        param($c)
        $pb = @()
        if (-not $c.unite)      { $pb += 'aucune unite' }
        if ($null -eq $c.mouvements) { $pb += 'aucun tableau de mouvements' }
        if ($null -eq $c.evenements) { $pb += 'aucun carnet sanitaire' }
        if ($null -eq $c.rappels)    { $pb += 'aucun tableau de rappels' }
        if (-not $c.rentabilite)     { $pb += 'aucune rentabilite' }
        if ($pb.Count) { return ($pb -join ' ; ') }
        $null
    } | Out-Null
} else { Ignorer 'B07' 'fiche de LOT-014' 'LOT-014 introuvable dans le cheptel' }

Verifier 'B08' 'unite inexistante' 404 (Invoke-Api GET '/api/cheptel/999999' $null $JE) | Out-Null

# --- creations, sur des codes jetables ------------------------------
$TV = $null; if ($TYPE.ContainsKey('VOLAILLE')) { $TV = $TYPE['VOLAILLE'].id }
$TB = $null; if ($TYPE.ContainsKey('BOVINS'))   { $TB = $TYPE['BOVINS'].id }
$TC = $null; if ($TYPE.ContainsKey('CAPRINS'))  { $TC = $TYPE['CAPRINS'].id }
if (-not $TV -and $LOT014) {
    # Repli : sans jeton admin on n'a pas la liste des types. On prend
    # celui de LOT-014 via sa fiche, plutot que de deviner un entier.
    $f = (Invoke-Api GET "/api/cheptel/$($LOT014.id)" $null $JE).Corps
    if ($f) { Noter 'types d elevage non listes (pas de jeton admin) : certains scenarios seront ignores' }
}

$CODE_T = "TST-$MARQUE"
$ID_LOT_T = $null
if ($TV) {
    $corps = @{ code = $CODE_T; typeElevageId = $TV; effectifInitial = 50
                dateMiseEnPlace = (Get-Date).AddDays(-10).ToString('yyyy-MM-dd') }
    $r = Invoke-Api POST '/api/cheptel/lots' $corps $JE
    $ok = Verifier 'B09' 'creation d un lot jetable' 201 $r {
        param($c)
        if ($c.statut -ne 'ACTIF') { return "statut $($c.statut) au lieu de ACTIF" }
        if ($c.effectifRestant -ne 50) { return "effectifRestant $($c.effectifRestant) au lieu de 50" }
        if ($c.filiere -ne 'VOLAILLE') { return "filiere $($c.filiere)" }
        $null
    }
    if ($ok) { $ID_LOT_T = $r.Corps.id }

    Verifier 'B10' 'meme code rejoue' 409 (Invoke-Api POST '/api/cheptel/lots' $corps $JE) | Out-Null
    $z = $corps.Clone(); $z.code = "ZER-$MARQUE"; $z.effectifInitial = 0
    Verifier 'B11' 'effectif a zero' 400 (Invoke-Api POST '/api/cheptel/lots' $z $JE) | Out-Null
    $t9 = $corps.Clone(); $t9.code = "T9-$MARQUE"; $t9.typeElevageId = 999999
    Verifier 'B12' 'type d elevage inexistant' 404 (Invoke-Api POST '/api/cheptel/lots' $t9 $JE) | Out-Null

    $sc = @{ typeElevageId = $TV; effectifInitial = 20
             dateMiseEnPlace = (Get-Date).AddDays(-5).ToString('yyyy-MM-dd') }
    $rs = Invoke-Api POST '/api/cheptel/lots' $sc $JE
    Verifier 'B13' 'lot sans code : un code est engendre' 201 $rs {
        param($c) if (-not $c.code) { 'aucun code engendre' } else { $null } }  | Out-Null
    $ID_LOT_VIDE = $null; if ($rs.Code -eq 201) { $ID_LOT_VIDE = $rs.Corps.id }
} else {
    foreach ($n in 'B09','B10','B11','B12','B13') { Ignorer $n 'creation de lot' 'type d elevage VOLAILLE inconnu (jeton admin absent)' }
    $ID_LOT_VIDE = $null
}

if ($TC) {
    $a = @{ code = "CAP-$MARQUE"; typeElevageId = $TC
            dateEntree = (Get-Date).AddDays(-10).ToString('yyyy-MM-dd')
            race = 'Sahelienne'; sexe = 'FEMELLE' }
    Verifier 'B14' 'creation d un animal individuel' 201 (Invoke-Api POST '/api/cheptel/animaux' $a $JE) {
        param($c) if ($c.modeSuivi -ne 'INDIVIDUEL') { "modeSuivi $($c.modeSuivi)" } else { $null } } | Out-Null
} else { Ignorer 'B14' 'creation d un animal' 'type d elevage CAPRINS inconnu' }

# --- les regles de sortie, sur le lot jetable de 50 tetes -----------
if ($ID_LOT_T) {
    # AUJOURD HUI, et non la veille. Une vente datee de la veille tombe
    # dans le MOIS PRECEDENT si le script est lance un 1er du mois : le
    # solde du mois courant ne bougerait pas, et B20b signalerait un
    # ecart imaginaire. C'est exactement le piege que l en-tete denonce.
    $hier = (Get-Date).ToString('yyyy-MM-dd')
    Verifier 'B15' 'vendre plus de tetes qu il n en reste' 422 (Invoke-Api POST "/api/cheptel/$ID_LOT_T/sorties" @{
        type = 'VENTE'; nombreTetes = 200; date = $hier; montant = 150000 } $JE) {
        param($c) if ($c.message -notmatch '50') { "le message ne cite pas l effectif reel : [$($c.message)]" } else { $null } } | Out-Null
    Verifier 'B16' 'zero tete' 400 (Invoke-Api POST "/api/cheptel/$ID_LOT_T/sorties" @{
        type = 'VENTE'; nombreTetes = 0; date = $hier; montant = 1000 } $JE) | Out-Null
    Verifier 'B17' 'un motif d entree n est pas une sortie' 422 (Invoke-Api POST "/api/cheptel/$ID_LOT_T/sorties" @{
        type = 'MISE_EN_PLACE'; nombreTetes = 10; date = $hier } $JE) | Out-Null
    Verifier 'B18' 'seule une vente porte un montant' 422 (Invoke-Api POST "/api/cheptel/$ID_LOT_T/sorties" @{
        type = 'MORT'; nombreTetes = 5; date = $hier; montant = 1000 } $JE) | Out-Null
    Verifier 'B19' 'une vente sans montant' 422 (Invoke-Api POST "/api/cheptel/$ID_LOT_T/sorties" @{
        type = 'VENTE'; nombreTetes = 20; date = $hier } $JE) | Out-Null

    # B20 : la vente doit engendrer la recette SANS saisie manuelle.
    $avant = (Invoke-Api GET '/api/finances/solde' $null $JE).Corps
    $r = Invoke-Api POST "/api/cheptel/$ID_LOT_T/sorties" @{
        type = 'VENTE'; nombreTetes = 20; date = $hier; montant = 67200; motif = 'Vente au marche' } $JE
    Verifier 'B20' 'vente de 20 tetes : 50 -> 30, statut ACTIF' 201 $r {
        param($c) if ($c.effectifRestant -ne 30) { "effectifRestant $($c.effectifRestant) au lieu de 30" }
                  elseif ($c.statut -ne 'ACTIF') { "statut $($c.statut) au lieu de ACTIF" } else { $null } } | Out-Null
    $apres = (Invoke-Api GET '/api/finances/solde' $null $JE).Corps
    Verifier 'B20b' 'la vente a engendre 67 200 de recette, sans saisie' 200 ([pscustomobject]@{ Code = 200; Corps = $apres; Texte = '' }) {
        param($c)
        $ecart = $c.revenus - $avant.revenus
        if ($ecart -ne 67200) { return "les revenus ont bouge de $ecart au lieu de 67200" }
        $null
    } | Out-Null

    $r = Invoke-Api POST "/api/cheptel/$ID_LOT_T/sorties" @{
        type = 'VENTE'; nombreTetes = 30; date = $hier; montant = 100000 } $JE
    Verifier 'B21' 'vente du solde : 0 tete, statut CLOTURE (pas VENDU)' 201 $r {
        param($c) if ($c.effectifRestant -ne 0) { "effectifRestant $($c.effectifRestant)" }
                  elseif ($c.statut -ne 'CLOTURE') { "statut $($c.statut) au lieu de CLOTURE" } else { $null } } | Out-Null
    Verifier 'B22' 'une sortie de plus sur un lot clos' 422 (Invoke-Api POST "/api/cheptel/$ID_LOT_T/sorties" @{
        type = 'VENTE'; nombreTetes = 1; date = $hier; montant = 100 } $JE) | Out-Null
} else {
    foreach ($n in 'B15','B16','B17','B18','B19','B20','B20b','B21','B22') { Ignorer $n 'regles de sortie' 'B09 a echoue : pas de lot jetable' }
}

# =====================================================================
Bloc 'C - Finances'

$r = Invoke-Api GET '/api/finances/solde' $null $JE
Verifier 'C01' 'solde du mois, toutes filieres' 200 $r {
    param($c) if ($c.solde -ne ($c.revenus - $c.depenses)) { "invariant rompu : $($c.solde) <> $($c.revenus) - $($c.depenses)" } else { $null } } | Out-Null
$soldeTout = $r.Corps

Verifier 'C02' 'solde filtre VOLAILLE : au plus le total' 200 (Invoke-Api GET '/api/finances/solde?filiere=VOLAILLE' $null $JE) {
    param($c)
    if ($c.solde -ne ($c.revenus - $c.depenses)) { return 'invariant rompu' }
    if ($soldeTout -and $c.revenus -gt $soldeTout.revenus) { return "les revenus volaille ($($c.revenus)) depassent le total ($($soldeTout.revenus))" }
    $null
} | Out-Null

$moisDernier = (Get-Date).AddMonths(-1).ToString('yyyy-MM-01')
Verifier 'C03' 'solde du mois precedent' 200 (Invoke-Api GET "/api/finances/solde?mois=$moisDernier" $null $JE) {
    param($c) if ($c.solde -ne ($c.revenus - $c.depenses)) { 'invariant rompu' } else { $null } } | Out-Null

$d1 = (Get-Date).AddDays(-60).ToString('yyyy-MM-dd')
$d2 = (Get-Date).ToString('yyyy-MM-dd')
Verifier 'C04' 'journal des operations sur 60 jours' 200 (Invoke-Api GET "/api/finances/operations?debut=$d1&fin=$d2" $null $JE) {
    param($c) if ($null -eq $c) { 'aucune reponse' } else { $null } } | Out-Null
Verifier 'C05' 'intervalle inverse : une erreur, pas un resultat vide' 422 (Invoke-Api GET "/api/finances/operations?debut=$d2&fin=$d1" $null $JE) | Out-Null
Verifier 'C06' 'parametre debut absent' 400 (Invoke-Api GET "/api/finances/operations?fin=$d2" $null $JE) | Out-Null

$avantC = (Invoke-Api GET '/api/finances/solde' $null $JE).Corps
$op = @{ type = 'DEPENSE'; categorie = 'ALIMENT'; montant = 12000
         dateOperation = (Get-Date).ToString('yyyy-MM-dd'); filiere = 'VOLAILLE'
         libelle = "Sac de mais 50 kg $MARQUE" }
$r = Invoke-Api POST '/api/finances/operations' $op $JE
$opOk = Verifier 'C07' 'saisie d une depense' 201 $r {
    param($c)
    if ($c.montant -ne 12000) { return "montant $($c.montant)" }
    if ($c.montantSigne -ne -12000) { return "montantSigne $($c.montantSigne) : une depense doit etre negative" }
    if ($c.issueDUnMouvement) { return 'issueDUnMouvement devrait etre faux pour une saisie manuelle' }
    $null
}
$ID_OP = $null; if ($opOk) { $ID_OP = $r.Corps.id }

$f = $op.Clone(); $f.dateOperation = (Get-Date).AddYears(1).ToString('yyyy-MM-dd')
Verifier 'C08' 'date future' 422 (Invoke-Api POST '/api/finances/operations' $f $JE) | Out-Null
$z = $op.Clone(); $z.montant = 0
Verifier 'C09' 'montant a zero' 400 (Invoke-Api POST '/api/finances/operations' $z $JE) | Out-Null

if ($ID_OP) {
    Verifier 'C10' 'suppression de la depense' 204 (Invoke-Api DELETE "/api/finances/operations/$ID_OP" $null $JE) | Out-Null
    $apresC = (Invoke-Api GET '/api/finances/solde' $null $JE).Corps
    Verifier 'C10b' 'le solde revient a sa valeur d avant' 200 ([pscustomobject]@{ Code = 200; Corps = $apresC; Texte = '' }) {
        param($c) if ($c.depenses -ne $avantC.depenses) { "depenses $($c.depenses) au lieu de $($avantC.depenses)" } else { $null } } | Out-Null
} else { Ignorer 'C10' 'suppression de la depense' 'C07 a echoue' ; Ignorer 'C10b' 'retour du solde' 'C07 a echoue' }

# C11 : une recette nee d'une vente ne se supprime pas seule.
$ID_OP_VENTE = $null
if ($ID_LOT_T) {
    $journalOperations = (Invoke-Api GET "/api/finances/operations?debut=$d1&fin=$d2" $null $JE).Corps
    $v = $journalOperations | Where-Object { $_.issueDUnMouvement -and $_.uniteId -eq $ID_LOT_T } | Select-Object -First 1
    if ($v) { $ID_OP_VENTE = $v.id }
}
if ($ID_OP_VENTE) {
    Verifier 'C11' 'supprimer une recette issue d une vente' 422 (Invoke-Api DELETE "/api/finances/operations/$ID_OP_VENTE" $null $JE) | Out-Null
} else { Ignorer 'C11' 'supprimer une recette issue d une vente' 'aucune operation issue d un mouvement trouvee' }

Verifier 'C12' 'rapport : courbe mensuelle et repartition' 200 (Invoke-Api GET "/api/finances/rapport?debut=$d1&fin=$d2" $null $JE) {
    param($c)
    if (-not $c.total) { return 'aucun total' }
    if ($null -eq $c.evolution) { return 'aucune courbe' }
    if ($null -eq $c.repartitionDepenses) { return 'aucune repartition' }
    $null
} | Out-Null

if ($LOT014) {
    Verifier 'C13' 'rentabilite de LOT-014' 200 (Invoke-Api GET "/api/finances/rentabilite/$($LOT014.id)" $null $JE) {
        param($c) if ($c.solde -ne ($c.revenus - $c.depenses)) { 'invariant rompu' } else { $null } } | Out-Null
} else { Ignorer 'C13' 'rentabilite' 'LOT-014 introuvable' }

# =====================================================================
Bloc 'D - Sanitaire, signalement, diagnostic'

$r = Invoke-Api GET '/api/sanitaire/rappels' $null $JE
Verifier 'D01' 'rappels de l eleveur' 200 $r {
    param($c)
    if ($null -eq $c) { return 'aucune reponse' }
    if ($c.Count -lt 1) { return 'aucun rappel : le jeu de demonstration en porte un (Newcastle)' }
    $null
} | Out-Null
if ($r.Code -eq 200) { Noter ("{0} rappel(s) dans l horizon par defaut" -f $r.Corps.Count) }

$r90 = Invoke-Api GET '/api/sanitaire/rappels?horizonJours=90' $null $JE
Verifier 'D02' 'horizon a 90 jours : au moins autant de rappels' 200 $r90 {
    param($c)
    if ($r.Code -eq 200 -and $c.Count -lt $r.Corps.Count) {
        return "$($c.Count) rappels a 90 jours contre $($r.Corps.Count) par defaut : un horizon plus large ne peut pas en rendre moins"
    }
    $null
} | Out-Null

# D03 : un evenement sanitaire sur le LOT JETABLE, pas sur LOT-014.
$ID_EVT = $null; $ID_RAPPEL_NE = $null
if ($ID_LOT_VIDE) {
    $ev = @{ type = 'VACCINATION'; dateEvenement = (Get-Date).ToString('yyyy-MM-dd')
             produitObservation = 'Vaccin Newcastle HB1'; rappelDansJours = 21 }
    # On releve les rappels AVANT de poser l'evenement, pour reconnaitre
    # ensuite celui qui vient de naitre.
    #
    # La premiere version prenait simplement le premier rappel du lot. Elle
    # a signale un faux ecart le 4 octobre : la creation d'un lot declenche
    # DEJA les rappels du protocole sanitaire de son type d'elevage. Le
    # premier rappel du lot etait donc celui du protocole, a J+21 de la mise
    # en place, et non celui de ma vaccination a J+21 d'aujourd'hui. Cinq
    # jours d'ecart, et le back avait raison.
    $avantRappels = (Invoke-Api GET '/api/sanitaire/rappels?horizonJours=120' $null $JE).Corps
    $idsAvant = @()
    if ($avantRappels) { $idsAvant = @($avantRappels | ForEach-Object { $_.id }) }

    $r = Invoke-Api POST "/api/sanitaire/unites/$ID_LOT_VIDE/evenements" $ev $JE
    $ok = Verifier 'D03' 'vaccination + rappel a J+21' 201 $r {
        param($c) if ($c.type -ne 'VACCINATION') { "type $($c.type)" } else { $null } }
    if ($ok) { $ID_EVT = $r.Corps.id }

    $tous = (Invoke-Api GET '/api/sanitaire/rappels?horizonJours=120' $null $JE).Corps
    $n = $tous | Where-Object { $_.uniteId -eq $ID_LOT_VIDE -and $idsAvant -notcontains $_.id } |
         Select-Object -First 1
    Verifier 'D04' 'le rappel engendre figure dans la liste' 200 ([pscustomobject]@{ Code = 200; Corps = $n; Texte = '' }) {
        param($c)
        if (-not $c) { return 'aucun rappel sur le lot qui vient d etre vaccine' }
        $attendu = (Get-Date).AddDays(21).ToString('yyyy-MM-dd')
        $obtenu  = ([string]$c.dateEcheance).Substring(0,10)
        if ($obtenu -ne $attendu) { return "echeance $obtenu au lieu de $attendu" }
        $null
    } | Out-Null
    if ($n) { $ID_RAPPEL_NE = $n.id }

    if ($ID_RAPPEL_NE) {
        Verifier 'D05' 'ignorer le rappel' 204 (Invoke-Api POST "/api/sanitaire/rappels/$ID_RAPPEL_NE/ignorer" $null $JE) | Out-Null
        $apres = (Invoke-Api GET '/api/sanitaire/rappels?horizonJours=90' $null $JE).Corps
        $encore = $apres | Where-Object { $_.id -eq $ID_RAPPEL_NE -and $_.statut -ne 'IGNORE' }
        Verifier 'D05b' 'le rappel ignore disparait de la liste active' 200 ([pscustomobject]@{ Code = 200; Corps = $encore; Texte = '' }) {
            param($c) if ($c) { 'le rappel est encore actif apres avoir ete ignore' } else { $null } } | Out-Null
    } else { Ignorer 'D05' 'ignorer un rappel' 'D04 n a pas trouve le rappel' ; Ignorer 'D05b' 'disparition du rappel' 'D04 a echoue' }

    Verifier 'D06' 'evenement sans type' 400 (Invoke-Api POST "/api/sanitaire/unites/$ID_LOT_VIDE/evenements" @{
        dateEvenement = (Get-Date).ToString('yyyy-MM-dd'); produitObservation = 'x' } $JE) | Out-Null
} else {
    foreach ($n in 'D03','D04','D05','D05b','D06') { Ignorer $n 'carnet sanitaire' 'B13 a echoue : pas de lot jetable vierge' }
}

Verifier 'D07' 'symptomes volaille : 10, dans l ordre de la maquette' 200 ([pscustomobject]@{ Code = 200; Corps = $SYMPTOMES; Texte = '' }) {
    param($c)
    # Libelles accentues depuis V12. Accents composes par code : ce fichier
    # reste en ASCII pur (PowerShell 5.1 lit un fichier sans BOM en ANSI).
    # Noms distincts : PowerShell ignore la casse des noms de variables.
    $eAigu = [char]0x00E9; $aCirc = [char]0x00E2; $eMajAigu = [char]0x00C9
    $attendu = @('Toux',"Diarrh${eAigu}e verd${aCirc}tre","Diarrh${eAigu}e avec sang",'Torticolis','Abattement',
                 'Chute de ponte','Morts subites',"Perte d'app${eAigu}tit","Diarrh${eAigu}e","${eMajAigu}coulement nasal")
    if ($c.Count -ne 10) { return "$($c.Count) symptomes au lieu de 10" }
    for ($i = 0; $i -lt 10; $i++) {
        if ($c[$i].libelle -ne $attendu[$i]) {
            return "position $($i+1) : [$($c[$i].libelle)] au lieu de [$($attendu[$i])] - l ordre vient de la colonne ordre, pas du libelle"
        }
    }
    $null
} | Out-Null

$symBov = (Invoke-Api GET '/api/signalements/symptomes?filiere=BOVINS' $null $JE)
Verifier 'D08' 'symptomes bovins : liste differente de la volaille' 200 $symBov {
    param($c)
    if ($c.Count -lt 1) { return 'aucun symptome bovin' }
    $v = ($SYMPTOMES | ForEach-Object { $_.libelle }) -join '|'
    $b = ($c | ForEach-Object { $_.libelle }) -join '|'
    if ($v -eq $b) { return 'la liste bovine est identique a la volaille : le filtre par filiere ne filtre rien' }
    $null
} | Out-Null

Verifier 'D09' 'maladies volaille : Newcastle, Coccidiose, Bronchite' 200 ([pscustomobject]@{ Code = 200; Corps = $MALADIES_V; Texte = '' }) {
    param($c)
    if ($c.Count -ne 3) { return "$($c.Count) maladies au lieu de 3" }
    foreach ($n in 'Newcastle','Coccidiose','Bronchite') {
        if (-not ($c | Where-Object { $_.nom -match $n })) { return "$n absente" }
    }
    $null
} | Out-Null

# --- D10 a D16 : le diagnostic -------------------------------------
$sToux   = Symptome 'Toux'
# 'verd' et non 'verdatre' : depuis V12 le libelle est "verdatre" accentue. Avec
# l'ancien motif, $sVerd restait nul et D10 a D16 etaient IGNORES en silence.
$sVerd   = Symptome 'verd'
$sTorti  = Symptome 'Torticolis'
$sSang   = Symptome 'avec sang'
$sAbat   = Symptome 'Abattement'
$ID_SIGNALEMENT = $null

if ($LOT009 -and $sToux -and $sVerd -and $sTorti) {
    $sig = @{ uniteId = $LOT009.id; symptomeIds = @($sToux, $sVerd, $sTorti)
              trancheEffectif = 'DE_10_A_50'; precision = "Fond du poulailler $MARQUE" }
    $r = Invoke-Api POST '/api/signalements' $sig $JE
    # Bronchite infectieuse n est PLUS proposee en second : elle ne partage
    # que [Toux] avec les trois symptomes coches (couverture 1/3), sous le
    # seuil de credibilite de DiagnosticService (couverture >= 0,5). Cette
    # attente datait d avant ce seuil ; D10 etait ignore depuis V12 et ne
    # l avait pas montre.
    $ok = Verifier 'D10' 'diagnostic : Newcastle probable, Bronchite ecartee (1 symptome sur 3), ELEVE' 201 $r {
        param($c)
        $d = $c.diagnostic
        if (-not $d) { return 'aucun diagnostic dans la reponse' }
        $pb = @()
        if ($d.probable.nom -notmatch 'Newcastle')  { $pb += "probable [$($d.probable.nom)] au lieu de Newcastle" }
        if ($d.alternative -and $d.alternative.nom -match 'Bronchite') { $pb += 'Bronchite proposee en second avec un seul symptome sur trois : le seuil de credibilite ne joue pas' }
        if ($d.gravite -ne 'ELEVE') { $pb += "gravite $($d.gravite) au lieu de ELEVE" }
        if (-not $d.orientationRequise) { $pb += 'orientationRequise devrait etre vrai' }
        if ($pb.Count) { return ($pb -join ' ; ') }
        $null
    }
    if ($ok) { $ID_SIGNALEMENT = $r.Corps.id }
    # Reponse de D10 gardee a part : D10b et D10c reutilisent $r, et D11
    # lisait la reponse de D10c (200, sans diagnostic) au lieu de celle-ci.
    $rD10 = $r

    # D10b, D10c : plus d'orientation automatique. L'eleveur voit TOUS les
    # veterinaires de sa zone competents (ordre aleatoire) et en choisit un.
    if ($ID_SIGNALEMENT -and $JV) {
        $moiV = (Invoke-Api GET '/api/auth/moi' $null $JV).Corps
        $r = Invoke-Api GET "/api/signalements/$ID_SIGNALEMENT/veterinaires" $null $JE
        Verifier 'D10b' 'veterinaires proposes : zone de l eleveur, competents' 200 $r {
            param($c)
            if (-not $c -or @($c).Count -eq 0) { return 'aucun veterinaire propose' }
            if (-not (@($c) | Where-Object { $_.id -eq $moiV.id })) { return 'le veterinaire de demonstration n est pas propose' }
            $null
        } | Out-Null
        $r = Invoke-Api POST "/api/signalements/$ID_SIGNALEMENT/veterinaire/$($moiV.id)" $null $JE
        Verifier 'D10c' 'l eleveur confie le cas au veterinaire choisi' 200 $r {
            param($c)
            if ($c.statut -ne 'ORIENTE') { return "statut $($c.statut) au lieu de ORIENTE" }
            $null
        } | Out-Null
    }

    # Sur un 500, on rapatrie LA TRACE dans le bilan. C'est le seul ecart
    # que je n'ai pas pu diagnostiquer depuis le code : cinq hypotheses
    # ecartees par lecture, et rien pour trancher. La trace est ecrite
    # dans target\erreurs-dev.log depuis le lot precedent ; autant que le
    # script la lise, plutot que de la faire chercher.
    if ($r.Code -eq 500) {
        $trace = TraceRecente
        Noter 'trace du 500 rapatriee dans le bilan, numero D10d'
        $script:Resultats += [pscustomobject]@{
            Numero = 'D10d'; Bloc = $script:BlocCourant
            Intitule = 'TRACE du 500 sur POST /api/signalements'
            Statut = 'ECART'; Detail = $trace
        }
    }

    # D11 : LE controle de securite metier. Newcastle exige un veterinaire,
    # donc son protocole de traitement ne doit PAS sortir du serveur.
    Verifier 'D11' 'traitement MASQUE sur une maladie orientee' 201 $rD10 {
        param($c)
        $t = $c.diagnostic.probable.traitement
        if ($t) { return "le protocole de traitement est renvoye a l eleveur alors que la maladie exige un veterinaire : [$t] - DEFAUT DE SECURITE METIER" }
        $null
    } | Out-Null
} else {
    foreach ($n in 'D10','D11') { Ignorer $n 'diagnostic Newcastle' 'LOT-009 ou un symptome introuvable' }
}

if ($LOT006 -and $sSang -and $sAbat) {
    $r = Invoke-Api POST '/api/signalements' @{ uniteId = $LOT006.id; symptomeIds = @($sSang, $sAbat)
              trancheEffectif = 'MOINS_DE_10'; precision = "Contre-epreuve $MARQUE" } $JE
    Verifier 'D12' 'Coccidiose : traitement RENSEIGNE (contre-epreuve de D11)' 201 $r {
        param($c)
        $d = $c.diagnostic
        if ($d.probable.nom -notmatch 'Coccidiose') { return "probable [$($d.probable.nom)] au lieu de Coccidiose" }
        if (-not $d.probable.traitement) { return 'le traitement est masque alors que cette maladie n exige pas de veterinaire : le masquage est trop large' }
        $null
    } | Out-Null
} else { Ignorer 'D12' 'Coccidiose, traitement renseigne' 'LOT-006 ou un symptome introuvable' }

if ($LOT009) {
    Verifier 'D13' 'symptome inconnu' 422 (Invoke-Api POST '/api/signalements' @{
        uniteId = $LOT009.id; symptomeIds = @(999999); trancheEffectif = 'DE_10_A_50' } $JE) | Out-Null
    Verifier 'D14' 'aucun symptome' 400 (Invoke-Api POST '/api/signalements' @{
        uniteId = $LOT009.id; symptomeIds = @(); trancheEffectif = 'DE_10_A_50' } $JE) | Out-Null
    Verifier 'D15' 'tranche d effectif absente' 400 (Invoke-Api POST '/api/signalements' @{
        uniteId = $LOT009.id; symptomeIds = @($sToux) } $JE) | Out-Null
} else { foreach ($n in 'D13','D14','D15') { Ignorer $n 'refus de signalement' 'LOT-009 introuvable' } }

# D16 : signaler sur l unite d un AUTRE eleveur. On a besoin d un lot
# qui appartienne au compte jetable.
$ID_LOT_ETRANGER = $null
if ($JJ -and $TV) {
    $re = Invoke-Api POST '/api/cheptel/lots' @{ code = "ETR-$MARQUE"; typeElevageId = $TV
            effectifInitial = 10; dateMiseEnPlace = (Get-Date).AddDays(-3).ToString('yyyy-MM-dd') } $JJ
    if ($re.Code -eq 201) { $ID_LOT_ETRANGER = $re.Corps.id }
}
if ($ID_LOT_ETRANGER -and $sToux) {
    Verifier 'D16' 'signaler sur l unite d un autre eleveur' 403 (Invoke-Api POST '/api/signalements' @{
        uniteId = $ID_LOT_ETRANGER; symptomeIds = @($sToux); trancheEffectif = 'MOINS_DE_10' } $JE) | Out-Null
} else { Ignorer 'D16' 'signalement sur une unite etrangere' 'pas de lot appartenant au compte jetable' }

# =====================================================================
Bloc 'E - Investissement et conseils'

# Les chiffres ci-dessous ont ete recalcules sur le referentiel reel
# (table poste_cout), et non repris du plan, qui se trompait sur E01 :
# sans sujetsVises, le simulateur MAXIMISE dans le budget. Il ne rend
# donc pas les chiffres de E05, ou les 30 sujets sont imposes.
$r = Invoke-Api POST '/api/investissement/simulation' @{ budget = 50000; inclureInstallation = $false } $JE
Verifier 'E01' 'simulation 50 000 FCFA : les 4 filieres, volaille 48 914' 200 $r {
    param($c)
    # AU MOINS quatre, et non EXACTEMENT quatre : F24 ajoute un type a
    # chaque execution, et aucune route ne le supprime. Ce qui compte est
    # que les quatre filieres de reference soient toutes chiffrees.
    if ($c.options.Count -lt 4) { return "$($c.options.Count) options, moins que les 4 filieres" }
    foreach ($f in @('VOLAILLE','BOVINS','OVINS','CAPRINS')) {
        if (-not ($c.options | Where-Object { $_.filiere -eq $f })) { return "aucune option $f" }
    }
    $v = $c.options | Where-Object { $_.libelle -eq 'Poulet de chair' } | Select-Object -First 1
    if (-not $v) { return 'le type de reference Poulet de chair est absent de la simulation' }
    $pb = @()
    if ($v.sujetsVises    -ne 31)    { $pb += "sujetsVises $($v.sujetsVises) au lieu de 31" }
    if ($v.sujetsAAcheter -ne 34)    { $pb += "sujetsAAcheter $($v.sujetsAAcheter) au lieu de 34" }
    if ($v.coutCycle      -ne 48914) { $pb += "coutCycle $($v.coutCycle) au lieu de 48914" }
    if ($v.ecartBudget    -ne 1086)  { $pb += "ecartBudget $($v.ecartBudget) au lieu de 1086" }
    if (-not $v.realisable)          { $pb += 'volaille devrait etre realisable' }
    if ($pb.Count) { return ($pb -join ' ; ') }
    $null
} | Out-Null

# E02 : le ticket d entree des filieres hors budget. C'est le correctif
# du 3 octobre : auparavant une filiere inaccessible affichait le seul
# cout de ses forfaits, avec realisable=true, pour ZERO animal - et le
# tri la placait EN PREMIER.
Verifier 'E02' 'hors budget : le manque reel, et realisable=false' 200 $r {
    param($c)
    $pb = @()
    $cap = $c.options | Where-Object { $_.libelle -eq $LIB_CAPRINS } | Select-Object -First 1
    $ovi = $c.options | Where-Object { $_.libelle -eq $LIB_OVINS }   | Select-Object -First 1
    $bov = $c.options | Where-Object { $_.libelle -eq $LIB_BOVINS }  | Select-Object -First 1
    if (-not $cap -or -not $ovi -or -not $bov) { return 'un type de reference est absent de la simulation' }
    if ($cap.coutCycle -ne 48000)   { $pb += "caprins coutCycle $($cap.coutCycle) au lieu de 48000" }
    if (-not $cap.realisable)       { $pb += 'caprins devrait etre realisable' }
    if ($ovi.coutCycle -ne 80000)   { $pb += "ovins coutCycle $($ovi.coutCycle) au lieu de 80000 (ticket d entree pour 1 sujet)" }
    if ($ovi.realisable)            { $pb += 'ovins est annonce realisable avec 50 000 FCFA : le defaut du ticket d entree est de retour' }
    if ($ovi.sujetsAAcheter -lt 1)  { $pb += "ovins sujetsAAcheter $($ovi.sujetsAAcheter) : un cout non nul pour zero animal" }
    if ($bov.coutCycle -ne 260000)  { $pb += "bovins coutCycle $($bov.coutCycle) au lieu de 260000" }
    if ($bov.realisable)            { $pb += 'bovins ne peut pas etre realisable' }
    if ($pb.Count) { return ($pb -join ' ; ') }
    $null
} | Out-Null

Verifier 'E02b' 'tri : les options tenables d abord' 200 $r {
    param($c)
    $vu = $false
    foreach ($o in $c.options) {
        if (-not $o.realisable) { $vu = $true }
        elseif ($vu) { return "[$($o.libelle)] est realisable mais passe apres une option hors budget" }
    }
    $null
} | Out-Null

Verifier 'E03' 'simulation filtree VOLAILLE : volaille uniquement' 200 (Invoke-Api POST '/api/investissement/simulation' @{
    budget = 50000; filiere = 'VOLAILLE'; inclureInstallation = $false } $JE) {
    param($c)
    if ($c.options.Count -lt 1) { return 'aucune option' }
    # Le filtre doit ecarter les AUTRES filieres. Le nombre d'options
    # volaille, lui, depend des types d'essai laisses par F24.
    foreach ($o in $c.options) {
        if ($o.filiere -ne 'VOLAILLE') { return "une option $($o.filiere) dans un filtre VOLAILLE" }
    }
    $null
} | Out-Null

Verifier 'E04' 'budget trop faible' 400 (Invoke-Api POST '/api/investissement/simulation' @{
    budget = 500; inclureInstallation = $false } $JE) | Out-Null

# E05 : la decomposition poste par poste, 30 sujets imposes. C'est
# l ecran [Investir - Detail], celui qu'un jury demandera a voir.
if ($TV) {
    $r5 = Invoke-Api POST "/api/investissement/simulation/$TV" @{
        budget = 50000; sujetsVises = 30; inclureInstallation = $true } $JE
    Verifier 'E05' 'detail volaille 30 sujets : cycle 47 593, installation 26 000' 200 $r5 {
        param($c)
        $o = $c.option
        $pb = @()
        if ($o.sujetsAAcheter   -ne 33)    { $pb += "sujetsAAcheter $($o.sujetsAAcheter) au lieu de 33 (30 x 1,08 arrondi au superieur)" }
        if ($o.coutCycle        -ne 47593) { $pb += "coutCycle $($o.coutCycle) au lieu de 47593" }
        if ($o.coutInstallation -ne 26000) { $pb += "coutInstallation $($o.coutInstallation) au lieu de 26000" }
        if ($o.coutTotal        -ne 73593) { $pb += "coutTotal $($o.coutTotal) au lieu de 73593" }
        if ($o.realisable)                 { $pb += '73 593 ne tient pas dans 50 000 : realisable devrait etre faux' }
        if ($pb.Count) { return ($pb -join ' ; ') }
        $null
    } | Out-Null

    Verifier 'E05b' 'lignes du detail : achat 19 800, aliment 20 493, vaccins 3 300' 200 $r5 {
        param($c)
        $pb = @()
        foreach ($p in @(@('Achat',19800), @('Aliment',20493), @('Vaccins',3300))) {
            $l = $c.postesCycle | Where-Object { $_.libelle -match $p[0] } | Select-Object -First 1
            if (-not $l) { $pb += "ligne [$($p[0])] absente de postesCycle" }
            elseif ($l.montant -ne $p[1]) { $pb += "$($p[0]) : $($l.montant) au lieu de $($p[1])" }
        }
        if ($pb.Count) { return ($pb -join ' ; ') }
        $null
    } | Out-Null
} else { foreach ($n in 'E05','E05b') { Ignorer $n 'detail de la simulation volaille' 'type VOLAILLE inconnu' } }

# E06 : une seule chevre reste UNE chevre. Pas de provision de mortalite
# sur une filiere suivie bete par bete.
if ($TC) {
    Verifier 'E06' 'caprins, 1 sujet vise : 1 sujet a acheter, pas 2' 200 (Invoke-Api POST "/api/investissement/simulation/$TC" @{
        budget = 50000; sujetsVises = 1; inclureInstallation = $false } $JE) {
        param($c)
        if ($c.option.sujetsAAcheter -ne 1) {
            return "sujetsAAcheter $($c.option.sujetsAAcheter) au lieu de 1 : la provision de mortalite ne s applique qu aux bandes"
        }
        if ($c.option.coutCycle -ne 48000) { return "coutCycle $($c.option.coutCycle) au lieu de 48000" }
        $null
    } | Out-Null
} else { Ignorer 'E06' 'caprins, 1 sujet' 'type CAPRINS inconnu' }

Verifier 'E07' 'type d elevage inexistant' 404 (Invoke-Api POST '/api/investissement/simulation/999999' @{
    budget = 50000; inclureInstallation = $false } $JE) | Out-Null

# --- conseils, vus par l eleveur ------------------------------------
$r = Invoke-Api GET '/api/conseils' $null $JE
Verifier 'E08' 'conseils : les publies seulement' 200 $r {
    param($c)
    if ($null -eq $c.elements) { return 'la reponse n est pas paginee (champ elements absent)' }
    $null
} | Out-Null
$NB_PUBLIES = 0; if ($r.Code -eq 200) { $NB_PUBLIES = [int]$r.Corps.total }
Noter "conseils publies : $NB_PUBLIES"

Verifier 'E09' 'conseils filtres OVINS' 200 (Invoke-Api GET '/api/conseils?filiere=OVINS' $null $JE) {
    param($c)
    foreach ($e in $c.elements) { if ($e.filiere -ne 'OVINS') { return "[$($e.titre)] est en $($e.filiere) alors que le filtre demande OVINS" } }
    $null
} | Out-Null

Verifier 'E10' 'conseils filtres par categorie et pagines' 200 (Invoke-Api GET '/api/conseils?categorie=SANTE&page=0' $null $JE) {
    param($c)
    if ($c.page -ne 0) { return "page $($c.page) au lieu de 0" }
    foreach ($e in $c.elements) { if ($e.categorie -ne 'SANTE') { return "[$($e.titre)] est en $($e.categorie)" } }
    $null
} | Out-Null

$premier = $null
if ($r.Code -eq 200 -and $r.Corps.elements.Count -gt 0) { $premier = $r.Corps.elements[0] }
if ($premier) {
    $l1 = (Invoke-Api GET "/api/conseils/$($premier.id)" $null $JE)
    Verifier 'E11' 'lecture d un conseil : le compteur s incremente' 200 $l1 {
        param($c) if (-not $c.contenu) { 'aucun contenu' } else { $null } } | Out-Null
    if ($l1.Code -eq 200) {
        $l2 = (Invoke-Api GET "/api/conseils/$($premier.id)" $null $JE)
        Verifier 'E11b' 'le compteur de lectures a bien monte' 200 $l2 {
            param($c)
            if ($c.nombreLectures -le $l1.Corps.nombreLectures) {
                return "nombreLectures $($c.nombreLectures) apres $($l1.Corps.nombreLectures) : le compteur ne monte pas"
            }
            $null
        } | Out-Null
    } else { Ignorer 'E11b' 'compteur de lectures' 'E11 a echoue' }
} else { Ignorer 'E11' 'lecture d un conseil' 'aucun conseil publie' ; Ignorer 'E11b' 'compteur de lectures' 'aucun conseil publie' }

# E12 : un brouillon ou un conseil en moderation ne doit pas etre
# lisible par un eleveur, sinon la moderation n est qu un affichage.
#
# LE SCENARIO FABRIQUE SA PROPRE CIBLE. La premiere version prenait le
# conseil en moderation du jeu de demonstration - et la deuxieme
# execution l'a trouve vide, parce que la premiere l'avait publie en
# F16. E12, F16 et F16b etaient ignores d'un coup.
#
# Ce conseil-ci est LAISSE en moderation : F16 s'en servira pour la
# publication, et le conseil de F07 sera celui que F15 retire. Chaque
# scenario a desormais sa propre cible.
$ID_EN_MODERATION = $null
if ($JV) {
    $cm = Invoke-Api POST '/api/conseils' @{
        titre = "Rotation des paturages $MARQUE"
        contenu = 'Decoupez la parcelle en quatre et faites tourner le troupeau toutes les trois semaines. La repousse est plus rapide et le parasitisme chute.'
        filiere = 'OVINS'; categorie = 'PRODUCTION' } $JV
    if ($cm.Code -eq 201) {
        $sm = Invoke-Api POST "/api/conseils/$($cm.Corps.id)/soumission" $null $JV
        if ($sm.Code -eq 200) { $ID_EN_MODERATION = $cm.Corps.id }
    }
}
if (-not $ID_EN_MODERATION -and $JA) {
    # Repli : un conseil deja en moderation, s'il en reste un.
    $mod = (Invoke-Api GET '/api/admin/moderation' $null $JA).Corps
    if ($mod -and $mod.Count -gt 0) { $ID_EN_MODERATION = $mod[0].id }
}
if ($ID_EN_MODERATION) {
    Verifier 'E12' 'un conseil en moderation n est pas lisible par un eleveur' 404 (
        Invoke-Api GET "/api/conseils/$ID_EN_MODERATION" $null $JE) | Out-Null
} else { Ignorer 'E12' 'conseil en moderation illisible' 'aucun conseil en moderation et jeton veterinaire absent' }

Verifier 'E13' 'mes-conseils refuse a un eleveur' 403 (Invoke-Api GET '/api/conseils/mes-conseils' $null $JE) | Out-Null
Verifier 'E14' 'rediger un conseil refuse a un eleveur' 403 (Invoke-Api POST '/api/conseils' @{
    titre = 'Essai'; contenu = 'Essai'; filiere = 'VOLAILLE'; categorie = 'SANTE' } $JE) | Out-Null

# =====================================================================
Bloc 'F - Veterinaire et administration'

if ($JV) {
    $r = Invoke-Api GET '/api/signalements/file' $null $JV
    Verifier 'F01' 'file veterinaire : les plus graves d abord' 200 $r {
        param($c)
        if ($null -eq $c.elements) { return 'reponse non paginee (champ elements absent)' }
        $rang = @{ 'ELEVE' = 0; 'MOYEN' = 1; 'FAIBLE' = 2 }
        $precedent = -1
        foreach ($s in $c.elements) {
            $g = 'MOYEN'
            if ($s.diagnostic -and $s.diagnostic.gravite) { $g = [string]$s.diagnostic.gravite }
            $n = $rang[$g]
            if ($null -eq $n) { continue }
            if ($n -lt $precedent) { return "un signalement $g arrive apres un plus benin : le tri par gravite ne tient pas" }
            $precedent = $n
        }
        $null
    } | Out-Null
    Noter ("file : {0} signalement(s)" -f $r.Corps.total)

    Verifier 'F02' 'file filtree NOUVEAU' 200 (Invoke-Api GET '/api/signalements/file?statut=NOUVEAU' $null $JV) {
        param($c)
        foreach ($s in $c.elements) { if ($s.statut -ne 'NOUVEAU') { return "un signalement $($s.statut) dans un filtre NOUVEAU" } }
        $null
    } | Out-Null

    # F03 a F06 : sur LE signalement cree par le script en D10, pas sur
    # celui du jeu de demonstration.
    if ($ID_SIGNALEMENT) {
        $r = Invoke-Api POST "/api/signalements/$ID_SIGNALEMENT/prise-en-charge" $null $JV
        Verifier 'F03' 'prise en charge : statut EN_COURS' 200 $r {
            param($c) if ($c.statut -ne 'EN_COURS') { "statut $($c.statut) au lieu de EN_COURS" } else { $null } } | Out-Null
        Verifier 'F04' 'prise en charge rejouee' @(409,422) (Invoke-Api POST "/api/signalements/$ID_SIGNALEMENT/prise-en-charge" $null $JV) | Out-Null
        Verifier 'F06' 'compte rendu vide' 400 (Invoke-Api POST "/api/signalements/$ID_SIGNALEMENT/cloture" @{ compteRendu = '' } $JV) | Out-Null
        Verifier 'F05' 'cloture : statut TRAITE' 200 (Invoke-Api POST "/api/signalements/$ID_SIGNALEMENT/cloture" @{
            compteRendu = "Vaccination d urgence, isolement du lot, revisite sous 5 jours. $MARQUE" } $JV) {
            param($c)
            if ($c.statut -ne 'TRAITE') { return "statut $($c.statut) au lieu de TRAITE" }
            if (-not $c.compteRendu) { return 'le compte rendu n est pas rendu' }
            if (-not $c.dateTraitement) { return 'dateTraitement non renseignee' }
            $null
        } | Out-Null
    } else {
        foreach ($n in 'F03','F04','F05','F06') { Ignorer $n 'prise en charge et cloture' 'D10 a echoue : aucun signalement cree par le script' }
    }

    # --- redaction d un conseil -------------------------------------
    $conseil = @{ titre = "Preparer la saison seche $MARQUE"
                  contenu = 'Constituez une reserve de fourrage des octobre. Un stock de trois mois evite la soudure de mars, ou le prix du tourteau double.'
                  filiere = 'OVINS'; categorie = 'PRODUCTION' }
    $r = Invoke-Api POST '/api/conseils' $conseil $JV
    $cOk = Verifier 'F07' 'redaction : statut BROUILLON, pas publie' 201 $r {
        param($c) if ($c.statut -ne 'BROUILLON') { "statut $($c.statut) : un veterinaire ne publie pas directement" } else { $null } }
    $ID_CONSEIL = $null; if ($cOk) { $ID_CONSEIL = $r.Corps.id }

    if ($ID_CONSEIL) {
        $m = $conseil.Clone(); $m.titre = "Preparer la saison seche (revu) $MARQUE"
        Verifier 'F08' 'modification du conseil' 200 (Invoke-Api PUT "/api/conseils/$ID_CONSEIL" $m $JV) {
            param($c) if ($c.titre -notmatch 'revu') { "titre [$($c.titre)] : la modification n a pas pris" } else { $null } } | Out-Null
        Verifier 'F09' 'soumission : statut EN_MODERATION' 200 (Invoke-Api POST "/api/conseils/$ID_CONSEIL/soumission" $null $JV) {
            param($c) if ($c.statut -ne 'EN_MODERATION') { "statut $($c.statut) au lieu de EN_MODERATION" } else { $null } } | Out-Null
        Verifier 'F10' 'mes-conseils contient le conseil redige' 200 (Invoke-Api GET '/api/conseils/mes-conseils' $null $JV) {
            param($c) if (-not ($c | Where-Object { $_.id -eq $ID_CONSEIL })) { 'le conseil redige est absent de mes-conseils' } else { $null } } | Out-Null
    } else { foreach ($n in 'F08','F09','F10') { Ignorer $n 'cycle de vie du conseil' 'F07 a echoue' } }

    # F11 : modifier le conseil d un AUTRE veterinaire. On prend un
    # conseil publie du jeu de demonstration : il n est pas de nous.
    $ID_AUTRUI = $null
    $pub = (Invoke-Api GET '/api/conseils' $null $JE).Corps
    $mesIds = @()
    $miens = (Invoke-Api GET '/api/conseils/mes-conseils' $null $JV).Corps
    if ($miens) { $mesIds = $miens | ForEach-Object { $_.id } }
    # Le fil est pagine par 10, du plus recent au plus ancien : chaque
    # passage du plan y ajoute SES conseils publies, qui finissent par
    # occuper toute la premiere page. Mesure du 5 octobre : F11 passait de OK
    # a IGNORE sans que le back ait change. On parcourt donc les pages.
    $pgF11 = 0
    while ($pub -and $pub.elements -and -not $ID_AUTRUI) {
        $a = $pub.elements | Where-Object { $mesIds -notcontains $_.id } | Select-Object -First 1
        if ($a) { $ID_AUTRUI = $a.id; break }
        if ($pub.derniere -or $pgF11 -ge 20) { break }
        $pgF11++
        $pub = (Invoke-Api GET "/api/conseils?page=$pgF11" $null $JE).Corps
    }
    if ($ID_AUTRUI) {
        Verifier 'F11' 'modifier le conseil d un autre veterinaire' 403 (Invoke-Api PUT "/api/conseils/$ID_AUTRUI" @{
            titre = 'Detournement'; contenu = 'Detournement du conseil d autrui, qui doit etre refuse.'
            filiere = 'OVINS'; categorie = 'SANTE' } $JV) | Out-Null
    } else { Ignorer 'F11' 'conseil d autrui' 'aucun conseil publie qui ne soit pas de ce veterinaire' }

    Verifier 'F12' 'tableau de bord eleveur refuse a un veterinaire' 403 (Invoke-Api GET '/api/tableau-de-bord' $null $JV) | Out-Null
} else {
    foreach ($n in 'F01','F02','F03','F04','F05','F06','F07','F08','F09','F10','F11','F12') {
        Ignorer $n 'bloc veterinaire' 'connexion veterinaire impossible'
    }
    $ID_CONSEIL = $null
}

# --- administration -------------------------------------------------
if ($JA) {
    $r = Invoke-Api GET '/api/admin/moderation' $null $JA
    Verifier 'F13' 'file de moderation' 200 $r {
        param($c)
        if ($null -eq $c) { return 'aucune reponse' }
        if ($ID_CONSEIL -and -not ($c | Where-Object { $_.id -eq $ID_CONSEIL })) {
            return 'le conseil soumis en F09 n apparait pas en moderation'
        }
        $null
    } | Out-Null

    if ($ID_CONSEIL) {
        Verifier 'F14' 'retrait sans motif' 422 (Invoke-Api POST "/api/admin/moderation/$ID_CONSEIL" @{ publier = $false } $JA) | Out-Null
        Verifier 'F15' 'retrait motive : statut RETIRE' 200 (Invoke-Api POST "/api/admin/moderation/$ID_CONSEIL" @{
            publier = $false; motif = 'Preciser les doses' } $JA) {
            param($c)
            if ($c.statut -ne 'RETIRE') { return "statut $($c.statut) au lieu de RETIRE" }
            if (-not $c.motifRetrait) { return 'le motif de retrait n est pas conserve' }
            $null
        } | Out-Null
    } else { foreach ($n in 'F14','F15') { Ignorer $n 'moderation' 'F07 a echoue : aucun conseil a moderer' } }

    # F16 : publier. La cible est le conseil que E12 a laisse en
    # moderation pour cela, et non [ce qui traine dans la file] - ce qui
    # dependait de l'etat laisse par l'execution precedente.
    $enMod = (Invoke-Api GET '/api/admin/moderation' $null $JA).Corps
    $aPublier = $null
    if ($ID_EN_MODERATION -and $enMod) {
        $aPublier = $enMod | Where-Object { $_.id -eq $ID_EN_MODERATION -and $_.statut -eq 'EN_MODERATION' } |
                    Select-Object -First 1
    }
    if (-not $aPublier -and $enMod) {
        $aPublier = $enMod | Where-Object { $_.statut -eq 'EN_MODERATION' } | Select-Object -First 1
    }
    if ($aPublier) {
        Verifier 'F16' 'publication : statut PUBLIE' 200 (Invoke-Api POST "/api/admin/moderation/$($aPublier.id)" @{ publier = $true } $JA) {
            param($c) if ($c.statut -ne 'PUBLIE') { "statut $($c.statut) au lieu de PUBLIE" } else { $null } } | Out-Null
        $apres = (Invoke-Api GET '/api/conseils' $null $JE).Corps
        Verifier 'F16b' 'le conseil publie apparait aux eleveurs' 200 ([pscustomobject]@{ Code = 200; Corps = $apres; Texte = '' }) {
            param($c) if (-not ($c.elements | Where-Object { $_.id -eq $aPublier.id })) { 'publie, mais absent de la liste des eleveurs' } else { $null } } | Out-Null
    } else { Ignorer 'F16' 'publication' 'aucun conseil en moderation' ; Ignorer 'F16b' 'visibilite du conseil publie' 'F16 non joue' }

    # --- alertes ----------------------------------------------------
    $newcastle = $null; $coccidiose = $null
    $mal = (Invoke-Api GET '/api/admin/maladies' $null $JA).Corps
    if ($mal) {
        $newcastle  = $mal | Where-Object { $_.nom -match 'Newcastle' }  | Select-Object -First 1
        $coccidiose = $mal | Where-Object { $_.nom -match 'Coccidiose' } | Select-Object -First 1
    }
    if ($newcastle -and $ZONE_ID) {
        Verifier 'F17' 'apercu : informe sans rien declencher' 200 (
            Invoke-Api GET "/api/admin/alertes/apercu?maladieId=$($newcastle.id)&zoneId=$ZONE_ID" $null $JA) {
            param($c)
            if ($null -eq $c.signalementsRecents) { return 'champ signalementsRecents absent' }
            if ($null -eq $c.eleveursCibles)      { return 'champ eleveursCibles absent' }
            $null
        } | Out-Null
        $avantAl = (Invoke-Api GET '/api/admin/alertes' $null $JA).Corps
        Verifier 'F17b' 'l apercu n a cree aucune alerte' 200 ([pscustomobject]@{ Code = 200; Corps = $avantAl; Texte = '' }) {
            param($c) if ($null -eq $c) { 'liste d alertes illisible' } else { $null } } | Out-Null
        Verifier 'F18' 'alerte Newcastle + Koutiala deja active' 409 (Invoke-Api POST '/api/admin/alertes' @{
            maladieId = $newcastle.id; zoneId = $ZONE_ID; filiere = 'VOLAILLE'
            message = 'Doublon, doit etre refuse' } $JA) | Out-Null
    } else { foreach ($n in 'F17','F17b','F18') { Ignorer $n 'apercu et doublon d alerte' 'Newcastle ou la zone introuvable' } }

    $ID_ALERTE = $null
    if ($coccidiose -and $ZONE_ID) {
        $r = Invoke-Api POST '/api/admin/alertes' @{ maladieId = $coccidiose.id; zoneId = $ZONE_ID
                filiere = 'VOLAILLE'; message = "Cas de coccidiose signales. Surveillez les fientes. $MARQUE" } $JA
        $alOk = Verifier 'F19' 'declenchement d une alerte Coccidiose' 201 $r {
            param($c)
            if ($c.statut -ne 'ACTIVE') { return "statut $($c.statut) au lieu de ACTIVE" }
            if ($c.portee -lt 1) { return "portee $($c.portee) : aucun eleveur notifie" }
            $null
        }
        if ($alOk) { $ID_ALERTE = $r.Corps.id }
    } else { Ignorer 'F19' 'declenchement d alerte' 'Coccidiose introuvable' }

    if ($ID_ALERTE) {
        # F20 : l enum est TERMINEE, et non CLOTUREE comme l annoncait
        # le plan. Verifie dans StatutAlerte.
        Verifier 'F20' 'cloture de l alerte : statut TERMINEE' 200 (Invoke-Api POST "/api/admin/alertes/$ID_ALERTE/cloture" $null $JA) {
            param($c) if ($c.statut -ne 'TERMINEE') { "statut $($c.statut) au lieu de TERMINEE" } else { $null } } | Out-Null
    } else { Ignorer 'F20' 'cloture de l alerte' 'F19 a echoue' }

    Verifier 'F21' 'liste complete des alertes' 200 (Invoke-Api GET '/api/admin/alertes' $null $JA) {
        param($c) if ($null -eq $c) { 'aucune reponse' } else { $null } } | Out-Null

    # --- referentiel ------------------------------------------------
    Verifier 'F22' 'types d elevage : les 4, avec leurs postes' 200 (Invoke-Api GET '/api/admin/types-elevage' $null $JA) {
        param($c)
        if ($c.Count -lt 4) { return "$($c.Count) types au lieu de 4 au moins" }
        foreach ($t in $c) { if ($t.postesCout.Count -lt 1) { return "[$($t.libelle)] n a aucun poste de cout" } }
        $null
    } | Out-Null

    if ($TV) {
        Verifier 'F23' 'volaille : capacite 100, mortalite 0,08, 8 postes' 200 (Invoke-Api GET "/api/admin/types-elevage/$TV" $null $JA) {
            param($c)
            $pb = @()
            if ($c.capaciteReference -ne 100) { $pb += "capaciteReference $($c.capaciteReference) au lieu de 100" }
            # Comparaison a tolerance : 0,08 n a pas de representation
            # binaire exacte, et un test d egalite stricte entre un double
            # venu du JSON et un litteral echouerait sans raison.
            if ([math]::Abs([double]$c.tauxMortalite - 0.08) -gt 0.0001) { $pb += "tauxMortalite $($c.tauxMortalite) au lieu de 0,08" }
            if ($c.postesCout.Count -ne 8) { $pb += "$($c.postesCout.Count) postes au lieu de 8" }
            if ($pb.Count) { return ($pb -join ' ; ') }
            $null
        } | Out-Null
    } else { Ignorer 'F23' 'detail du type volaille' 'type VOLAILLE inconnu' }

    $nt = @{ filiere = 'VOLAILLE'; libelle = "Pondeuse d essai $MARQUE"; modeEvaluation = 'PAR_PERIODE'
             dureeCycleJours = 120; capaciteReference = 50; espaceParSujetM2 = 0.15
             alimentParSujetKg = 2.2; eauParSujetJourL = 0.25; vaccinsObligatoires = 'Newcastle'
             tauxMortalite = 0.06; difficulte = 'MOYEN'
             postesCout = @(
                @{ libelle = 'Achat des sujets'; montant = 900; unite = 'sujet'; phase = 'CYCLE'
                   modeQuantite = 'PAR_SUJET'; coefficient = 1.0; ordre = 1 },
                @{ libelle = 'Aliment'; montant = 345; unite = 'kg'; phase = 'CYCLE'
                   modeQuantite = 'PAR_SUJET'; coefficient = 2.2; ordre = 2 }) }
    $r = Invoke-Api POST '/api/admin/types-elevage' $nt $JA
    # Garde pour le menage de fin de bloc J : sans lui, chaque execution
    # laissait ce type ACTIF, propose a l eleveur dans " Ajouter ".
    $ID_TYPE_F24 = $null; if ($r.Code -eq 201) { $ID_TYPE_F24 = $r.Corps.id }
    Verifier 'F24' 'creation d un type d elevage avec ses postes' 201 $r {
        param($c) if ($c.postesCout.Count -ne 2) { "$($c.postesCout.Count) postes au lieu de 2" } else { $null } } | Out-Null

    $sToutes = (Invoke-Api GET '/api/signalements/symptomes?filiere=VOLAILLE' $null $JE).Corps
    $sid = $null; if ($sToutes -and $sToutes.Count -gt 0) { $sid = $sToutes[0].id }

    $mSansFiliere = @{ nom = "Maladie essai $MARQUE"; gravite = 'MOYEN'; filieres = @()
                       symptomesTexte = 'Essai'; orienteVersVeterinaire = $false
                       symptomes = @(@{ symptomeId = $sid; poids = 0.8 }) }
    Verifier 'F25' 'maladie sans filiere' 400 (Invoke-Api POST '/api/admin/maladies' $mSansFiliere $JA) | Out-Null
    $mSansSympt = $mSansFiliere.Clone(); $mSansSympt.filieres = @('VOLAILLE'); $mSansSympt.symptomes = @()
    Verifier 'F26' 'maladie sans ponderation' 400 (Invoke-Api POST '/api/admin/maladies' $mSansSympt $JA) | Out-Null
    $mDoublon = $mSansFiliere.Clone(); $mDoublon.filieres = @('VOLAILLE'); $mDoublon.nom = 'Maladie de Newcastle'
    Verifier 'F27' 'nom de maladie deja pris' 409 (Invoke-Api POST '/api/admin/maladies' $mDoublon $JA) | Out-Null

    # F27b : une creation qui REUSSIT. Le plan n en avait aucune, donc
    # les scenarios I24 et I25 n avaient rien a modifier d autre que
    # Newcastle - ce qui aurait abime le jeu de demonstration.
    $ID_MALADIE_T = $null
    if ($sid) {
        $mOk = $mSansFiliere.Clone(); $mOk.filieres = @('VOLAILLE')
        $mOk.traitement = 'Isolement, rehydratation, surveillance 72 h.'
        $r = Invoke-Api POST '/api/admin/maladies' $mOk $JA
        $ok = Verifier 'F27b' 'creation d une maladie jetable' 201 $r {
            param($c) if ($c.filieres -notcontains 'VOLAILLE') { 'filiere VOLAILLE absente' } else { $null } }
        if ($ok) { $ID_MALADIE_T = $r.Corps.id }
    } else { Ignorer 'F27b' 'creation d une maladie jetable' 'aucun symptome disponible pour la ponderer' }

    # F28 : en profil dev, le repli console n echoue JAMAIS. La creation
    # reussit donc, contrairement a ce qu annoncait le plan, ecrit pour
    # une configuration sans courriel du tout.
    $vet = @{ nom = "Dr Essai $MARQUE"; telephone = '79000000'; email = "veto-$MARQUE@example.ml"
              zoneId = $ZONE_ID; zoneInterventionId = $ZONE_ID
              titre = 'Docteur'; specialite = 'Volaille' }
    $r = Invoke-Api POST '/api/admin/veterinaires' $vet $JA
    $ID_VETO_T = $null
    $ok = Verifier 'F28' 'creation d un veterinaire (repli console actif)' 201 $r {
        param($c) if (-not $c.id) { 'aucun identifiant rendu' } else { $null } }
    if ($ok) {
        $ID_VETO_T = $r.Corps.id
        Noter 'le mot de passe provisoire est affiche dans la console du serveur'
    }
} else {
    foreach ($n in 'F13','F14','F15','F16','F16b','F17','F17b','F18','F19','F20','F21','F22','F23','F24','F25','F26','F27','F27b','F28') {
        Ignorer $n 'bloc administration' 'connexion administrateur impossible'
    }
    $ID_MALADIE_T = $null; $ID_VETO_T = $null; $ID_ALERTE = $null
}

# =====================================================================
Bloc 'G - Securite : ce qui doit etre refuse'

Verifier 'G01' 'tableau de bord sans jeton' 401 (Invoke-Api GET '/api/tableau-de-bord') | Out-Null
Verifier 'G02' 'zones : route ouverte (le formulaire d inscription en a besoin)' 200 (Invoke-Api GET '/api/zones') {
    param($c) if ($c.Count -lt 1) { 'aucune zone' } else { $null } } | Out-Null
Verifier 'G03' 'cheptel sans jeton' 401 (Invoke-Api GET '/api/cheptel') | Out-Null
Verifier 'G04' 'connexion : il faut bien pouvoir entrer' 200 (Invoke-Api POST '/api/auth/connexion' @{
    email = $ELEVEUR_MAIL; motDePasse = $ELEVEUR_MDP }) | Out-Null

# G05 : un caractere change AU MILIEU de la charge utile. Le jeton reste
# bien forme, seule la signature ne colle plus.
$morceaux = $JE -split '\.'
$charge = $morceaux[1]
$milieu = [int]($charge.Length / 2)
$c0 = $charge[$milieu]
$c1 = 'A'; if ($c0 -eq 'A') { $c1 = 'B' }
$chargeAbimee = $charge.Substring(0, $milieu) + $c1 + $charge.Substring($milieu + 1)
$jetonAbime = $morceaux[0] + '.' + $chargeAbimee + '.' + $morceaux[2]
Verifier 'G05' 'jeton deforme : signature invalide' 401 (Invoke-Api GET '/api/tableau-de-bord' $null $jetonAbime) | Out-Null

# G06 : prefixe Bearer en double. On passe l en-tete a la main, puisque
# Invoke-Api ajoute deja [Bearer].
Verifier 'G06' 'prefixe Bearer en double' 401 (Invoke-Api GET '/api/tableau-de-bord' $null $null @{
    'Authorization' = "Bearer Bearer $JE" }) | Out-Null

# G07 : un jeton de RAFRAICHISSEMENT presente la ou un jeton d acces est
# attendu. Les deux sont signes par la meme cle : seul le type les
# distingue, et il doit etre verifie.
if ($RAFRAICHIR_E) {
    Verifier 'G07' 'jeton de rafraichissement presente comme jeton d acces' 401 (
        Invoke-Api GET '/api/tableau-de-bord' $null $RAFRAICHIR_E) | Out-Null
} else { Ignorer 'G07' 'mauvais type de jeton' 'aucun jeton de rafraichissement recupere' }

Verifier 'G08' 'moderation refusee a un eleveur' 403 (Invoke-Api GET '/api/admin/moderation' $null $JE) | Out-Null
Verifier 'G09' 'declencher une alerte, en eleveur' 403 (Invoke-Api POST '/api/admin/alertes' @{
    maladieId = 1; zoneId = 1; filiere = 'VOLAILLE'; message = 'Doit etre refuse' } $JE) | Out-Null
Verifier 'G10' 'file veterinaire refusee a un eleveur' 403 (Invoke-Api GET '/api/signalements/file' $null $JE) | Out-Null
Verifier 'G11' 'rediger un conseil, en eleveur' 403 (Invoke-Api POST '/api/conseils' @{
    titre = 'Essai'; contenu = 'Essai'; filiere = 'VOLAILLE'; categorie = 'SANTE' } $JE) | Out-Null

# G12 a G14 : LE scenario decisif. Le lot cree par le compte jetable
# n appartient pas a Balla Kone. Le controle doit etre a CHAQUE point
# d entree, pas seulement sur la lecture.
if ($ID_LOT_ETRANGER) {
    Verifier 'G12' 'lire l unite d un autre eleveur' 403 (Invoke-Api GET "/api/cheptel/$ID_LOT_ETRANGER" $null $JE) | Out-Null
    Verifier 'G13' 'sortir des tetes de l unite d un autre' 403 (Invoke-Api POST "/api/cheptel/$ID_LOT_ETRANGER/sorties" @{
        type = 'VENTE'; nombreTetes = 1; date = (Get-Date).AddDays(-1).ToString('yyyy-MM-dd'); montant = 1000 } $JE) | Out-Null
    Verifier 'G14' 'rentabilite de l unite d un autre' 403 (Invoke-Api GET "/api/finances/rentabilite/$ID_LOT_ETRANGER" $null $JE) | Out-Null
    Verifier 'G15' 'carnet sanitaire de l unite d un autre' 403 (Invoke-Api POST "/api/sanitaire/unites/$ID_LOT_ETRANGER/evenements" @{
        type = 'VACCINATION'; dateEvenement = (Get-Date).ToString('yyyy-MM-dd'); produitObservation = 'Intrusion' } $JE) | Out-Null
    Verifier 'G16' 'modifier l unite d un autre' 403 (Invoke-Api PUT "/api/cheptel/$ID_LOT_ETRANGER" @{
        code = "VOL-$MARQUE"; typeElevageId = $TV; dateEntree = (Get-Date).AddDays(-3).ToString('yyyy-MM-dd') } $JE) | Out-Null
    Verifier 'G17' 'supprimer l unite d un autre' 403 (Invoke-Api DELETE "/api/cheptel/$ID_LOT_ETRANGER" $null $JE) | Out-Null
} else {
    foreach ($n in 'G12','G13','G14','G15','G16','G17') { Ignorer $n 'cloisonnement entre eleveurs' 'pas de lot appartenant au compte jetable' }
}

# =====================================================================
Bloc 'H - Mot de passe oublie et comptes'
#
#  ORDRE IMPOSE. Deux contraintes :
#    1. le plafond est de TROIS demandes par heure et par compte. Le
#       succes passe donc AVANT les refus - sinon le quota est epuise
#       avant d avoir prouve que le parcours fonctionne, et il faut
#       attendre une heure ;
#    2. reinitialiser CHANGE le mot de passe. Le faire sur Balla Kone
#       invaliderait le [eleveur2026] dont tout le reste depend.
#  D ou un compte jetable dedie.

$MAIL_R = "reinit-$MARQUE@example.ml"
$MDP_R  = 'jetable2026'
$rOk = $false
$r = Invoke-Api POST '/api/auth/inscription' @{
    nom = "Compte Reinit $MARQUE"; email = $MAIL_R; zoneId = $ZONE_ID
    filieres = @('VOLAILLE'); motDePasse = $MDP_R }
$rOk = Verifier 'H01' 'compte jetable dedie a la reinitialisation' 201 $r {
    param($c) if (-not $c.utilisateurId) { 'aucun identifiant' } else { $null } }
$ID_R = $null; if ($rOk) { $ID_R = $r.Corps.utilisateurId }

if ($rOk) {
    Verifier 'H02' 'demande d oubli : 202, et le code part en console' 202 (
        Invoke-Api POST '/api/auth/mot-de-passe/oublie' @{ email = $MAIL_R }) {
        param($c) if (-not $c.message) { 'aucun message de reponse' } else { $null } } | Out-Null

    # Lu dans le journal des courriels. La saisie a la main n'est plus
    # qu'un repli, pour le cas ou le fichier serait introuvable.
    $CODE = $null
    $brut = ValeurDuJournal 'CODE DE REINITIALISATION' $MAIL_R 'Code :'
    if ($brut -match '(\d{6})') {
        $CODE = $Matches[1]
        Noter ("code lu dans le journal : {0}" -f $CODE)
    }
    elseif (-not $SansPause) {
        Write-Host ''
        Write-Host '  ---------------------------------------------------------------' -ForegroundColor White
        Write-Host '  Journal indisponible : lecture a la main.' -ForegroundColor White
        Write-Host '  Dans la console du serveur, le DERNIER cadre portant' -ForegroundColor White
        Write-Host ("     CODE DE REINITIALISATION   /   A : {0}" -f $MAIL_R) -ForegroundColor DarkGray
        Write-Host '  Attention : il peut y en avoir plusieurs si le serveur tourne' -ForegroundColor White
        Write-Host '  depuis longtemps. Prenez le plus bas dans la console.' -ForegroundColor White
        Write-Host '  ---------------------------------------------------------------' -ForegroundColor White
        $CODE = Demander '   le code a six chiffres (Entree pour sauter) :'
        if ($CODE -notmatch '^\d{6}$') { $CODE = $null }
    }

    if ($CODE) {
        # DEUX TENTATIVES. A la deuxieme execution du 4 octobre, H03 a
        # rendu 422 [Code invalide] et a entraine H04 et H05 avec lui.
        # La cause la plus probable n'est pas le back : la console portait
        # alors plusieurs cadres - le code de H02, celui de H11, le mot de
        # passe provisoire de H20 - et un seul chiffre de travers suffit.
        #
        # Un refus n'est donc plus un echec immediat : on redemande une
        # fois, en rappelant l'adresse exacte du cadre a lire. Si le
        # deuxieme essai echoue aussi, l'ecart est reel et il est signale.
        $rr = Invoke-Api POST '/api/auth/mot-de-passe/reinitialiser' @{
            email = $MAIL_R; code = $CODE; nouveauMotDePasse = 'change2026' }
        if ($rr.Code -eq 422 -and -not $SansPause) {
            Write-Host ''
            Write-Host '   Ce code a ete REFUSE.' -ForegroundColor Yellow
            Write-Host ("   Verifiez que le cadre porte bien  A : {0}" -f $MAIL_R) -ForegroundColor Yellow
            Write-Host '   et non une autre adresse. Il peut y avoir plusieurs cadres.' -ForegroundColor Yellow
            $CODE2 = Demander '   le code, deuxieme essai (Entree pour abandonner) :'
            if ($CODE2 -match '^\d{6}$') {
                $CODE = $CODE2
                $rr = Invoke-Api POST '/api/auth/mot-de-passe/reinitialiser' @{
                    email = $MAIL_R; code = $CODE; nouveauMotDePasse = 'change2026' }
            }
        }
        Verifier 'H03' 'reinitialisation : 204, aucun jeton rendu' 204 $rr {
            param($c) if ($c -and $c.jetonAcces) { 'un jeton est rendu : une route publique ne doit pas connecter' } else { $null } } | Out-Null
        Verifier 'H04' 'le nouveau mot de passe fonctionne' 200 (Invoke-Api POST '/api/auth/connexion' @{
            email = $MAIL_R; motDePasse = 'change2026' }) | Out-Null
        Verifier 'H05' 'l ancien ne fonctionne plus' 422 (Invoke-Api POST '/api/auth/connexion' @{
            email = $MAIL_R; motDePasse = $MDP_R }) | Out-Null
        Verifier 'H06' 'le code est a usage unique' 422 (Invoke-Api POST '/api/auth/mot-de-passe/reinitialiser' @{
            email = $MAIL_R; code = $CODE; nouveauMotDePasse = 'encore2026' }) | Out-Null
        $MDP_R = 'change2026'
    } else {
        foreach ($n in 'H03','H04','H05','H06') {
            AFaireAMain $n 'parcours de reinitialisation' 'demande la lecture du code dans la console du serveur'
        }
    }

    # H07 : LE point du bloc. Une adresse inconnue doit rendre la MEME
    # reponse, au mot pres, et ne rien ecrire en console.
    $connue   = Invoke-Api POST '/api/auth/mot-de-passe/oublie' @{ email = $MAIL_R }
    $inconnue = Invoke-Api POST '/api/auth/mot-de-passe/oublie' @{ email = "personne-$MARQUE@example.ml" }
    Verifier 'H07' 'adresse inconnue : reponse identique au mot pres' 202 $inconnue {
        param($c)
        if ($connue.Code -ne 202) { return "l adresse connue a rendu $($connue.Code), comparaison impossible" }
        if ($c.message -ne $connue.Corps.message) {
            return "messages differents : [$($c.message)] contre [$($connue.Corps.message)] - les comptes sont enumerables"
        }
        $null
    } | Out-Null
    Noter 'verifiez qu AUCUN cadre n est apparu en console pour l adresse inconnue'

    Verifier 'H08' 'adresse mal formee' 400 (Invoke-Api POST '/api/auth/mot-de-passe/oublie' @{ email = 'pas-une-adresse' }) | Out-Null
    Verifier 'H09' 'reinitialiser sur un compte inexistant' 422 (Invoke-Api POST '/api/auth/mot-de-passe/reinitialiser' @{
        email = "personne-$MARQUE@example.ml"; code = '123456'; nouveauMotDePasse = 'peu-importe2026' }) | Out-Null
    Verifier 'H10' 'code a cinq chiffres' 400 (Invoke-Api POST '/api/auth/mot-de-passe/reinitialiser' @{
        email = $MAIL_R; code = '12345'; nouveauMotDePasse = 'peu-importe2026' }) | Out-Null

    # SCENARIO REECRIT LE 4 OCTOBRE. La premiere version envoyait six codes
    # FAUX et attendait que le 6e message change. Elle a signale un ecart, a
    # tort : elle demandait au back de contredire sa propre defense.
    #
    # Le correctif numero 7 du 3 octobre a deplace la comparaison d'empreinte
    # AVANT les tests d'expiration et de plafond, parce que [ce code a expire]
    # prouvait l'existence du compte a qui ne detenait aucun code. Consequence
    # voulue : un code FAUX recoit desormais TOUJOURS le message uniforme. La
    # branche [saisi trop de fois] n'est atteignable que par qui detient le
    # BON code.
    #
    # La protection fonctionne toujours : apres cinq echecs estUtilisable()
    # est faux, et meme le bon code est refuse. C'est CELA qu'il faut mesurer
    # - et cela demande de connaitre le vrai code, donc la console.
    #
    # Compte DEDIE : le plafond est de trois demandes par heure et par
    # compte, et celui de H01 a deja servi.
    $MAIL_P = "plafond-$MARQUE@example.ml"
    $rp = Invoke-Api POST '/api/auth/inscription' @{
        nom = "Compte Plafond $MARQUE"; email = $MAIL_P; zoneId = $ZONE_ID
        filieres = @('VOLAILLE'); motDePasse = 'plafond2026' }
    $pOk = ($rp.Code -eq 201)

    if ($pOk) {
        Verifier 'H11' 'demande de code sur le compte dedie au plafond' 202 (
            Invoke-Api POST '/api/auth/mot-de-passe/oublie' @{ email = $MAIL_P }) | Out-Null
    } else { Ignorer 'H11' 'demande de code pour le plafond' 'creation du compte dedie impossible' }

    $CODE_P = $null
    if ($pOk) {
        $brutP = ValeurDuJournal 'CODE DE REINITIALISATION' $MAIL_P 'Code :'
        if ($brutP -match '(\d{6})') {
            $CODE_P = $Matches[1]
            Noter ("code du compte plafond lu dans le journal : {0}" -f $CODE_P)
        }
        elseif (-not $SansPause) {
            Write-Host ''
            Write-Host '  Journal indisponible. Dans la console du serveur, le dernier' -ForegroundColor White
            Write-Host ("  cadre CODE DE REINITIALISATION pour {0}" -f $MAIL_P) -ForegroundColor White
            $CODE_P = Demander '   le code a six chiffres (Entree pour sauter) :'
            if ($CODE_P -notmatch '^\d{6}$') { $CODE_P = $null }
        }
    }

    if ($pOk) {
        # Cinq essais FAUX. Le message doit rester UNIFORME : c'est la
        # defense contre l'enumeration, et elle doit tenir.
        $messages = @()
        $codes = @()
        for ($i = 1; $i -le 5; $i++) {
            $faux = Invoke-Api POST '/api/auth/mot-de-passe/reinitialiser' @{
                email = $MAIL_P; code = ('90000' + $i); nouveauMotDePasse = 'tentative2026' }
            $m = ''
            if ($faux.Corps -and $faux.Corps.message) { $m = $faux.Corps.message }
            $messages += $m
            $codes += $faux.Code
        }
        Verifier 'H12a' 'cinq codes faux : message UNIFORME, aucune fuite' 422 ([pscustomobject]@{
            Code = $codes[4]; Corps = @{ message = $messages[4] }; Texte = '' }) {
            param($c)
            $autres = @($codes | Where-Object { $_ -ne 422 })
            if ($autres.Count) { return "les cinq appels n ont pas tous rendu 422 : $($codes -join ', ')" }
            $distincts = @($messages | Sort-Object -Unique)
            if ($distincts.Count -ne 1) {
                return "les messages varient : $($distincts -join ' / ') - un code faux ne doit RIEN apprendre"
            }
            $null
        } | Out-Null
        Noter ("message uniforme : [{0}]" -f $messages[0])
    } else { Ignorer 'H12a' 'cinq codes faux, message uniforme' 'compte dedie absent' }

    # H12b : LA mesure du compteur. Le bon code, apres cinq echecs, doit etre
    # refuse avec un message DIFFERENT. Si le compteur etait annule au
    # rollback, le bon code passerait et le mot de passe changerait.
    if ($CODE_P) {
        $bon = Invoke-Api POST '/api/auth/mot-de-passe/reinitialiser' @{
            email = $MAIL_P; code = $CODE_P; nouveauMotDePasse = 'apresplafond2026' }
        Verifier 'H12b' 'le BON code refuse apres cinq echecs : le compteur persiste' 422 $bon {
            param($c)
            if (-not $c.message) { return 'aucun message' }
            if ($c.message -notmatch 'trop de fois') {
                return "message [$($c.message)] au lieu de [Ce code a ete saisi trop de fois]. Si le bon code a ete ACCEPTE, le compteur est annule au rollback - DEFAUT CRITIQUE 2"
            }
            $null
        } | Out-Null
        Verifier 'H12c' 'et le mot de passe n a PAS change' 422 (Invoke-Api POST '/api/auth/connexion' @{
            email = $MAIL_P; motDePasse = 'apresplafond2026' }) | Out-Null
    } else {
        AFaireAMain 'H12b' 'le bon code refuse apres cinq echecs' 'demande la lecture du code dans la console'
        AFaireAMain 'H12c' 'le mot de passe n a pas change' 'depend de H12b'
    }

    # H13 et H14 : la quatrieme demande. Meme reponse, mais AUCUN code
    # ne doit partir. Le quota ne se libere qu au bout d une heure, donc
    # ces deux scenarios viennent en dernier du sous-bloc.
    Verifier 'H13' 'quatrieme demande : la reponse ne change pas' 202 (
        Invoke-Api POST '/api/auth/mot-de-passe/oublie' @{ email = $MAIL_R }) | Out-Null
    Verifier 'H14' 'cinquieme demande : toujours 202, plafond atteint' 202 (
        Invoke-Api POST '/api/auth/mot-de-passe/oublie' @{ email = $MAIL_R }) | Out-Null
    Noter 'verifiez qu AUCUN nouveau cadre n est apparu pour ces deux dernieres demandes'
} else {
    foreach ($n in 'H02','H03','H04','H05','H06','H07','H08','H09','H10','H11','H12a','H12b','H12c','H13','H14') {
        Ignorer $n 'mot de passe oublie' 'H01 a echoue : pas de compte dedie'
    }
    $ID_R = $null
}

# H15 : les deux chemins se ressemblent, les regles non. /mot-de-passe
# (changement avec l ancien) reste PROTEGE ; /mot-de-passe/oublie est
# ouvert.
Verifier 'H15' 'changer son mot de passe sans jeton reste refuse' 401 (
    Invoke-Api POST '/api/auth/mot-de-passe' @{ ancienMotDePasse = 'x'; nouveauMotDePasse = 'y2026' }) | Out-Null

# --- administration des comptes -------------------------------------
if ($JA) {
    $r = Invoke-Api GET '/api/admin/utilisateurs' $null $JA
    Verifier 'H16' 'liste des utilisateurs : paginee, champs complets' 200 $r {
        param($c)
        if ($null -eq $c.elements) { return 'reponse non paginee' }
        if ($c.taille -gt 20) { return "taille de page $($c.taille) : la maquette en annonce 20" }
        if ($c.elements.Count -lt 1) { return 'aucun utilisateur' }
        $u = $c.elements[0]
        foreach ($champ in 'email','role','actif','verrouille','doitChangerMotDePasse') {
            if ($null -eq $u.$champ) { return "le champ $champ manque sur une ligne" }
        }
        $null
    } | Out-Null

    Verifier 'H17a' 'recherche par prenom' 200 (Invoke-Api GET '/api/admin/utilisateurs?recherche=balla' $null $JA) {
        param($c) if ($c.total -lt 1) { 'aucun resultat pour [balla]' } else { $null } } | Out-Null
    Verifier 'H17b' 'recherche par nom' 200 (Invoke-Api GET '/api/admin/utilisateurs?recherche=kone' $null $JA) {
        param($c) if ($c.total -lt 1) { 'aucun resultat pour [kone]' } else { $null } } | Out-Null
    Verifier 'H17c' 'recherche par fragment d adresse' 200 (Invoke-Api GET '/api/admin/utilisateurs?recherche=example.ml' $null $JA) {
        param($c) if ($c.total -lt 1) { 'aucun resultat pour [example.ml] : la recherche ne porte pas sur l adresse' } else { $null } } | Out-Null
    Verifier 'H17d' 'recherche par fragment de telephone' 200 (Invoke-Api GET '/api/admin/utilisateurs?recherche=7000' $null $JA) {
        param($c) if ($c.total -lt 1) { 'aucun resultat pour [7000] : la recherche ne porte pas sur le telephone' } else { $null } } | Out-Null

    Verifier 'H18' 'filtres combines filiere + actif' 200 (Invoke-Api GET '/api/admin/utilisateurs?filiere=VOLAILLE&actif=true' $null $JA) {
        param($c)
        foreach ($u in $c.elements) {
            if (-not $u.actif) { return "[$($u.nom)] est inactif dans un filtre actif=true" }
            if ($u.filieres -and $u.filieres -notcontains 'VOLAILLE') { return "[$($u.nom)] ne pratique pas la volaille" }
        }
        $null
    } | Out-Null

    Verifier 'H19' 'liste des utilisateurs refusee a un eleveur' 403 (Invoke-Api GET '/api/admin/utilisateurs' $null $JE) | Out-Null

    if ($ID_R) {
        Verifier 'H20' 'l admin reinitialise le mot de passe du compte jetable' 204 (
            Invoke-Api POST "/api/admin/utilisateurs/$ID_R/mot-de-passe" $null $JA) | Out-Null
        $fiche = (Invoke-Api GET "/api/admin/utilisateurs/$ID_R" $null $JA)

        # L'adresse du compte a pu changer en I21 ; ici elle est encore
        # celle de H01, puisque le bloc I vient apres.
        # Le provisoire fait DOUZE caracteres, pris dans un alphabet sans les
        # confusions de lecture : pas de 0/O, pas de 1/l/I. On extrait donc
        # exactement cela, plutot que [tout ce qui suit l'etiquette] - une
        # extraction trop large laissait passer un caractere parasite, et
        # H21 echouait sans qu'on sache sur quelle valeur.
        $PROV_BRUT = ValeurDuJournal "MOT DE PASSE REINITIALISE" $MAIL_R 'Mot de passe :'
        $PROV_TRACE = $script:DERNIERE_RECHERCHE
        $PROV = $null
        if ($PROV_BRUT -match '([a-km-zA-HJ-NP-Z2-9]{12})') { $PROV = $Matches[1] }
        if ($PROV) { Noter ("provisoire lu dans le journal : [{0}]" -f $PROV) }
        else {
            Noter ("provisoire NON lu. Recherche : {0}" -f $PROV_TRACE)
            $script:Resultats += [pscustomobject]@{
                Numero = 'H20d'; Bloc = $script:BlocCourant
                Intitule = 'diagnostic : lecture du provisoire dans le journal'
                Statut = 'ECART'; Detail = $PROV_TRACE
            }
            # Saisir un code a six chiffres a la place d'un provisoire de
            # douze caracteres ne prouve rien et brouille le diagnostic.
            Write-Host '   Ne tapez RIEN si vous ne voyez pas un mot de passe de 12 caracteres.' -ForegroundColor Yellow
        }
        if (-not $PROV -and -not $SansPause) {
            Write-Host ''
            Write-Host '  Journal indisponible. Dans la console du serveur, le cadre' -ForegroundColor White
            Write-Host '  MOT DE PASSE REINITIALISE PAR L ADMINISTRATION.' -ForegroundColor White
            $PROV = Demander '   le mot de passe provisoire, 12 caracteres (Entree pour sauter) :'
            # On REFUSE une saisie qui n'a pas la forme d'un provisoire. A
            # l'execution de 22h23, un code a six chiffres a ete saisi ici :
            # H21 a signale un ecart qui ne disait rien du serveur.
            if ($PROV -notmatch '^[a-km-zA-HJ-NP-Z2-9]{12}$') {
                if ($PROV) {
                    Write-Host ("   [{0}] n a pas la forme d un mot de passe provisoire : ignore." -f $PROV) -ForegroundColor Yellow
                }
                $PROV = $null
            }
        }
        if ($PROV) {
            # Le provisoire et la ligne brute sont reportes DANS le detail de
            # l'ecart. A l'execution du 4 octobre, H21 a echoue sans que rien
            # ne dise sur quelle valeur : impossible de distinguer une
            # mauvaise extraction d'un vrai defaut du serveur.
            $cx = Invoke-Api POST '/api/auth/connexion' @{ email = $MAIL_R; motDePasse = $PROV }
            Verifier 'H21' 'connexion au provisoire : doitChangerMotDePasse vrai' 200 $cx {
                param($c)
                if (-not $c.doitChangerMotDePasse) { return 'doitChangerMotDePasse est faux : l application n imposera pas le changement' }
                $null
            } | Out-Null
            if ($cx.Code -ne 200) {
                Noter ("provisoire essaye : [{0}]  sur l adresse [{1}]" -f $PROV, $MAIL_R)
                Noter ("ligne brute du journal : [{0}]" -f $PROV_BRUT)
                $script:Resultats += [pscustomobject]@{
                    Numero = 'H21d'; Bloc = $script:BlocCourant
                    Intitule = 'diagnostic H21 : valeur extraite'
                    Statut = 'ECART'
                    Detail = "provisoire [$PROV] refuse sur [$MAIL_R] ; ligne brute du journal : [$PROV_BRUT]"
                }
            }
            $MDP_R = $PROV
        } else {
            AFaireAMain 'H21' 'connexion au mot de passe provisoire' 'demande la lecture du provisoire dans la console'
        }

        Verifier 'H22' 'desactivation du compte' 204 (
            Invoke-Api POST "/api/admin/utilisateurs/$ID_R/activation?actif=false" $null $JA) | Out-Null
        Verifier 'H22b' 'un compte desactive ne se connecte plus' 422 (Invoke-Api POST '/api/auth/connexion' @{
            email = $MAIL_R; motDePasse = $MDP_R }) | Out-Null
        Verifier 'H23' 'reactivation du compte' 204 (
            Invoke-Api POST "/api/admin/utilisateurs/$ID_R/activation?actif=true" $null $JA) | Out-Null
    } else {
        foreach ($n in 'H20','H21','H22','H22b','H23') { Ignorer $n 'administration du compte jetable' 'H01 a echoue' }
    }
} else {
    foreach ($n in 'H16','H17a','H17b','H17c','H17d','H18','H19','H20','H21','H22','H22b','H23') {
        Ignorer $n 'administration des comptes' 'connexion administrateur impossible'
    }
}

# =====================================================================
Bloc 'I - Correction et suppression'

# I01 : LE correctif critique numero 3. [Mes informations] recevait une
# entite DETACHEE et n ecrivait rien en base : la reponse etait juste,
# seule la base mentait. La preuve n est donc pas le 200 - il etait deja
# la - mais la relecture.
$profil = (Invoke-Api GET '/api/auth/moi' $null $JE).Corps
if ($profil) {
    $maj = @{ nom = $profil.nom; email = $profil.email; telephone = '70000001'
              zoneId = $profil.zone.id; langue = 'FR'
              filieres = @('VOLAILLE','BOVINS','OVINS','CAPRINS') }
    $r = Invoke-Api PUT '/api/auth/moi' $maj $JE
    Verifier 'I01' 'modification du profil : 200 et ordre des filieres' 200 $r {
        param($c)
        $attendu = 'VOLAILLE,BOVINS,OVINS,CAPRINS'
        if (($c.filieresPratiquees -join ',') -ne $attendu) {
            return "ordre [$($c.filieresPratiquees -join ',')] au lieu de [$attendu]"
        }
        $null
    } | Out-Null

    $relu = (Invoke-Api GET '/api/auth/moi' $null $JE).Corps
    Verifier 'I01b' 'l ecriture a vraiment atteint la base (relecture)' 200 ([pscustomobject]@{ Code = 200; Corps = $relu; Texte = '' }) {
        param($c)
        if ($c.telephone -ne '70000001') {
            return "relu [$($c.telephone)] au lieu de [70000001] : la reponse etait juste mais rien n a ete ecrit - DEFAUT CRITIQUE 3"
        }
        $null
    } | Out-Null
    # On remet le telephone d origine.
    $retour = $maj.Clone(); $retour.telephone = $profil.telephone
    $null = Invoke-Api PUT '/api/auth/moi' $retour $JE

    $vide = $maj.Clone(); $vide.filieres = @()
    Verifier 'I02' 'profil sans aucune filiere' 422 (Invoke-Api PUT '/api/auth/moi' $vide $JE) | Out-Null
    $pris = $maj.Clone(); $pris.email = $VETO_MAIL
    Verifier 'I03' 'adresse deja prise par un autre compte' 409 (Invoke-Api PUT '/api/auth/moi' $pris $JE) | Out-Null
    $maj2 = $maj.Clone(); $maj2.email = $profil.email.ToUpper(); $maj2.telephone = $profil.telephone
    Verifier 'I04' 'adresse en majuscules : normalisee en minuscules' 200 (Invoke-Api PUT '/api/auth/moi' $maj2 $JE) {
        param($c) if ($c.email -ne $profil.email.ToLower()) { "l adresse rendue est [$($c.email)] : la normalisation n est pas faite au serveur" } else { $null } } | Out-Null

    # I04b a I04e : changer l ADRESSE (identifiant de connexion) exige le mot
    # de passe actuel (audit du 10 octobre). Un jeton vole ne suffit plus.
    $neuve = $maj.Clone(); $neuve.email = "i04-$MARQUE@example.ml"
    Verifier 'I04b' 'changer d adresse sans mot de passe : refuse' 422 (
        Invoke-Api PUT '/api/auth/moi' $neuve $JE) | Out-Null
    $faux = $neuve.Clone(); $faux.motDePasseActuel = 'pas-le-bon'
    Verifier 'I04c' 'changer d adresse avec un mauvais mot de passe : refuse' 422 (
        Invoke-Api PUT '/api/auth/moi' $faux $JE) | Out-Null
    $bon = $neuve.Clone(); $bon.motDePasseActuel = $ELEVEUR_MDP
    $rI04d = Invoke-Api PUT '/api/auth/moi' $bon $JE
    Verifier 'I04d' 'changer d adresse avec le bon mot de passe : 200' 200 $rI04d | Out-Null
    if ($rI04d.Code -eq 200) {
        # Retour a l adresse de demonstration, sinon la suite ne se connecte plus.
        $rendre = $maj.Clone(); $rendre.motDePasseActuel = $ELEVEUR_MDP
        $rendre.telephone = $profil.telephone
        $null = Invoke-Api PUT '/api/auth/moi' $rendre $JE
    }
    # Rend aussi le telephone d origine de l eleveur de demonstration.
    $fin = $maj.Clone(); $fin.telephone = $profil.telephone
    Verifier 'I04e' 'nom ou telephone seuls : aucun mot de passe demande' 200 (
        Invoke-Api PUT '/api/auth/moi' $fin $JE) | Out-Null
} else {
    foreach ($n in 'I01','I01b','I02','I03','I04','I04b','I04c','I04d','I04e') { Ignorer $n 'modification du profil' 'profil illisible' }
}

# I05 a I09 : les competences du veterinaire. On choisit une maladie que
# ce veterinaire ne declare pas encore.
if ($JV -and $JA) {
    $toutes = (Invoke-Api GET '/api/admin/maladies' $null $JA).Corps
    $cible = $null
    if ($ID_MALADIE_T) { $cible = $ID_MALADIE_T }
    elseif ($toutes -and $toutes.Count -gt 0) { $cible = $toutes[$toutes.Count - 1].id }

    if ($cible) {
        $p = Invoke-Api POST "/api/veterinaires/competences/$cible" $null $JV
        if ($p.Code -eq 409) {
            # Deja declaree : on retire d abord, pour que le scenario ait
            # un sens. Deviner l etat serait l erreur a ne pas faire.
            $null = Invoke-Api DELETE "/api/veterinaires/competences/$cible" $null $JV
            $p = Invoke-Api POST "/api/veterinaires/competences/$cible" $null $JV
        }
        Verifier 'I05' 'declaration d une competence' 204 $p | Out-Null
        Verifier 'I06' 'competence declaree deux fois' 409 (Invoke-Api POST "/api/veterinaires/competences/$cible" $null $JV) | Out-Null
        Verifier 'I07' 'retrait de la competence' 204 (Invoke-Api DELETE "/api/veterinaires/competences/$cible" $null $JV) | Out-Null
        Verifier 'I08' 'retrait rejoue : 204 et non 404 (idempotence)' 204 (Invoke-Api DELETE "/api/veterinaires/competences/$cible" $null $JV) | Out-Null
        Verifier 'I09' 'declarer une competence, en eleveur' 403 (Invoke-Api POST "/api/veterinaires/competences/$cible" $null $JE) | Out-Null
    } else { foreach ($n in 'I05','I06','I07','I08','I09') { Ignorer $n 'competences' 'aucune maladie cible' } }
} else { foreach ($n in 'I05','I06','I07','I08','I09') { Ignorer $n 'competences' 'jeton veterinaire ou admin absent' } }

# I10 a I14 : correction et suppression d une unite. Sur les lots
# JETABLES, pas sur LOT-014 : une suppression refusee se prouve aussi
# bien sur un lot du script, et le jeu de demonstration reste intact.
if ($ID_LOT_VIDE -and $TV) {
    $u = @{ code = "VID-$MARQUE"; typeElevageId = $TV
            dateEntree = (Get-Date).AddDays(-4).ToString('yyyy-MM-dd'); note = "Corrige $MARQUE" }
    Verifier 'I10' 'correction d une unite : date et note' 200 (Invoke-Api PUT "/api/cheptel/$ID_LOT_VIDE" $u $JE) {
        param($c) if ($c.code -ne "VID-$MARQUE") { "code [$($c.code)] : la correction n a pas pris" } else { $null } } | Out-Null

    # I10b : la correction de date doit DEPLACER les rappels du protocole.
    # Le lot a ete cree en B13 avec une date d entree a J-5, et I10 la
    # corrige a J-4 : chaque rappel de protocole encore ouvert doit donc
    # tomber a J-4 + son jour de cycle. Avant le 5 octobre, les rappels
    # restaient calcules sur l ancienne date, un jour trop tot.
    $ficheI = (Invoke-Api GET "/api/cheptel/$ID_LOT_VIDE" $null $JE).Corps
    $dateEntreeI = (Get-Date).AddDays(-4)
    $vusI = 0; $ecartsI = @()
    foreach ($rp in @($ficheI.rappels)) {
        if ($rp.libelle -notmatch '\(J(\d+)\)') { continue }
        if ($rp.statut -ne 'A_VENIR' -and $rp.statut -ne 'ECHU') { continue }
        $vusI++
        $attenduI = $dateEntreeI.AddDays([int]$Matches[1]).ToString('yyyy-MM-dd')
        $obtenuI  = ([string]$rp.dateEcheance).Substring(0,10)
        if ($obtenuI -ne $attenduI) { $ecartsI += "$($rp.libelle) : echeance $obtenuI au lieu de $attenduI" }
    }
    if ($vusI -eq 0) {
        Ignorer 'I10b' 'la correction de date deplace les rappels du protocole' 'aucun rappel de protocole ouvert sur ce lot'
    } else {
        Verifier 'I10b' 'la correction de date deplace les rappels du protocole' 200 (
            [pscustomobject]@{ Code = 200; Corps = [pscustomobject]@{ ecarts = $ecartsI }; Texte = '' }) {
            param($c) if (@($c.ecarts).Count) { return (@($c.ecarts) -join ' ; ') } else { $null } } | Out-Null
    }

    if ($TB) {
        $bascule = $u.Clone(); $bascule.typeElevageId = $TB
        Verifier 'I11' 'changer la filiere d un elevage' 422 (Invoke-Api PUT "/api/cheptel/$ID_LOT_VIDE" $bascule $JE) | Out-Null
    } else { Ignorer 'I11' 'changement de filiere refuse' 'type BOVINS inconnu' }

    if ($ID_LOT_T) {
        $dejaPris = $u.Clone(); $dejaPris.code = $CODE_T
        Verifier 'I12' 'code deja porte par un autre de vos lots' 409 (Invoke-Api PUT "/api/cheptel/$ID_LOT_VIDE" $dejaPris $JE) | Out-Null
    } else { Ignorer 'I12' 'code en doublon' 'B09 a echoue' }

    # I13 : le lot vaccine en D03 porte un carnet, donc il ne se supprime
    # pas. C'est le meme lot : la suppression doit etre refusee.
    Verifier 'I13' 'supprimer un lot qui porte un carnet sanitaire' 422 (
        Invoke-Api DELETE "/api/cheptel/$ID_LOT_VIDE" $null $JE) | Out-Null
} else { foreach ($n in 'I10','I11','I12','I13') { Ignorer $n 'correction d unite' 'B13 a echoue' } }

# I14 : la seule suppression autorisee - un lot tout juste cree, vierge.
if ($TV) {
    $neuf = Invoke-Api POST '/api/cheptel/lots' @{ code = "DEL-$MARQUE"; typeElevageId = $TV
            effectifInitial = 5; dateMiseEnPlace = (Get-Date).ToString('yyyy-MM-dd') } $JE
    if ($neuf.Code -eq 201) {
        Verifier 'I14' 'supprimer un lot vierge : autorise' 204 (
            Invoke-Api DELETE "/api/cheptel/$($neuf.Corps.id)" $null $JE) | Out-Null
        Verifier 'I14b' 'il a bien disparu' 404 (Invoke-Api GET "/api/cheptel/$($neuf.Corps.id)" $null $JE) | Out-Null
    } else { Ignorer 'I14' 'suppression d un lot vierge' 'creation du lot impossible' ; Ignorer 'I14b' 'disparition du lot' 'creation impossible' }
} else { Ignorer 'I14' 'suppression d un lot vierge' 'type VOLAILLE inconnu' ; Ignorer 'I14b' 'disparition du lot' 'type inconnu' }

# I15 et I16 : LE scenario decisif du bloc. Supprimer l evenement qui
# avait solde un rappel doit ROUVRIR ce rappel.
if ($ID_EVT) {
    Verifier 'I15' 'correction d un evenement sanitaire' 200 (Invoke-Api PUT "/api/sanitaire/evenements/$ID_EVT" @{
        type = 'VACCINATION'; dateEvenement = (Get-Date).AddDays(-1).ToString('yyyy-MM-dd')
        produitObservation = "Vaccin Newcastle HB1, lot corrige $MARQUE" } $JE) {
        param($c) if ($c.produitObservation -notmatch 'corrige') { 'la correction n a pas pris' } else { $null } } | Out-Null
} else { Ignorer 'I15' 'correction d un evenement' 'D03 a echoue' }

# I16 : LE scenario decisif du bloc, reecrit le 5 octobre.
#
# La version precedente ne prouvait RIEN. Elle posait un rappel a la main
# ("rappelDansJours"), puis un acte du meme type, puis supprimait l acte et
# verifiait que le rappel etait "A_VENIR ou ECHU". Or un rappel pose a la
# main n a pas de ligne de protocole, et seul un rappel de protocole peut etre
# regle par un acte : le rappel n avait JAMAIS ete regle, donc il ne pouvait
# pas ne pas se rouvrir. Le test passait par construction, relance apres
# relance.
#
# Cette version regle un VRAI rappel de protocole, et le prouve : le lot est
# mis en place il y a 30 jours, son protocole (Newcastle J7 et J21) a donc deux
# rappels, a J-23 et J-9. Une vaccination datee de J-10 tombe dans la fenetre
# de sept jours du second, et seulement de celui-la. Un rappel regle quitte la
# liste des rappels ouverts : c est son absence qui prouve le reglement.
$ID_LOT_I16 = $null
if ($TV) {
    $cI = Invoke-Api POST '/api/cheptel/lots' @{ code = "RAP-$MARQUE"; typeElevageId = $TV
            effectifInitial = 15; dateMiseEnPlace = (Get-Date).AddDays(-30).ToString('yyyy-MM-dd') } $JE
    if ($cI.Code -eq 201) { $ID_LOT_I16 = $cI.Corps.id }
}
$lireOuverts = {
    @((Invoke-Api GET '/api/sanitaire/rappels?horizonJours=200' $null $JE).Corps |
        Where-Object { $_.uniteId -eq $ID_LOT_I16 })
}
$NUMS_I16 = 'I16','I16a','I16b','I16c','I16d','I16e'
if ($ID_LOT_I16) {
    $avantI = & $lireOuverts
    $j21 = $avantI | Where-Object { $_.libelle -match 'J21' } | Select-Object -First 1
    if (-not $j21) {
        foreach ($n in $NUMS_I16) { Ignorer $n 'reglement et rouverture d un rappel' 'le rappel J21 du protocole est absent du lot neuf' }
    } else {
        $acte = @{ type = 'VACCINATION'; dateEvenement = (Get-Date).AddDays(-10).ToString('yyyy-MM-dd')
                   produitObservation = "Vaccin Newcastle HB1 $MARQUE" }
        $pose = Invoke-Api POST "/api/sanitaire/unites/$ID_LOT_I16/evenements" $acte $JE
        $poseOk = Verifier 'I16' 'pose d une vaccination datee de J-10 sur le lot neuf' 201 $pose
        if ($poseOk) {
            $idActe = $pose.Corps.id
            $apresPose = & $lireOuverts
            $encorePresent = $apresPose | Where-Object { $_.id -eq $j21.id }
            Verifier 'I16a' 'la vaccination a regle le rappel J21 : il quitte la liste' 200 ([pscustomobject]@{ Code = 200; Corps = $encorePresent; Texte = '' }) {
                param($c) if ($c) { 'le rappel J21 est encore ouvert : l acte ne l a pas regle, le scenario ne prouve rien' } else { $null } } | Out-Null

            # I16b : le type de l acte devient TRAITEMENT. Un traitement n a
            # pas vaccine : le rappel de vaccin doit se rouvrir.
            $typeCorrige = $acte.Clone(); $typeCorrige.type = 'TRAITEMENT'
            $rb = Invoke-Api PUT "/api/sanitaire/evenements/$idActe" $typeCorrige $JE
            if ($rb.Code -eq 200) {
                $apresB = & $lireOuverts
                $rouvertB = $apresB | Where-Object { $_.id -eq $j21.id } | Select-Object -First 1
                Verifier 'I16b' 'changer le type de l acte en traitement rouvre le rappel de vaccin' 200 ([pscustomobject]@{ Code = 200; Corps = $rouvertB; Texte = '' }) {
                    param($c)
                    if (-not $c) { return 'le rappel reste regle alors que l acte n est plus une vaccination' }
                    if ($c.statut -notin @('A_VENIR','ECHU')) { return "statut $($c.statut)" }
                    $null } | Out-Null

                # I16c : on remet VACCINATION. La correction est reversible :
                # le rappel est de nouveau regle.
                $rc = Invoke-Api PUT "/api/sanitaire/evenements/$idActe" $acte $JE
                if ($rc.Code -eq 200) {
                    $apresC = & $lireOuverts
                    $presentC = $apresC | Where-Object { $_.id -eq $j21.id }
                    Verifier 'I16c' 'remettre le type vaccination regle de nouveau le rappel' 200 ([pscustomobject]@{ Code = 200; Corps = $presentC; Texte = '' }) {
                        param($c) if ($c) { 'le rappel est reste ouvert : la correction n est pas reversible' } else { $null } } | Out-Null
                } else { Ignorer 'I16c' 'retour au type vaccination' "PUT refuse (code $($rc.Code))" }
            } else {
                Ignorer 'I16b' 'changement de type de l acte' "PUT refuse (code $($rb.Code))"
                Ignorer 'I16c' 'retour au type vaccination' 'I16b non joue'
            }

            # I16d / I16e : la suppression de l acte rouvre le rappel. Ici le
            # rappel est reellement regle (I16c), donc le test prouve quelque chose.
            Verifier 'I16d' 'suppression de l acte : 204' 204 (
                Invoke-Api DELETE "/api/sanitaire/evenements/$idActe" $null $JE) | Out-Null
            $apresD = & $lireOuverts
            $rouvertD = $apresD | Where-Object { $_.id -eq $j21.id } | Select-Object -First 1
            Verifier 'I16e' 'le rappel est de nouveau ouvert apres la suppression de l acte' 200 ([pscustomobject]@{ Code = 200; Corps = $rouvertD; Texte = '' }) {
                param($c)
                if (-not $c) { return 'le rappel a disparu de la liste au lieu de se rouvrir' }
                if ($c.statut -notin @('A_VENIR','ECHU')) { return "statut $($c.statut) : le rappel reste regle alors que l acte a ete supprime" }
                $null } | Out-Null
        } else {
            foreach ($n in 'I16a','I16b','I16c','I16d','I16e') { Ignorer $n 'reglement et rouverture d un rappel' "pose de l acte refusee (code $($pose.Code))" }
        }
    }
} else { foreach ($n in $NUMS_I16) { Ignorer $n 'reglement et rouverture d un rappel' 'creation du lot dedie impossible' } }

# I17 a I19 : correction d une operation financiere.
$op2 = @{ type = 'DEPENSE'; categorie = 'ALIMENT'; montant = 9000
          dateOperation = (Get-Date).ToString('yyyy-MM-dd'); filiere = 'VOLAILLE'
          libelle = "A requalifier $MARQUE" }
$ro = Invoke-Api POST '/api/finances/operations' $op2 $JE
if ($ro.Code -eq 201) {
    $id2 = $ro.Corps.id
    Verifier 'I17' 'requalifier : depense Aliment -> revenu Vente d animaux' 200 (
        Invoke-Api PUT "/api/finances/operations/$id2" @{ type = 'REVENU'; categorie = 'VENTE_ANIMAUX'
            montant = 9000; dateOperation = (Get-Date).ToString('yyyy-MM-dd'); filiere = 'VOLAILLE'
            libelle = "Requalifie $MARQUE" } $JE) {
        param($c)
        if ($c.type -ne 'REVENU') { return "type $($c.type) au lieu de REVENU" }
        if ($c.montantSigne -ne 9000) { return "montantSigne $($c.montantSigne) : un revenu doit etre positif" }
        $null
    } | Out-Null
    Verifier 'I18' 'categorie incompatible avec le type' 422 (
        Invoke-Api PUT "/api/finances/operations/$id2" @{ type = 'DEPENSE'; categorie = 'VENTE_ANIMAUX'
            montant = 9000; dateOperation = (Get-Date).ToString('yyyy-MM-dd'); filiere = 'VOLAILLE'
            libelle = 'Incoherent' } $JE) | Out-Null
    $null = Invoke-Api DELETE "/api/finances/operations/$id2" $null $JE
} else { Ignorer 'I17' 'requalification' 'creation de l operation impossible' ; Ignorer 'I18' 'categorie incompatible' 'creation impossible' }

if ($ID_OP_VENTE) {
    Verifier 'I19' 'corriger une recette issue d une vente' 422 (
        Invoke-Api PUT "/api/finances/operations/$ID_OP_VENTE" @{ type = 'REVENU'; categorie = 'VENTE_ANIMAUX'
            montant = 1; dateOperation = (Get-Date).ToString('yyyy-MM-dd'); filiere = 'VOLAILLE'
            libelle = 'Doit etre refuse' } $JE) | Out-Null
} else { Ignorer 'I19' 'corriger une recette de vente' 'aucune operation issue d un mouvement' }

# I20 a I29 : administration.
if ($JA) {
    Verifier 'I20' 'fiche complete d un utilisateur' 200 (Invoke-Api GET "/api/admin/utilisateurs/$ID_ELEVEUR" $null $JA) {
        param($c)
        if (-not $c.email) { return 'aucune adresse' }
        if ($c.role -ne 'ELEVEUR') { return "role $($c.role)" }
        $null
    } | Out-Null

    if ($ID_R) {
        $nouvelle = "repare-$MARQUE@example.ml"
        Verifier 'I21' 'l admin corrige l adresse d un compte' 200 (Invoke-Api PUT "/api/admin/utilisateurs/$ID_R" @{
            nom = "Compte Reinit $MARQUE"; email = $nouvelle; zoneId = $ZONE_ID
            langue = 'FR'; filieres = @('VOLAILLE') } $JA) {
            param($c) if ($c.email -ne $nouvelle) { "l adresse rendue est [$($c.email)]" } else { $null } } | Out-Null
        Noter 'un avertissement part vers l ANCIENNE adresse : visible en console'
    } else { Ignorer 'I21' 'correction d adresse par l admin' 'H01 a echoue' }

    Verifier 'I22' 'annuaire admin : tous les veterinaires, actifs ou non' 200 (Invoke-Api GET '/api/admin/veterinaires' $null $JA) {
        param($c) if ($c.Count -lt 2) { "$($c.Count) veterinaire(s) : le jeu de demonstration en porte deux" } else { $null } } | Out-Null

    if ($ID_VETO_T -and $ZONE_AUTRE) {
        Verifier 'I23' 'changer la zone d intervention d un veterinaire' 200 (
            Invoke-Api PUT "/api/admin/veterinaires/$ID_VETO_T" @{ nom = "Dr Essai $MARQUE"
                telephone = '79000000'; email = "veto-$MARQUE@example.ml"; zoneId = $ZONE_ID
                zoneInterventionId = $ZONE_AUTRE; titre = 'Docteur'; specialite = 'Volaille' } $JA) {
            param($c) if (-not $c.zoneIntervention) { 'aucune zone d intervention rendue' } else { $null } } | Out-Null
    } else { Ignorer 'I23' 'zone d intervention' 'F28 a echoue, ou une seule zone en base' }

    # I24 et I25 : sur la maladie JETABLE creee en F27b. Les jouer sur
    # Newcastle aurait desactive la maladie dont dependent D09 et D10 a
    # la prochaine execution.
    if ($ID_MALADIE_T -and $SYMPTOMES.Count -ge 2) {
        Verifier 'I24' 'remplacer les ponderations : les anciennes sont effacees' 200 (
            Invoke-Api PUT "/api/admin/maladies/$ID_MALADIE_T" @{ nom = "Maladie essai $MARQUE"
                gravite = 'FAIBLE'; filieres = @('VOLAILLE'); symptomesTexte = 'Revu'
                traitement = 'Isolement.'; orienteVersVeterinaire = $false
                symptomes = @(@{ symptomeId = $SYMPTOMES[1].id; poids = 0.4 }) } $JA) {
            param($c) if ($c.gravite -ne 'FAIBLE') { "gravite $($c.gravite) : la modification n a pas pris" } else { $null } } | Out-Null

        Verifier 'I25' 'retrait d une maladie : active=false, pas de suppression' 204 (
            Invoke-Api DELETE "/api/admin/maladies/$ID_MALADIE_T" $null $JA) | Out-Null
        $apres = (Invoke-Api GET '/api/signalements/maladies?filiere=VOLAILLE' $null $JE).Corps
        Verifier 'I25b' 'la maladie retiree disparait de la liste des eleveurs' 200 ([pscustomobject]@{ Code = 200; Corps = $apres; Texte = '' }) {
            param($c)
            if ($c | Where-Object { $_.id -eq $ID_MALADIE_T }) { return 'la maladie retiree est encore proposee aux eleveurs' }
            if ($c.Count -ne 3) { return "$($c.Count) maladies au lieu de 3 : le jeu de demonstration a ete abime" }
            $null
        } | Out-Null
    } else { foreach ($n in 'I24','I25','I25b') { Ignorer $n 'modification et retrait d une maladie' 'F27b a echoue' } }

    if ($ID_ALERTE) {
        Verifier 'I27' 'modifier le message d une alerte TERMINEE' 422 (
            Invoke-Api PUT "/api/admin/alertes/$ID_ALERTE" @{ message = 'Trop tard' } $JA) | Out-Null
    } else { Ignorer 'I27' 'alerte terminee non modifiable' 'F19 a echoue' }

    # I26 : modifier une alerte ACTIVE. Celle du jeu de demonstration est
    # active ; on relit son message pour le remettre ensuite.
    $actives = (Invoke-Api GET '/api/admin/alertes' $null $JA).Corps
    $active = $null
    if ($actives) { $active = $actives | Where-Object { $_.statut -eq 'ACTIVE' } | Select-Object -First 1 }
    if ($active) {
        $ancien = $active.message
        Verifier 'I26' 'modifier le message, pas la portee ni la zone' 200 (
            Invoke-Api PUT "/api/admin/alertes/$($active.id)" @{ message = "Texte d essai $MARQUE" } $JA) {
            param($c)
            if ($c.message -notmatch $MARQUE) { return 'le message n a pas change' }
            if ($c.zone -ne $active.zone) { return "la zone a change : $($active.zone) -> $($c.zone)" }
            if ($c.portee -ne $active.portee) { return "la portee a change : $($active.portee) -> $($c.portee)" }
            $null
        } | Out-Null
        $null = Invoke-Api PUT "/api/admin/alertes/$($active.id)" @{ message = $ancien } $JA
    } else { Ignorer 'I26' 'modification d une alerte active' 'aucune alerte active' }

    # I28 : tetesVivantes = tetes en lot + animaux individuels. Le plan
    # annoncait 320 : c'est le compte de la VOLAILLE seule. Le total du
    # jeu de demonstration est 326, les six animaux individuels compris.
    # Le script ne compare donc que l invariant, le cheptel ayant ete
    # augmente par ses propres creations.
    Verifier 'I28' 'tableau de bord admin : huit tuiles coherentes' 200 (Invoke-Api GET '/api/admin/tableau-de-bord' $null $JA) {
        param($c)
        $pb = @()
        if ($c.tetesVolaille -gt $c.tetesVivantes) { $pb += "tetesVolaille $($c.tetesVolaille) depasse tetesVivantes $($c.tetesVivantes)" }
        if ($c.tetesVivantes -lt 320) { $pb += "tetesVivantes $($c.tetesVivantes) : le jeu de demonstration en porte deja 326" }
        if ($c.utilisateurs -lt 1) { $pb += 'aucun utilisateur' }
        if ($c.veterinaires -lt 2) { $pb += "veterinaires $($c.veterinaires) : il y en a au moins deux" }
        if ($null -eq $c.parFiliere) { $pb += 'repartition par filiere absente' }
        if ($pb.Count) { return ($pb -join ' ; ') }
        $null
    } | Out-Null

    Verifier 'I29' 'statistiques : les trois indicateurs de la maquette' 200 (Invoke-Api GET '/api/admin/statistiques' $null $JA) {
        param($c)
        $pb = @()
        if ($null -eq $c.tauxRetention30j) { $pb += 'tauxRetention30j absent' }
        elseif ($c.tauxRetention30j -lt 0 -or $c.tauxRetention30j -gt 100) { $pb += "tauxRetention30j $($c.tauxRetention30j) hors de 0-100" }
        if ($null -eq $c.partSignalementsTraites) { $pb += 'partSignalementsTraites absent' }
        elseif ($c.partSignalementsTraites -lt 0 -or $c.partSignalementsTraites -gt 100) { $pb += "partSignalementsTraites $($c.partSignalementsTraites) hors de 0-100" }
        if ($null -eq $c.parZone) { $pb += 'repartition par zone absente' }
        if ($null -eq $c.parRole) { $pb += 'repartition par role absente' }

        # L INVARIANT, AJOUTE LE 5 OCTOBRE.
        #
        # Ce controle ne verifiait que la PRESENCE des champs et
        # l intervalle 0-100. C est pour cela qu il a laisse passer un
        # vrai defaut pendant tout ce temps : la tuile UTILISATEURS
        # rendait le nombre d ELEVEURS, tandis que [+ N ce mois] et le
        # taux de retention portaient sur TOUS les roles. Trois
        # populations sous un seul titre, et chacune restait
        # tranquillement entre 0 et 100.
        #
        # Un intervalle n est pas un test : il accepte n importe quel
        # chiffre de la bonne forme. La somme des roles, elle, ne peut
        # etre juste que si les deux chiffres comptent la meme chose.
        # Un `if` simple, pas un `elseif` : celui-ci serait separe de
        # son `if` par une ligne vide et dix lignes de commentaire, et
        # mon verificateur ne controle que les accolades et les
        # chaines, pas la grammaire PowerShell. Il ne l attraperait pas.
        if ($c.parRole) {
            $sommeRoles = 0
            foreach ($l in $c.parRole) { $sommeRoles += [long]$l.valeur }
            if ([long]$c.utilisateurs -ne $sommeRoles) {
                $pb += "utilisateurs $($c.utilisateurs), mais la repartition par role totalise $sommeRoles : les tuiles ne comptent pas la meme population"
            }
        }

        # LE PLAFOND SUR partSignalementsTraites EST-IL LEVE ?
        #
        # Le denominateur comptait TOUS les signalements. Or un cas
        # benin n est jamais oriente - par conception, l eleveur le
        # traite seul - et prendreEnCharge() exige ORIENTE : il ne
        # pouvait donc jamais rejoindre le numerateur. Plus la
        # plateforme recevait de cas benins, plus elle paraissait
        # defaillante.
        #
        # Le denominateur est desormais les signalements qui ont
        # NECESSITE un veterinaire. F05 vient de clore un signalement
        # dans cette execution meme : le numerateur est donc non nul,
        # et le pourcentage ne peut plus etre structurellement zero.
        if ([long]$c.signalementsTraitesSur -lt 1) {
            $pb += 'signalementsTraitesSur vaut 0 : aucun signalement oriente dans la fenetre, alors que D10 et F19 en creent'
        }
        if ([long]$c.signalementsTraitesSur -ge 1 -and [int]$c.partSignalementsTraites -lt 1) {
            $pb += "partSignalementsTraites $($c.partSignalementsTraites) % sur $($c.signalementsTraitesSur) cas orientes, alors que F05 vient d en clore un : le numerateur reste inatteignable"
        }
        if ($pb.Count) { return ($pb -join ' ; ') }
        $null
    } | Out-Null
} else {
    foreach ($n in 'I20','I21','I22','I23','I24','I25','I25b','I26','I27','I28','I29') {
        Ignorer $n 'administration' 'connexion administrateur impossible'
    }
}

# =====================================================================
Bloc 'J - Les routes jamais exercees'
#
#  AJOUTE LE 5 OCTOBRE. Comptage mecanique du 5 octobre : sur 78 routes
#  declarees, 69 etaient appelees par un scenario et NEUF ne l etaient
#  par aucun. Ce bloc les couvre.
#
#  Pourquoi ca compte sur ce projet precisement : les trois defauts
#  critiques du 3 octobre et celui du 4 (le mot reserve [precision])
#  passaient TOUS la compilation, l audit statique et la validation du
#  schema. Quatre fois sur quatre, seule l execution les a reveles.
#  Ces neuf routes sont donc la part du back dont on ne sait rien.
#
#  CERTAINS SCENARIOS ICI SONT ECRITS POUR ECHOUER. Ils enoncent le
#  comportement VOULU, pas le comportement observe. Un ecart sur
#  J32 ou J34 n est pas un defaut du test : c est le defaut qu il
#  cherche. Voir leur commentaire.
# =====================================================================

# -- notifications : le centre de notifications et la pastille -----------
#
#  Cinq routes, aucune jamais appelee. Le risque est modere - les
#  notifications sont ecrites en base et relues par le tableau de bord,
#  verifie en B01 - mais le centre lui-meme n a jamais repondu.

Verifier 'J01' 'liste des notifications : enveloppe de pagination complete' 200 (
    Invoke-Api GET '/api/notifications' $null $JE) {
    param($c)
    if ($null -eq $c) { return 'aucune reponse' }
    $pb = @()
    foreach ($champ in @('elements','page','taille','total','nombrePages','derniere')) {
        if ($null -eq $c.PSObject.Properties[$champ]) { $pb += "champ [$champ] absent" }
    }
    if ($c.taille -ne 20) { $pb += "taille $($c.taille) au lieu de 20" }
    if ($pb.Count) { return ($pb -join ' ; ') }
    $null
} | Out-Null

# J02 : une page tres au-dela du dernier element. Doit rendre une page
# vide, pas une erreur : le client Flutter fait defiler et demandera
# toujours une page de trop.
Verifier 'J02' 'page 50 : vide et marquee derniere, pas une erreur' 200 (
    Invoke-Api GET '/api/notifications?page=50' $null $JE) {
    param($c)
    if ($null -eq $c) { return 'aucune reponse' }
    if ($c.elements.Count -ne 0) { return "$($c.elements.Count) elements sur une page vide attendue" }
    if (-not $c.derniere) { return 'derniere vaut faux sur une page au-dela de la fin' }
    $null
} | Out-Null

# J03 : ECRIT POUR ENONCER LE COMPORTEMENT VOULU.
#
# PageRequest.of(-1, 20) leve IllegalArgumentException. Le controleur ne
# borne pas le parametre, donc l exception monte jusqu au filet
# @ExceptionHandler(Exception.class) et sort en 500. Un parametre fautif
# est une faute du CLIENT : la reponse doit etre 400.
#
# CORRIGE LE 5 OCTOBRE par Pagination.de, qui leve
# RegleMetierException -> 422 UNPROCESSABLE_ENTITY.
#
# POURQUOI 422 ET NON 400, apres mesure. Ce scenario attendait 400
# et le back a rendu 422 : c'est l'assertion qui avait tort.
#   - page=-1 est un entier SYNTAXIQUEMENT valide et semantiquement
#     impossible. C'est la definition de 422.
#   - zoneId=abc est une erreur de TYPE : le corps ne se lit pas,
#     donc 400 (scenario J20).
# La convention existait deja : C05, l'intervalle de dates inverse,
# a la meme forme et rend 422 par le meme gestionnaire.
#
# On n'accepte PAS les deux codes : asseoir 400 ou 422 indifferemment
# laisserait passer un changement de famille de reponse.
Verifier 'J03' 'page negative : refusee en 422, plus en 500' 422 (
    Invoke-Api GET '/api/notifications?page=-1' $null $JE) | Out-Null

$NON_LUES_AVANT = (Invoke-Api GET '/api/notifications/non-lues' $null $JE)
Verifier 'J04' 'compteur de la pastille : un nombre, pas une erreur' 200 $NON_LUES_AVANT {
    param($c)
    if ($null -eq $c) { return 'aucune reponse' }
    $n = 0
    if (-not [int]::TryParse([string]$c, [ref]$n)) { return "reponse [$c] n est pas un nombre" }
    if ($n -lt 0) { return "compteur negatif : $n" }
    $null
} | Out-Null
Noter ("notifications non lues avant lecture : {0}" -f $NON_LUES_AVANT.Corps)

Verifier 'J05' 'marquer toutes les notifications lues' 204 (
    Invoke-Api POST '/api/notifications/lecture' $null $JE) | Out-Null

# J06 : LE scenario qui compte dans ce groupe. J05 rend 204 meme si elle
# n ecrit rien - c est exactement la forme du defaut du 3 octobre, ou
# [Mes informations] rendait 200 sans rien enregistrer. On relit donc le
# compteur apres, et on exige zero.
Verifier 'J06' 'apres lecture, le compteur est retombe a zero' 200 (
    Invoke-Api GET '/api/notifications/non-lues' $null $JE) {
    param($c)
    $n = 0
    [void][int]::TryParse([string]$c, [ref]$n)
    if ($n -ne 0) { return "$n notification(s) encore non lue(s) apres POST /lecture" }
    $null
} | Out-Null

$JETON_FCM = "essai-fcm-$MARQUE"
Verifier 'J07' 'enregistrement d un jeton d appareil' 204 (
    Invoke-Api POST '/api/notifications/appareils' @{
        jeton = $JETON_FCM; plateforme = 'android' } $JE) | Out-Null

# J08 : le MEME jeton rejoue. Firebase renouvelle ses jetons sans
# intervention de l utilisateur, et l application rappelle cette route a
# chaque demarrage : un refus en 409 couperait les notifications de tout
# telephone deja enregistre. Le service rattache plutot qu il ne cree.
Verifier 'J08' 'meme jeton rejoue : rattachement, pas de conflit' 204 (
    Invoke-Api POST '/api/notifications/appareils' @{
        jeton = $JETON_FCM; plateforme = 'android' } $JE) | Out-Null

Verifier 'J09' 'jeton d appareil vide' 400 (
    Invoke-Api POST '/api/notifications/appareils' @{
        jeton = ''; plateforme = 'android' } $JE) | Out-Null

Verifier 'J10' 'jeton d appareil de plus de 255 caracteres' 400 (
    Invoke-Api POST '/api/notifications/appareils' @{
        jeton = ('x' * 300); plateforme = 'android' } $JE) | Out-Null

Verifier 'J11' 'plateforme de plus de 12 caracteres' 400 (
    Invoke-Api POST '/api/notifications/appareils' @{
        jeton = "autre-$MARQUE"; plateforme = 'une-plateforme-beaucoup-trop-longue' } $JE) | Out-Null

Verifier 'J12' 'oubli de l appareil, a la deconnexion' 204 (
    Invoke-Api DELETE ("/api/notifications/appareils?jeton=" + $JETON_FCM) $null $JE) | Out-Null

# J13 : le parametre obligatoire manque. Exerce le handler
# ServletRequestBindingException ajoute le 4 octobre - avant lui, un
# parametre absent sortait en 500.
Verifier 'J13' 'oubli d appareil sans le parametre jeton' 400 (
    Invoke-Api DELETE '/api/notifications/appareils' $null $JE) | Out-Null

# J14 : un jeton inconnu. 204 et SILENCE, volontairement : rendre 404
# permettrait de tester l existence d un jeton Firebase, et 403 sur le
# jeton d autrui en confirmerait le proprietaire.
Verifier 'J14' 'oubli d un jeton inconnu : 204 et silence' 204 (
    Invoke-Api DELETE '/api/notifications/appareils?jeton=jeton-qui-n-existe-pas' $null $JE) | Out-Null

Verifier 'J15' 'liste des notifications sans jeton' 401 (
    Invoke-Api GET '/api/notifications') | Out-Null
Verifier 'J16' 'marquer lu sans jeton' 401 (
    Invoke-Api POST '/api/notifications/lecture') | Out-Null

# -- annuaire veterinaire : un ecran de la maquette ----------------------
#
#  GET /api/veterinaires est l ecran [Annuaire veterinaire]. Il n avait
#  jamais repondu a un appel, et c est l un des ecrans que le front doit
#  afficher.

Verifier 'J17' 'annuaire de la zone de l appelant' 200 (
    Invoke-Api GET '/api/veterinaires' $null $JE) {
    param($c)
    $liste = @($c)
    if ($liste.Count -lt 1) { return 'annuaire vide : aucun veterinaire dans la zone de Balla Kone' }
    foreach ($v in $liste) {
        if (-not $v.nom) { return 'une ligne sans nom' }
        if ($null -eq $v.PSObject.Properties['telephone']) { return 'champ telephone absent : l eleveur ne peut pas appeler' }
    }
    $null
} | Out-Null

if ($ZONE_AUTRE) {
    Verifier 'J18' 'annuaire d une autre zone, demandee explicitement' 200 (
        Invoke-Api GET ("/api/veterinaires?zoneId=" + $ZONE_AUTRE) $null $JE) | Out-Null
} else { Ignorer 'J18' 'annuaire d une autre zone' 'une seule zone connue' }

# J19 : zone inexistante. 200 et liste vide, pas 404 : la route filtre,
# elle ne resout pas une ressource. Un 404 obligerait le client a
# distinguer deux cas qui s affichent pareil.
Verifier 'J19' 'annuaire d une zone inexistante : liste vide' 200 (
    Invoke-Api GET '/api/veterinaires?zoneId=999999' $null $JE) {
    # Where-Object : sous PowerShell 7, une liste JSON vide arrive comme $null,
    # et @($null).Count vaut 1.
    param($c) $n = @($c | Where-Object { $_ }).Count
    if ($n -ne 0) { "$n veterinaires dans une zone inexistante" } else { $null } } | Out-Null

# J20 : zoneId non numerique. Exerce le handler
# MethodArgumentTypeMismatchException ajoute le 4 octobre.
Verifier 'J20' 'zoneId non numerique' 400 (
    Invoke-Api GET '/api/veterinaires?zoneId=abc' $null $JE) | Out-Null

Verifier 'J21' 'annuaire sans jeton' 401 (Invoke-Api GET '/api/veterinaires') | Out-Null

# -- competences declarees PAR L ADMINISTRATION --------------------------
#
#  A distinguer des deux routes de l espace veterinaire, deja exercees en
#  F : celles-ci portent l identifiant d un AUTRE compte, parce que
#  l administration agit sur le compte d un tiers - ecran [Modifier un
#  veterinaire], bloc [Maladies traitees].

$ID_VETO_J = $null
$ID_MALADIE_J = $null
if ($JA) {
    $vetos = @((Invoke-Api GET '/api/admin/veterinaires' $null $JA).Corps)
    if ($vetos.Count -gt 0) { $ID_VETO_J = $vetos[0].id }

    # On cherche une maladie que ce veterinaire ne traite PAS deja, sinon
    # J22 rendrait 409 a juste titre et le scenario ne prouverait rien.
    # Les maladies de la liste admin sont completes.
    $maladies = @((Invoke-Api GET '/api/admin/maladies' $null $JA).Corps)
    if ($maladies.Count -gt 0) { $ID_MALADIE_J = ($maladies | Select-Object -Last 1).id }
}

if ($ID_VETO_J -and $ID_MALADIE_J) {

    # On part d un etat connu : si la competence existe deja d une
    # execution precedente, on la retire sans compter le resultat.
    #
    # Le chemin est ecrit EN CLAIR a chaque appel, pas range dans une
    # variable. Le volet 8 de l audit mesure la couverture des routes
    # en lisant les chaines litterales du plan : un chemin passe par
    # variable lui fait declarer la route non couverte alors qu elle
    # l est. Un faux positif dans un audit est pire qu une absence de
    # controle - on cherche un defaut qui n existe pas, et on finit
    # par ne plus croire l audit du tout.
    [void](Invoke-Api DELETE "/api/admin/veterinaires/$ID_VETO_J/competences/$ID_MALADIE_J" $null $JA)

    Verifier 'J22' 'l administration declare une competence' 204 (
        Invoke-Api POST "/api/admin/veterinaires/$ID_VETO_J/competences/$ID_MALADIE_J" $null $JA) | Out-Null

    # J23 : le double clic. Sans la contrainte d unicite ET ce refus,
    # l ecran afficherait [4 competences] pour trois maladies.
    Verifier 'J23' 'competence deja declaree : refus en 409' 409 (
        Invoke-Api POST "/api/admin/veterinaires/$ID_VETO_J/competences/$ID_MALADIE_J" $null $JA) | Out-Null

    Verifier 'J24' 'l administration retire la competence' 204 (
        Invoke-Api DELETE "/api/admin/veterinaires/$ID_VETO_J/competences/$ID_MALADIE_J" $null $JA) | Out-Null

    # J25 : une suppression est idempotente. 404 sur un second appel
    # ferait echouer un reseau lent alors que l etat voulu est atteint.
    Verifier 'J25' 'retrait rejoue : 204, la suppression est idempotente' 204 (
        Invoke-Api DELETE "/api/admin/veterinaires/$ID_VETO_J/competences/$ID_MALADIE_J" $null $JA) | Out-Null

    Verifier 'J26' 'competence sur un veterinaire inexistant' 404 (
        Invoke-Api POST "/api/admin/veterinaires/999999/competences/$ID_MALADIE_J" $null $JA) | Out-Null
    Verifier 'J27' 'competence sur une maladie inexistante' 404 (
        Invoke-Api POST "/api/admin/veterinaires/$ID_VETO_J/competences/999999" $null $JA) | Out-Null

    Verifier 'J28' 'declarer une competence, en eleveur' 403 (
        Invoke-Api POST "/api/admin/veterinaires/$ID_VETO_J/competences/$ID_MALADIE_J" $null $JE) | Out-Null
    Verifier 'J29' 'retirer une competence, en eleveur' 403 (
        Invoke-Api DELETE "/api/admin/veterinaires/$ID_VETO_J/competences/$ID_MALADIE_J" $null $JE) | Out-Null
} else {
    foreach ($n in 'J22','J23','J24','J25','J26','J27','J28','J29') {
        Ignorer $n 'competences par l administration' 'aucun veterinaire ou aucune maladie en base'
    }
}

# -- PUT /api/admin/types-elevage/{id} -----------------------------------
#
#  LA ROUTE LA PLUS SENSIBLE DES NEUF. Deux raisons.
#
#  1. C est elle qui porte le correctif d audit n 4 du 3 octobre :
#     changer la filiere d un type EMPLOYE reclasserait en masse toutes
#     les unites qui l utilisent, et OperationFinanciere.filiere est une
#     colonne STOCKEE qui ne suivrait pas. Le solde par filiere et le
#     nombre de tetes raconteraient deux histoires, definitivement.
#     J ai ecrit ce garde-fou. Il n a jamais ete execute.
#
#  2. Le referentiel qu elle modifie alimente le simulateur
#     d investissement : chaque chiffre y sort en FCFA a l ecran d un
#     eleveur.
#
#  ON TRAVAILLE SUR UN TYPE JETABLE, PAS SUR UN TYPE DE REFERENCE.
#  Si le garde-fou est casse, le scenario J31 reclasserait tout le
#  cheptel volaille de demonstration en bovins - huit jours avant la
#  soutenance. Le libelle contient [essai] pour que la decouverte des
#  identifiants l ecarte aux executions suivantes.

$ID_TYPE_ESSAI = $null
$POSTES_ESSAI = $null
if ($JA) {
    # Nom distinct de $neuf, deja employe par le scenario I14 : les
    # variables PowerShell sont de portee script, et deux blocs qui
    # partagent un nom se marchent dessus en silence. C est exactement
    # ce qui a coute l execution du 4 octobre, avec $journal et
    # $script:JOURNAL.
    $neufType = @{
        filiere = 'VOLAILLE'; libelle = "Type d essai J $MARQUE"
        modeEvaluation = 'PAR_PERIODE'; dureeCycleJours = 45
        capaciteReference = 100; espaceParSujetM2 = 0.1
        alimentParSujetKg = 1.8; eauParSujetJourL = 0.2
        vaccinsObligatoires = 'Newcastle'; tauxMortalite = 0.08
        difficulte = 'ACCESSIBLE'
        postesCout = @(
            @{ libelle = 'Poussin'; montant = 500; unite = 'sujet'; phase = 'CYCLE'
               modeQuantite = 'PAR_SUJET'; coefficient = 1.0; ordre = 1 },
            @{ libelle = 'Aliment'; montant = 450; unite = 'kg'; phase = 'CYCLE'
               modeQuantite = 'PAR_SUJET'; coefficient = 1.8; ordre = 2 },
            @{ libelle = 'Abreuvoir'; montant = 12000; unite = 'lot'; phase = 'INSTALLATION'
               modeQuantite = 'FORFAIT'; coefficient = 1.0; ordre = 1 })
    }
    $rn = Invoke-Api POST '/api/admin/types-elevage' $neufType $JA
    if ($rn.Code -eq 201) {
        $ID_TYPE_ESSAI = $rn.Corps.id
        $POSTES_ESSAI = @($rn.Corps.postesCout)
    }
}

if ($ID_TYPE_ESSAI) {
    Noter ("type d essai cree : id {0}, {1} postes de cout" -f $ID_TYPE_ESSAI, $POSTES_ESSAI.Count)

    # J30 : L ALLER-RETOUR. On relit le type, on le renvoie tel quel, et
    # on exige que rien n ait bouge. C est le scenario que tout ecran
    # d administration joue des qu un administrateur corrige une faute
    # de frappe et clique Enregistrer.
    #
    # Les identifiants des postes CHANGENT - ils sont remplaces en bloc,
    # volontairement : l ecran les envoie tous, et un rapprochement
    # ligne a ligne demanderait des identifiants stables que le
    # formulaire ne porte pas. On compare donc les libelles et les
    # montants, jamais les identifiants.
    # GARDE-FOU SUR LA RELECTURE. En PowerShell, lire une propriete sur
    # $null rend $null SANS erreur : si ce GET echouait, $allerRetour
    # serait une table de nulls, le PUT rendrait 400, et J30 a J39
    # compteraient une dizaine de faux ecarts qu on passerait la soiree
    # a chercher dans le back.
    $reluJ = (Invoke-Api GET "/api/admin/types-elevage/$ID_TYPE_ESSAI" $null $JA).Corps
    $allerRetour = $null
    if ($reluJ -and $reluJ.libelle) {
        $allerRetour = @{
            filiere = [string]$reluJ.filiere; libelle = $reluJ.libelle
            modeEvaluation = [string]$reluJ.modeEvaluation; dureeCycleJours = $reluJ.dureeCycleJours
            capaciteReference = $reluJ.capaciteReference; espaceParSujetM2 = $reluJ.espaceParSujetM2
            alimentParSujetKg = $reluJ.alimentParSujetKg; eauParSujetJourL = $reluJ.eauParSujetJourL
            vaccinsObligatoires = $reluJ.vaccinsObligatoires; tauxMortalite = $reluJ.tauxMortalite
            difficulte = [string]$reluJ.difficulte
            postesCout = @($reluJ.postesCout | ForEach-Object {
                @{ libelle = $_.libelle; montant = $_.montant; unite = $_.unite
                   phase = [string]$_.phase; modeQuantite = [string]$_.modeQuantite
                   coefficient = $_.coefficient; ordre = $_.ordre } })
        }
    }
}

if ($ID_TYPE_ESSAI -and $allerRetour) {
    Verifier 'J30' 'aller-retour identique : les 3 postes survivent' 200 (
        Invoke-Api PUT "/api/admin/types-elevage/$ID_TYPE_ESSAI" $allerRetour $JA) {
        param($c)
        if (@($c.postesCout).Count -ne 3) { return "$(@($c.postesCout).Count) postes au lieu de 3 apres un aller-retour identique" }
        $libelles = @($c.postesCout | ForEach-Object { $_.libelle })
        foreach ($attendu in @('Poussin','Aliment','Abreuvoir')) {
            if ($libelles -notcontains $attendu) { return "le poste [$attendu] a disparu" }
        }
        $null
    } | Out-Null

    # J31 : LE CORRECTIF D AUDIT N 4, enfin execute.
    #
    # On rattache d abord une unite au type d essai, pour qu il soit
    # EMPLOYE : sans unite, le changement de filiere est legitime et le
    # garde-fou ne doit justement pas se declencher. L unite est creee
    # sur le compte jetable, qui porte la filiere VOLAILLE.
    $ID_LOT_ESSAI = $null
    if ($JJ) {
        $rl = Invoke-Api POST '/api/cheptel/lots' @{
            code = "ESS-$MARQUE"; typeElevageId = $ID_TYPE_ESSAI
            effectifInitial = 10
            dateMiseEnPlace = (Get-Date).AddDays(-2).ToString('yyyy-MM-dd') } $JJ
        if ($rl.Code -eq 201) { $ID_LOT_ESSAI = $rl.Corps.id }
    }

    if ($ID_LOT_ESSAI) {
        $changeFiliere = $allerRetour.Clone()
        $changeFiliere.filiere = 'BOVINS'
        # PAR_PERIODE reste coherent : on ne veut pas qu un refus vienne
        # de la duree de cycle plutot que de la filiere.
        Verifier 'J31' 'changer la filiere d un type EMPLOYE : refuse' 422 (
            Invoke-Api PUT "/api/admin/types-elevage/$ID_TYPE_ESSAI" $changeFiliere $JA) {
            param($c)
            # 'fili.re' : le message porte l accent (filiere avec accent grave).
            if ($c -and $c.message -and $c.message -notmatch 'fili.re') {
                return "refuse, mais le message ne parle pas de filiere : [$($c.message)]"
            }
            $null
        } | Out-Null

        # J31b : la preuve que le refus a bien protege les donnees. Si le
        # garde-fou avait cede, l unite serait passee en BOVINS.
        Verifier 'J31b' 'apres le refus, l unite est toujours en volaille' 200 (
            Invoke-Api GET "/api/cheptel/$ID_LOT_ESSAI" $null $JJ) {
            param($c)
            # LE CHAMP EST IMBRIQUE. GET /api/cheptel/{uniteId} rend un
            # LotDetailReponse, dont le premier membre est l unite :
            #   { unite: { ..., filiere }, mouvements, evenements, ... }
            # La premiere version lisait $c.filiere, qui vaut $null.
            #
            # En PowerShell, lire une propriete absente ne leve rien :
            # le controle a donc annonce [le garde-fou a cede] alors
            # que J31 venait de prouver le contraire, avec le message
            # du back lui-meme. Un test dont l echec affirme l inverse
            # de la realite est pire qu un test absent - il envoie
            # chercher un defaut qui n existe pas.
            #
            # On exige donc que le champ SOIT LA avant de le juger.
            $f = [string]$c.unite.filiere
            if (-not $f) { return 'filiere absente de la reponse : chemin de lecture errone, pas un defaut du back' }
            if ($f -ne 'VOLAILLE') { return "l unite est passee en $f : le garde-fou a cede" }
            $null
        } | Out-Null
    } else {
        Ignorer 'J31' 'changement de filiere sur un type employe' 'aucune unite rattachee au type d essai'
        Ignorer 'J31b' 'l unite reste en volaille' 'aucune unite rattachee au type d essai'
    }

    # J32 et J33 : ECRITS POUR ENONCER LE COMPORTEMENT VOULU.
    #
    # Le service fait, dans cet ordre :
    #     type.getPostesCout().clear();          // orphanRemoval = true
    #     ajouterPostes(type, requete.postesCout());
    # et ajouterPostes sort en silence si la liste est null. Or
    # postesCout ne porte AUCUN @NotEmpty.
    #
    # Consequence attendue si rien ne borde ce cas : un administrateur
    # qui corrige un libelle depuis un client qui ne renvoie pas les
    # postes DETRUIT la structure de cout du type. Et derriere,
    # sujetsTenablesDansLeBudget interroge une fonction de cout qui rend
    # 0 pour tout le monde : la dichotomie monte jusqu a SUJETS_MAX. Le
    # simulateur annoncerait a l eleveur qu il peut acheter le maximum
    # de sujets pour 0 FCFA.
    #
    # Une requete qui n apporte aucun poste ne doit donc pas pouvoir
    # effacer ceux qui existent. 400 attendu.
    $sansPostes = $allerRetour.Clone()
    $sansPostes.Remove('postesCout')
    Verifier 'J32' 'PUT sans postesCout : ne doit pas pouvoir les effacer' 400 (
        Invoke-Api PUT "/api/admin/types-elevage/$ID_TYPE_ESSAI" $sansPostes $JA) | Out-Null

    Verifier 'J33' 'apres J32, les 3 postes de cout sont toujours la' 200 (
        Invoke-Api GET "/api/admin/types-elevage/$ID_TYPE_ESSAI" $null $JA) {
        param($c)
        $n = @($c.postesCout).Count
        if ($n -eq 0) { return 'structure de cout DETRUITE : le simulateur chiffrera ce type a 0 FCFA' }
        if ($n -ne 3) { return "$n postes au lieu de 3" }
        $null
    } | Out-Null

    # J34 : la liste vide, explicitement envoyee. Meme exigence : le
    # referentiel d un type sans aucun poste ne peut pas etre chiffre.
    $postesVides = $allerRetour.Clone()
    $postesVides.postesCout = @()
    Verifier 'J34' 'PUT avec une liste de postes vide' 400 (
        Invoke-Api PUT "/api/admin/types-elevage/$ID_TYPE_ESSAI" $postesVides $JA) | Out-Null

    Verifier 'J35' 'apres J34, les 3 postes de cout sont toujours la' 200 (
        Invoke-Api GET "/api/admin/types-elevage/$ID_TYPE_ESSAI" $null $JA) {
        param($c)
        $n = @($c.postesCout).Count
        if ($n -eq 0) { return 'structure de cout DETRUITE par une liste vide' }
        if ($n -ne 3) { return "$n postes au lieu de 3" }
        $null
    } | Out-Null

    # J36 : la coherence du cycle, deja verifiee a la creation, doit
    # l etre aussi a la modification. Un suivi par periode sans duree
    # rendrait clotureDuCycle(...) nulle et les rappels muets.
    $sansDuree = $allerRetour.Clone()
    $sansDuree.dureeCycleJours = $null
    Verifier 'J36' 'PAR_PERIODE sans duree de cycle' 422 (
        Invoke-Api PUT "/api/admin/types-elevage/$ID_TYPE_ESSAI" $sansDuree $JA) | Out-Null

    # J37 : tauxMortalite est une FRACTION. 0,95 signifierait 95 % de
    # pertes previsionnelles, et le simulateur conseillerait d acheter
    # vingt fois le cheptel vise.
    $mortaliteFolle = $allerRetour.Clone()
    $mortaliteFolle.tauxMortalite = 0.95
    Verifier 'J37' 'taux de mortalite previsionnel de 95 pour cent' 400 (
        Invoke-Api PUT "/api/admin/types-elevage/$ID_TYPE_ESSAI" $mortaliteFolle $JA) | Out-Null

    Verifier 'J38' 'modifier le referentiel, en eleveur' 403 (
        Invoke-Api PUT "/api/admin/types-elevage/$ID_TYPE_ESSAI" $allerRetour $JE) | Out-Null

    if ($JV) {
        Verifier 'J39' 'modifier le referentiel, en veterinaire' 403 (
            Invoke-Api PUT "/api/admin/types-elevage/$ID_TYPE_ESSAI" $allerRetour $JV) | Out-Null
    } else { Ignorer 'J39' 'referentiel en veterinaire' 'connexion veterinaire impossible' }

} else {
    foreach ($n in 'J30','J31','J31b','J32','J33','J34','J35','J36','J37','J38','J39') {
        Ignorer $n 'modification du referentiel' 'type d essai non cree ou non relu'
    }
}

if ($JA) {
    Verifier 'J40' 'modifier un type d elevage inexistant' 404 (
        Invoke-Api PUT '/api/admin/types-elevage/999999' @{
            filiere = 'VOLAILLE'; libelle = 'Inexistant'; modeEvaluation = 'PAR_ENTREE'
            capaciteReference = 10; tauxMortalite = 0.05; difficulte = 'ACCESSIBLE'
            postesCout = @(@{ libelle = 'Poste'; montant = 100; unite = 'sujet'
                              phase = 'CYCLE'; modeQuantite = 'PAR_SUJET'
                              coefficient = 1.0; ordre = 1 }) } $JA) | Out-Null
} else { Ignorer 'J40' 'type d elevage inexistant' 'connexion administrateur impossible' }

# --- J41 a J48 : SUPPRESSION D UN TYPE D ELEVAGE (6 octobre) -----------
#
# Maquette " Admin - Referentiel fiche - POPUP Supprimer " : " Ce type
# disparaitra de la page Logistique et du simulateur. Les lots deja crees
# ne sont pas touches. " Suppression LOGIQUE : actif = false.
#
# C est aussi le MENAGE de ce script. Avant cette route, chaque execution
# laissait deux types d essai actifs (F24 et J), et la liste " Type
# d elevage " de l ecran Ajouter s allongeait de deux lignes a chaque
# passage. Les deux types crees ici sont donc supprimes en fin de bloc.
if ($JA -and $ID_TYPE_ESSAI) {
    Verifier 'J41' 'supprimer un type d elevage, en eleveur' 403 (
        Invoke-Api DELETE "/api/admin/types-elevage/$ID_TYPE_ESSAI" $null $JE) | Out-Null
    # Depuis le 9 octobre : DELETE = suppression DEFINITIVE (409 si un
    # elevage emploie le type) ; POST .../retrait = suppression logique.
    Verifier 'J42' 'retrait du type d essai' 204 (
        Invoke-Api POST "/api/admin/types-elevage/$ID_TYPE_ESSAI/retrait" $null $JA) | Out-Null
    Verifier 'J43' 'retrait rejoue : 204 (idempotence)' 204 (
        Invoke-Api POST "/api/admin/types-elevage/$ID_TYPE_ESSAI/retrait" $null $JA) | Out-Null
    if ($ID_LOT_ESSAI) {
        Verifier 'J43b' 'suppression definitive d un type employe par un lot : refusee' 409 (
            Invoke-Api DELETE "/api/admin/types-elevage/$ID_TYPE_ESSAI" $null $JA) | Out-Null
    }

    Verifier 'J44' 'le type supprime n est plus propose a l eleveur' 200 (
        Invoke-Api GET '/api/types-elevage?filiere=VOLAILLE' $null $JE) {
        param($c)
        if ($c | Where-Object { $_.id -eq $ID_TYPE_ESSAI }) { return 'le type supprime est encore dans la liste eleveur' }
        if (-not ($c | Where-Object { $_.libelle -eq 'Poulet de chair' })) { return 'Poulet de chair a disparu de la liste' }
        $null
    } | Out-Null
    Verifier 'J45' 'le type retire reste dans le referentiel administration, inactif' 200 (
        Invoke-Api GET "/api/admin/types-elevage/$ID_TYPE_ESSAI" $null $JA) {
        param($c) if ($c.actif) { 'le type retire est encore actif' } else { $null } } | Out-Null

    if ($JJ) {
        Verifier 'J46' 'creer un lot sur un type supprime : refuse' 422 (
            Invoke-Api POST '/api/cheptel/lots' @{
                code = "ESX-$MARQUE"; typeElevageId = $ID_TYPE_ESSAI; effectifInitial = 10
                dateMiseEnPlace = (Get-Date).AddDays(-1).ToString('yyyy-MM-dd') } $JJ) | Out-Null
    } else { Ignorer 'J46' 'lot sur un type supprime' 'pas de compte jetable' }

    if ($JJ -and $ID_LOT_ESSAI) {
        Verifier 'J47' 'le lot deja cree sur ce type reste lisible' 200 (
            Invoke-Api GET "/api/cheptel/$ID_LOT_ESSAI" $null $JJ) | Out-Null
    } else { Ignorer 'J47' 'lot existant sur un type supprime' 'aucun lot rattache au type d essai' }
} else {
    foreach ($n in 'J41','J42','J43','J43b','J44','J45','J46','J47') {
        Ignorer $n 'suppression d un type d elevage' 'type d essai non cree ou connexion administrateur impossible'
    }
}

if ($JA) {
    Verifier 'J48' 'supprimer un type d elevage inexistant' 404 (
        Invoke-Api DELETE '/api/admin/types-elevage/999999' $null $JA) | Out-Null
    # Menage du type cree par F24 : suppression definitive s il n a servi
    # a aucun elevage, retrait sinon.
    if ($ID_TYPE_F24) {
        $rs = Invoke-Api DELETE "/api/admin/types-elevage/$ID_TYPE_F24" $null $JA
        Verifier 'J49' 'suppression definitive d un type jamais employe (ou retrait s il l est)' @(204,409) $rs | Out-Null
        if ($rs.Code -eq 409) { $null = Invoke-Api POST "/api/admin/types-elevage/$ID_TYPE_F24/retrait" $null $JA }
    }
} else { Ignorer 'J48' 'type d elevage inexistant' 'connexion administrateur impossible' }

# =====================================================================
#  K - REVOCATION DES JETONS ET CHRONOMETRE (lot 2b, 5 octobre)
# =====================================================================
#
#  Deux defauts de securite que les blocs precedents ne pouvaient pas voir,
#  parce qu'ils ne se manifestent ni par un code ni par un message :
#
#  1. REVOCATION SANS EFFET. Changer son mot de passe positionnait
#     Session.revoquee, que rien ne lisait. Un jeton d'acces vole restait
#     valable jusqu'a une heure apres le geste qui devait le couper.
#     Un jeton emis avant un changement de mot de passe doit etre refuse,
#     QUEL QUE SOIT le chemin du changement : K04 (par l'utilisateur) et
#     K09 (par l'administration) le verifient separement.
#
#  2. ENUMERATION PAR LE TEMPS DE REPONSE. Le message d'erreur etait le meme
#     pour une adresse inconnue et un mauvais mot de passe ; la duree, non :
#     quelques millisecondes d'un cote, une verification BCrypt de l'autre.
#     K10 compare les deux. C'est une mesure, pas une assertion exacte : un
#     ratio, sur la mediane de trois essais, avec une marge large.
#
#  La partie SMTP du second defaut (l'envoi du code, qui prenait environ
#  une seconde) n'est pas mesurable ici : le profil dev ecrit dans un
#  journal, sans reseau. Elle est corrigee dans ServiceCourrielSmtp mais ne
#  sera prouvee que par un envoi reel.
Bloc 'K - Revocation des jetons et chronometre'

if ($JA) {
    $mailK = "revoc-$MARQUE@example.ml"
    $mdpK1 = 'jetable2026'
    $mdpK2 = 'change2026'
    $rK = Invoke-Api POST '/api/auth/inscription' @{
        nom = "Compte Revoc $MARQUE"; email = $mailK; zoneId = $ZONE_ID
        filieres = @('VOLAILLE'); motDePasse = $mdpK1 }
    $kOk = Verifier 'K01' 'inscription d un compte jetable pour la revocation' 201 $rK {
        param($c) if (-not $c.jetonAcces) { 'aucun jeton d acces rendu' } else { $null } }
    $jetonK1 = $null; $idK = $null
    if ($kOk) { $jetonK1 = $rK.Corps.jetonAcces; $idK = $rK.Corps.utilisateurId }

    if ($jetonK1) {
        Verifier 'K02' 'le jeton fraichement emis ouvre /api/auth/moi' 200 (
            Invoke-Api GET '/api/auth/moi' $null $jetonK1) | Out-Null

        $rChg = Invoke-Api POST '/api/auth/mot-de-passe' @{
            ancienMotDePasse = $mdpK1; nouveauMotDePasse = $mdpK2 } $jetonK1
        $chgOk = Verifier 'K03' 'changement du mot de passe par l utilisateur' 204 $rChg

        if ($chgOk) {
            Verifier 'K04' 'l ancien jeton d acces est REFUSE apres le changement' 401 (
                Invoke-Api GET '/api/auth/moi' $null $jetonK1) | Out-Null

            $rNew = Invoke-Api POST '/api/auth/connexion' @{ email = $mailK; motDePasse = $mdpK2 }
            $newOk = Verifier 'K05' 'connexion avec le nouveau mot de passe' 200 $rNew {
                param($c) if (-not $c.jetonAcces) { 'aucun jeton d acces rendu' } else { $null } }
            $jetonK2 = $null
            if ($newOk) { $jetonK2 = $rNew.Corps.jetonAcces }

            if ($jetonK2) {
                Verifier 'K06' 'le nouveau jeton ouvre /api/auth/moi' 200 (
                    Invoke-Api GET '/api/auth/moi' $null $jetonK2) | Out-Null
                Verifier 'K07' 'une reconnexion ne rend pas son acces a l ancien jeton' 401 (
                    Invoke-Api GET '/api/auth/moi' $null $jetonK1) | Out-Null

                # K08/K09 : l AUTRE chemin, celui que l administration emprunte
                # pour changer un mot de passe. Le correctif repose sur
                # l empreinte du mot de passe, donc sur aucun chemin en
                # particulier : ce scenario le demontre au lieu de l affirmer.
                if ($idK) {
                    Verifier 'K08' 'l administration reinitialise le mot de passe' 204 (
                        Invoke-Api POST "/api/admin/utilisateurs/$idK/mot-de-passe" $null $JA) | Out-Null
                    Verifier 'K09' 'le jeton emis avant la reinitialisation est REFUSE' 401 (
                        Invoke-Api GET '/api/auth/moi' $null $jetonK2) | Out-Null
                } else {
                    foreach ($n in 'K08','K09') { Ignorer $n 'reinitialisation par l administration' 'identifiant du compte jetable inconnu' }
                }
            } else {
                foreach ($n in 'K06','K07','K08','K09') { Ignorer $n 'jeton apres changement' 'K05 a echoue' }
            }
        } else {
            foreach ($n in 'K04','K05','K06','K07','K08','K09') { Ignorer $n 'jeton apres changement' 'K03 a echoue' }
        }
    } else {
        foreach ($n in 'K02','K03','K04','K05','K06','K07','K08','K09') { Ignorer $n 'revocation des jetons' 'K01 a echoue' }
    }

    # K10 : le chronometre. Trois essais de chaque cote, mediane, rapport.
    # Un mauvais mot de passe sur le compte jetable : trois echecs, sous le
    # seuil de verrouillage (cinq), sur un compte qui ne sert plus ensuite.
    $dureesInconnu = @(); $dureesConnu = @()
    foreach ($essai in 1..3) {
        $chrono = [System.Diagnostics.Stopwatch]::StartNew()
        Invoke-Api POST '/api/auth/connexion' @{
            email = "inconnu$essai-$MARQUE@example.ml"; motDePasse = 'nimporte2026' } | Out-Null
        $chrono.Stop(); $dureesInconnu += $chrono.Elapsed.TotalMilliseconds

        $chrono = [System.Diagnostics.Stopwatch]::StartNew()
        Invoke-Api POST '/api/auth/connexion' @{
            email = $mailK; motDePasse = "faux$essai-faux2026" } | Out-Null
        $chrono.Stop(); $dureesConnu += $chrono.Elapsed.TotalMilliseconds
    }
    $medInconnu = ($dureesInconnu | Sort-Object)[1]
    $medConnu   = ($dureesConnu   | Sort-Object)[1]
    Noter ("chronometre : adresse inconnue {0:N0} ms, adresse connue {1:N0} ms (medianes de 3)" -f $medInconnu, $medConnu)

    if ($medConnu -lt 50) {
        Ignorer 'K10' 'chronometre : adresse inconnue contre adresse connue' (
            "la verification du mot de passe est trop rapide pour etre mesuree ({0:N0} ms)" -f $medConnu)
    } else {
        Verifier 'K10' 'chronometre : une adresse inconnue n est pas plus rapide qu une adresse connue' 200 (
            [pscustomobject]@{ Code = 200; Corps = [pscustomobject]@{ inconnu = $medInconnu; connu = $medConnu } }) {
            param($c)
            if ($c.inconnu -lt (0.5 * $c.connu)) {
                return ("adresse inconnue : {0:N0} ms ; adresse connue : {1:N0} ms - le temps de reponse distingue les comptes" -f $c.inconnu, $c.connu)
            }
            $null
        } | Out-Null
    }
} else {
    foreach ($n in 'K01','K02','K03','K04','K05','K06','K07','K08','K09','K10') {
        Ignorer $n 'revocation des jetons et chronometre' 'connexion administrateur impossible'
    }
}

# =====================================================================
#  L - RAFRAICHISSEMENT DE SESSION (POST /api/auth/rafraichir)
# =====================================================================
#
#  Le serveur remettait un jeton de rafraichissement a la connexion, et
#  aucune route ne l'acceptait : toute session mourait au bout d'une heure.
#
#  Ce que le bloc doit prouver, et pourquoi chaque scenario existe :
#   L02  un rafraichissement FAIT DANS LA SECONDE de la connexion reussit.
#        Sans identifiant unique dans le jeton, les deux jetons seraient
#        identiques, donc leurs empreintes aussi, et la contrainte d unicite
#        de la table session ferait echouer la requete.
#   L04  le jeton est a usage unique (rotation) : rejoue, il est refuse.
#   L09  apres une deconnexion, le jeton ne rafraichit plus.
#   L10  apres un changement de mot de passe, il ne rafraichit plus non plus.
#   L11  un compte desactive ne se rafraichit plus.
Bloc 'L - Rafraichissement de session'

$mailL = "rafr-$MARQUE@example.ml"
$mdpL1 = 'jetable2026'
$mdpL2 = 'change2026'
$repL1 = Invoke-Api POST '/api/auth/inscription' @{
    nom = "Compte Rafraichissement $MARQUE"; email = $mailL; zoneId = $ZONE_ID
    filieres = @('VOLAILLE'); motDePasse = $mdpL1 }
$lOk = Verifier 'L01' 'inscription d un compte jetable pour le rafraichissement' 201 $repL1 {
    param($c) if (-not $c.jetonRafraichissement) { 'aucun jeton de rafraichissement rendu' } else { $null } }
$jRaf0 = $null
if ($lOk) { $jRaf0 = $repL1.Corps.jetonRafraichissement }

if ($jRaf0) {
    # L02 : immediatement, dans la meme seconde que l inscription.
    $repL2 = Invoke-Api POST '/api/auth/rafraichir' @{ jetonRafraichissement = $jRaf0 }
    $l2 = Verifier 'L02' 'rafraichissement immediat : nouveaux jetons, et le jeton change' 200 $repL2 {
        param($c)
        if (-not $c.jetonAcces) { return 'aucun jeton d acces rendu' }
        if (-not $c.jetonRafraichissement) { return 'aucun jeton de rafraichissement rendu' }
        if ($c.jetonRafraichissement -eq $jRaf0) { return 'le jeton de rafraichissement n a pas change' }
        $null }
    $jRaf1 = $null; $jAcc1 = $null
    if ($l2) { $jRaf1 = $repL2.Corps.jetonRafraichissement; $jAcc1 = $repL2.Corps.jetonAcces }

    if ($jAcc1) {
        Verifier 'L03' 'le nouveau jeton d acces ouvre /api/auth/moi' 200 (
            Invoke-Api GET '/api/auth/moi' $null $jAcc1) | Out-Null
    } else { Ignorer 'L03' 'nouveau jeton d acces' 'L02 a echoue' }

    # L04 : rotation. Le premier jeton a servi, il ne sert plus.
    Verifier 'L04' 'le jeton deja utilise est REFUSE (usage unique)' 422 (
        Invoke-Api POST '/api/auth/rafraichir' @{ jetonRafraichissement = $jRaf0 }) {
        param($c) if ($c.message -notlike '*Reconnectez*') { "message inattendu : [$($c.message)]" } else { $null } } | Out-Null

    # L05 : la chaine continue avec le jeton le plus recent.
    $jRaf2 = $null
    if ($jRaf1) {
        $repL5 = Invoke-Api POST '/api/auth/rafraichir' @{ jetonRafraichissement = $jRaf1 }
        $l5 = Verifier 'L05' 'le jeton le plus recent rafraichit a son tour' 200 $repL5 {
            param($c) if (-not $c.jetonRafraichissement) { 'aucun jeton rendu' } else { $null } }
        if ($l5) { $jRaf2 = $repL5.Corps.jetonRafraichissement }
    } else { Ignorer 'L05' 'chaine de rafraichissements' 'L02 a echoue' }

    Verifier 'L06' 'jeton quelconque : refuse, meme message' 422 (
        Invoke-Api POST '/api/auth/rafraichir' @{ jetonRafraichissement = 'pas.un.jeton' }) {
        param($c) if ($c.message -notlike '*Reconnectez*') { "message inattendu : [$($c.message)]" } else { $null } } | Out-Null

    if ($jAcc1) {
        Verifier 'L07' 'un jeton d acces presente comme jeton de rafraichissement' 422 (
            Invoke-Api POST '/api/auth/rafraichir' @{ jetonRafraichissement = $jAcc1 }) | Out-Null
    } else { Ignorer 'L07' 'jeton d acces comme rafraichissement' 'L02 a echoue' }

    Verifier 'L08' 'jeton de rafraichissement vide' 400 (
        Invoke-Api POST '/api/auth/rafraichir' @{ jetonRafraichissement = '' }) | Out-Null

    # L09 : deconnexion, puis tentative.
    # Le jeton de rafraichissement voyage dans l en-tete X-Refresh-Token.
    # Depuis le 10 octobre la route est OUVERTE : le jeton de rafraichissement
    # suffit, et L09b le prouve sans jeton d acces.
    if ($jRaf2) {
        Verifier 'L09a' 'deconnexion avec le jeton de rafraichissement' 204 (
            Invoke-Api POST '/api/auth/deconnexion' $null $jAcc1 @{ 'X-Refresh-Token' = $jRaf2 }) | Out-Null
        Verifier 'L09' 'apres la deconnexion, le jeton ne rafraichit plus' 422 (
            Invoke-Api POST '/api/auth/rafraichir' @{ jetonRafraichissement = $jRaf2 }) | Out-Null
    } else { foreach ($n in 'L09a','L09') { Ignorer $n 'deconnexion puis rafraichissement' 'L05 a echoue' } }

    # L09b : deconnexion SANS jeton d acces (jeton expire, ou console admin).
    $repL9b = Invoke-Api POST '/api/auth/connexion' @{ email = $mailL; motDePasse = $mdpL1 }
    if ($repL9b.Code -eq 200 -and $repL9b.Corps.jetonRafraichissement) {
        $jRaf9b = $repL9b.Corps.jetonRafraichissement
        Verifier 'L09b' 'deconnexion sans jeton d acces : 204' 204 (
            Invoke-Api POST '/api/auth/deconnexion' $null $null @{ 'X-Refresh-Token' = $jRaf9b }) | Out-Null
        Verifier 'L09c' 'apres L09b, le jeton ne rafraichit plus' 422 (
            Invoke-Api POST '/api/auth/rafraichir' @{ jetonRafraichissement = $jRaf9b }) | Out-Null
    } else { foreach ($n in 'L09b','L09c') { Ignorer $n 'deconnexion sans jeton d acces' 'connexion impossible' } }

    # L10 : changement de mot de passe.
    $repL10 = Invoke-Api POST '/api/auth/connexion' @{ email = $mailL; motDePasse = $mdpL1 }
    if ($repL10.Code -eq 200 -and $repL10.Corps.jetonAcces) {
        $jRaf3 = $repL10.Corps.jetonRafraichissement
        $chg = Invoke-Api POST '/api/auth/mot-de-passe' @{
            ancienMotDePasse = $mdpL1; nouveauMotDePasse = $mdpL2 } $repL10.Corps.jetonAcces
        if ($chg.Code -eq 204) {
            Verifier 'L10' 'apres un changement de mot de passe, le jeton ne rafraichit plus' 422 (
                Invoke-Api POST '/api/auth/rafraichir' @{ jetonRafraichissement = $jRaf3 }) | Out-Null
        } else { Ignorer 'L10' 'rafraichissement apres changement de mot de passe' "le changement a rendu $($chg.Code)" }
    } else { Ignorer 'L10' 'rafraichissement apres changement de mot de passe' 'connexion du compte jetable impossible' }

    # L11 : compte desactive. Il faut l administration.
    if ($JA) {
        $repL11 = Invoke-Api POST '/api/auth/connexion' @{ email = $mailL; motDePasse = $mdpL2 }
        if ($repL11.Code -eq 200 -and $repL11.Corps.utilisateurId) {
            $jRaf4 = $repL11.Corps.jetonRafraichissement; $idL = $repL11.Corps.utilisateurId
            $des = Invoke-Api POST "/api/admin/utilisateurs/$idL/activation?actif=false" $null $JA
            if ($des.Code -eq 204) {
                Verifier 'L11' 'un compte desactive ne se rafraichit plus' 422 (
                    Invoke-Api POST '/api/auth/rafraichir' @{ jetonRafraichissement = $jRaf4 }) | Out-Null
                Invoke-Api POST "/api/admin/utilisateurs/$idL/activation?actif=true" $null $JA | Out-Null
            } else { Ignorer 'L11' 'rafraichissement d un compte desactive' "la desactivation a rendu $($des.Code)" }
        } else { Ignorer 'L11' 'rafraichissement d un compte desactive' 'connexion avec le nouveau mot de passe impossible' }
    } else { Ignorer 'L11' 'rafraichissement d un compte desactive' 'connexion administrateur impossible' }
} else {
    foreach ($n in 'L02','L03','L04','L05','L06','L07','L08','L09a','L09','L10','L11') {
        Ignorer $n 'rafraichissement de session' 'L01 a echoue'
    }
}

# =====================================================================
#  BILAN
# =====================================================================
$ok     = @($script:Resultats | Where-Object { $_.Statut -eq 'OK' })
$ecarts = @($script:Resultats | Where-Object { $_.Statut -eq 'ECART' })
$ign    = @($script:Resultats | Where-Object { $_.Statut -eq 'IGNORE' })
$main   = @($script:Resultats | Where-Object { $_.Statut -eq 'A LA MAIN' })

Write-Host ''
Write-Host ('=' * 70) -ForegroundColor White
Write-Host 'BILAN' -ForegroundColor White
Write-Host ('=' * 70) -ForegroundColor White
Write-Host ("  conformes   : {0}" -f $ok.Count)     -ForegroundColor Green
Write-Host ("  ecarts      : {0}" -f $ecarts.Count) -ForegroundColor $(if ($ecarts.Count) { 'Red' } else { 'Green' })
Write-Host ("  ignores     : {0}" -f $ign.Count)    -ForegroundColor DarkGray
Write-Host ("  a la main   : {0}" -f $main.Count)   -ForegroundColor Magenta
Write-Host ("  total joue  : {0}" -f $script:Resultats.Count)

if ($ecarts.Count) {
    Write-Host ''
    Write-Host 'LES ECARTS, PAR BLOC' -ForegroundColor Red
    foreach ($b in ($ecarts | Select-Object -ExpandProperty Bloc -Unique)) {
        Write-Host ''
        Write-Host ("  $b") -ForegroundColor Cyan
        foreach ($e in ($ecarts | Where-Object { $_.Bloc -eq $b })) {
            Write-Host ("    {0,-5} {1}" -f $e.Numero, $e.Intitule) -ForegroundColor Red
            Write-Host ("          {0}" -f $e.Detail) -ForegroundColor Yellow
        }
    }
}

if ($ign.Count) {
    Write-Host ''
    Write-Host 'IGNORES (prerequis manquant - ce ne sont pas des echecs)' -ForegroundColor DarkGray
    foreach ($i in $ign) { Write-Host ("    {0,-5} {1} - {2}" -f $i.Numero, $i.Intitule, $i.Detail) -ForegroundColor DarkGray }
}

if ($main.Count) {
    Write-Host ''
    Write-Host 'A JOUER A LA MAIN (lecture de la console du serveur)' -ForegroundColor Magenta
    foreach ($m in $main) { Write-Host ("    {0,-5} {1}" -f $m.Numero, $m.Intitule) -ForegroundColor Magenta }
}

$fichier = Join-Path (Get-Location) ("resultats-back-{0}.csv" -f $MARQUE)
try {
    $script:Resultats | Select-Object Numero, Bloc, Intitule, Statut, Detail |
        Export-Csv -Path $fichier -NoTypeInformation -Encoding UTF8
    Write-Host ''
    Write-Host "Detail complet ecrit dans : $fichier" -ForegroundColor White
    Write-Host 'Envoyez ce fichier, ou recopiez la section BILAN ci-dessus.' -ForegroundColor White
}
catch {
    Write-Host ''
    Write-Host "Impossible d ecrire le CSV : $($_.Exception.Message)" -ForegroundColor Yellow
    Write-Host 'Recopiez la section BILAN ci-dessus.' -ForegroundColor Yellow
}

Write-Host ''
if ($ecarts.Count -eq 0) {
    Write-Host 'Aucun ecart. Les scenarios joues se comportent comme prevu.' -ForegroundColor Green
} else {
    Write-Host ("{0} ecart(s) a examiner." -f $ecarts.Count) -ForegroundColor Red
}
Write-Host ''
exit 0
