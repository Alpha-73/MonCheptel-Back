# =====================================================================
#  MonCheptel - lancement du back
#
#  A lancer depuis le dossier api :
#
#      .\lancer.ps1
#
#  Les deux caracteres .\ sont obligatoires : PowerShell ne lance
#  jamais un script du dossier courant sans chemin explicite.
#
#  Si Windows refuse pour cause de politique d'execution :
#      Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#  puis relancez. Le -Scope Process ne change rien au-dela de cette
#  fenetre de terminal.
#
#
#  UNE SEULE VARIABLE A FOURNIR : le mot de passe MySQL. Ce script la
#  demande et la pose lui-meme.
#
#  Il n'y a RIEN a faire pour le secret JWT. application.yml le lit
#  bien dans MONCHEPTEL_JWT_SECRET, sans valeur par defaut - mais le
#  profil dev porte son propre secret (application-dev.yml), et un
#  fichier de profil est PRIORITAIRE sur application.yml. En dev, une
#  variable MONCHEPTEL_JWT_SECRET serait donc simplement ignoree.
#  Elle ne devient obligatoire qu'en production, hors profil dev.
#
#
#  NOTE SUR L'ECRITURE DE CE SCRIPT
#  --------------------------------
#  Aucune chaine de ce fichier ne contient de guillemet double
#  echappe, ni de backtick, ni de concatenation sur plusieurs lignes.
#  La version precedente ecrivait
#
#      Echouer ("texte" + "autre texte ""entre guillemets""")
#
#  en comptant sur le doublement du guillemet pour l'echapper. Cela
#  n'a pas ete accepte : la chaine se fermait au premier guillemet du
#  couple, une nouvelle s'ouvrait au second, la derniere restait
#  ouverte jusqu'au bout de la ligne - et le parseur signalait des
#  erreurs sur les lignes voisines, loin de la vraie cause.
#
#  Les messages sur plusieurs lignes passent maintenant par des
#  here-strings @' ... '@, qui prennent tout au pied de la lettre,
#  guillemets compris. Une seule contrainte, respectee ici : le '@ de
#  fermeture doit etre en TOUT DEBUT DE LIGNE, sans espace avant.
# =====================================================================

$ErrorActionPreference = 'Continue'

function Etape($numero, $titre) {
    Write-Host ''
    Write-Host "=== $numero  $titre " -ForegroundColor Cyan
}

function Echouer($message) {
    Write-Host ''
    Write-Host 'ARRET' -ForegroundColor Red
    Write-Host $message -ForegroundColor Red
    Write-Host ''
    exit 1
}

# --- On doit etre dans le dossier api -------------------------------------
if (-not (Test-Path 'pom.xml')) {
    $m = @'
pom.xml introuvable. Lancez ce script depuis le dossier api, par exemple :
    cd C:\Users\balla\Downloads\moncheptel-back\api
    .\lancer.ps1
'@
    Echouer $m
}

# --- 1. Maven et Java -----------------------------------------------------
Etape '1/4' 'Maven et Java'
$version = (mvn -version 2>&1) -join [Environment]::NewLine
if ($LASTEXITCODE -ne 0) {
    Echouer 'Maven ne repond pas. Est-il installe et dans le PATH ?'
}
$ligneJava = ($version -split [Environment]::NewLine | Where-Object { $_ -match 'Java version' })
Write-Host $ligneJava
if (-not $ligneJava) {
    Echouer 'Maven ne trouve pas de JDK. Verifiez la variable JAVA_HOME.'
}

# --- 2. Mot de passe MySQL ------------------------------------------------
Etape '2/4' 'Mot de passe MySQL'
if ($env:MONCHEPTEL_DB_PASSWORD) {
    Write-Host 'Deja defini dans cette fenetre - on le reutilise.'
} else {
    # -AsSecureString : le mot de passe ne s'affiche pas et n'entre pas
    # dans l'historique de PowerShell.
    $secret = Read-Host 'Mot de passe du root MySQL (rien = mot de passe vide)' -AsSecureString
    $brut = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR(
        [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret))
    $env:MONCHEPTEL_DB_PASSWORD = $brut
    Write-Host 'Enregistre pour cette fenetre de terminal.'
}

# --- 3. La base -----------------------------------------------------------
Etape '3/4' 'Base de donnees moncheptel_dev'
$mysqlPresent = $null -ne (Get-Command mysql -ErrorAction SilentlyContinue)
if ($mysqlPresent) {
    # MYSQL_PWD evite l'invite interactive de mysql.exe.
    $env:MYSQL_PWD = $env:MONCHEPTEL_DB_PASSWORD
    $sql = 'CREATE DATABASE IF NOT EXISTS moncheptel_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
    mysql -u root -e $sql 2>&1 |
        Where-Object { $_ -notmatch 'Using a password on the command line' } |
        ForEach-Object { Write-Host $_ }
    $code = $LASTEXITCODE
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue

    if ($code -ne 0) {
        $m = @'
MySQL a refuse la connexion. Mauvais mot de passe, ou service arrete.
  Verifier le service :  Get-Service MySQL*
  Reessayer a la main :  mysql -u root -p
'@
        Echouer $m
    }
    Write-Host 'Base prete.'
} else {
    Write-Host 'mysql.exe absent du PATH - on laisse le pilote JDBC creer la base.'
    Write-Host "(l'URL du profil dev contient createDatabaseIfNotExist=true)"
}

# --- 4. Demarrage ---------------------------------------------------------
Etape '4/4' "Demarrage de l'application"

$avis = @'
Profil : dev
Swagger : http://localhost:8080/swagger-ui.html
Ctrl+C pour arreter.

LES CINQ LIGNES A SURVEILLER
  1. The following 1 profile is active: "dev"
  2. Successfully applied 5 migrations        <- 5 depuis V5
     (ou "Successfully validated 5 migrations" si la base est deja
      a jour : les deux sont bons)
  3. WARN ... les courriels seront AFFICHES DANS CETTE CONSOLE
     <- confirme le repli courriel. Sans cette ligne, le bloc H des
        tests ne prouvera rien.
  4. aucune QuerySyntaxException
  5. Started MonCheptelApplication

GARDEZ CETTE FENETRE OUVERTE ET VISIBLE.
Les codes a six chiffres et les mots de passe provisoires s'affichent
ici, dans un cadre.

Pour les tests, ouvrez une AUTRE fenetre PowerShell et lancez
    .\test-back-complet.ps1
'@
Write-Host $avis -ForegroundColor Yellow
Write-Host ''

# Les guillemets autour de -D sont indispensables : sans eux
# PowerShell coupe l'argument au premier point.
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
