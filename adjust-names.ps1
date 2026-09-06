#!/usr/bin/env pwsh
# -- adjust-names.ps1 ----------------------------------------------------------
# Rename this template IN PLACE to your own mod. Run it once, right after you
# create a repository from the template (or clone it), then delete it (the
# script offers to remove itself and the bootstrap workflow when it finishes).
#
# It accepts a free-form mod name and derives every naming form the project
# needs, keeping Java/Fabric/NeoForge conventions:
#
#   "My Funny Minecraft Mod"  ->  display     My Funny Minecraft Mod
#                                 fabric id   my-funny-minecraft-mod   (kebab)
#                                 neoforge id my_funny_minecraft_mod   (snake)
#                                 package seg myfunnyminecraftmod
#                                 class base  MyFunnyMinecraftMod
#
# Usage:
#   ./adjust-names.ps1 -Name "My Funny Minecraft Mod" -Owner my-github-user
#   ./adjust-names.ps1 -Name "My Mod" -Package org.example.mymod
#
# This is a faithful PowerShell port of adjust-names.sh (the source of truth).
# ------------------------------------------------------------------------------
[CmdletBinding()]
param(
    [string]$Name,
    [string]$Owner,
    [string]$Package,
    [string]$GroupPrefix,
    [string]$Repo,
    [switch]$KeepTooling,
    [Alias('y')]
    [switch]$Yes,
    [Alias('h')]
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

# -- Placeholders shipped by the template (do not change) ----------------------
$PH_PACKAGE      = 'com.example.examplemod'
$PH_PACKAGE_PATH = 'com/example/examplemod'
$PH_CLASS        = 'ExampleMod'
$PH_DISPLAY      = 'Example Mod'
$PH_KEBAB        = 'example-mod'
$PH_SNAKE        = 'example_mod'
$PH_OWNER        = 'example-owner'

function Show-Usage {
    @'

adjust-names.ps1 - rename this template in place to your own mod.

Options:
  -Name  <str>        Mod name, free-form. Required.
  -Owner <str>        Your GitHub user/org. Builds the package prefix
                      io.github.<owner> and fills in repository URLs.
  -Package <pkg>      Full base package override, e.g. org.example.mymod.
                      Takes precedence over -Owner/-GroupPrefix.
  -GroupPrefix <p>    Package prefix to use instead of io.github.<owner>.
  -Repo <name>        Repository name for URLs (default: the fabric/kebab id).
  -KeepTooling        Do NOT delete adjust-names.* / bootstrap workflow after.
  -Yes                Do not prompt for confirmation.
  -Help               Show this help.
'@ | Write-Output
}

function Die([string]$message) {
    [Console]::Error.WriteLine($message)
    exit 1
}

function Warn([string]$message) {
    [Console]::Error.WriteLine($message)
}

if ($Help) { Show-Usage; exit 0 }

if ([string]::IsNullOrEmpty($Name)) {
    [Console]::Error.WriteLine('Error: -Name is required.')
    Show-Usage
    exit 1
}

# -- Helpers -------------------------------------------------------------------
$JAVA_KEYWORDS = @(
    'abstract','assert','boolean','break','byte','case','catch','char','class',
    'const','continue','default','do','double','else','enum','extends','final',
    'finally','float','for','goto','if','implements','import','instanceof','int',
    'interface','long','native','new','package','private','protected','public',
    'return','short','static','strictfp','super','switch','synchronized','this',
    'throw','throws','transient','try','void','volatile','while','true','false',
    'null','var','record','sealed','permits','yield'
)

function Convert-Capitalize([string]$w) {
    if ([string]::IsNullOrEmpty($w)) { return '' }
    return $w.Substring(0, 1).ToUpperInvariant() + $w.Substring(1).ToLowerInvariant()
}

# -- Normalize the mod name into every form -----------------------------------
if ($Name -match '[^\x20-\x7e]') {
    Warn 'Warning: non-ASCII characters in -Name will be dropped.'
}

# Split on camelCase boundaries (lowercase/digit -> uppercase), then turn every
# non-alphanumeric character into a space, matching the bash sed/tr pipeline.
$spaced = [regex]::Replace($Name, '([a-z0-9])([A-Z])', '$1 $2')
$spaced = [regex]::Replace($spaced, '[^A-Za-z0-9]', ' ')

$words = @()
foreach ($w in ($spaced -split '\s+')) {
    if ($w -ne '') { $words += $w.ToLowerInvariant() }
}
if ($words.Count -eq 0) {
    Die "Error: -Name '$Name' has no usable characters."
}

# Strip leading digits from the first word (Java identifiers cannot start with a digit).
$first = $words[0] -replace '^[0-9]+', ''
if ($first -eq '') {
    if ($words.Count -gt 1) {
        $words = $words[1..($words.Count - 1)]
    }
    else {
        $words = @()
    }
    if ($words.Count -eq 0) {
        Die "Error: -Name '$Name' has no letter-leading word (Java identifiers cannot start with a digit)."
    }
}
else {
    $words[0] = $first
}

$JOINED = ''; $KEBAB = ''; $SNAKE = ''; $PASCAL = ''; $DISPLAY = ''; $i = 0
foreach ($w in $words) {
    $JOINED = "$JOINED$w"
    $PASCAL = "$PASCAL$(Convert-Capitalize $w)"
    if ($i -eq 0) {
        $KEBAB = $w
        $SNAKE = $w
        $DISPLAY = (Convert-Capitalize $w)
    }
    else {
        $KEBAB = "$KEBAB-$w"
        $SNAKE = "${SNAKE}_$w"
        $DISPLAY = "$DISPLAY $(Convert-Capitalize $w)"
    }
    $i++
}

# -- Validate derived identifiers ---------------------------------------------
if ($JOINED -notmatch '^[a-z]') {
    Die "Error: package segment '$JOINED' must start with a lowercase letter."
}
if ($JAVA_KEYWORDS -contains $JOINED) {
    Die "Error: package segment '$JOINED' is a Java keyword."
}
if ($JAVA_KEYWORDS -contains $PASCAL.ToLowerInvariant()) {
    Die "Error: class name '$PASCAL' collides with a Java keyword."
}
if (-not [regex]::IsMatch($SNAKE, '^[a-z][a-z0-9_]{1,63}$')) {
    Die "Error: neoforge mod id '$SNAKE' must match ^[a-z][a-z0-9_]{1,63}`$ (2-64 chars, letter first)."
}

# -- Resolve package / owner / repo -------------------------------------------
if (-not [string]::IsNullOrEmpty($Package)) {
    if (-not [regex]::IsMatch($Package, '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$')) {
        Die "Error: -Package '$Package' is not a valid lowercase dotted Java package."
    }
    $PACKAGE = $Package
}
else {
    if (-not [string]::IsNullOrEmpty($GroupPrefix)) {
        $PREFIX = $GroupPrefix
    }
    elseif (-not [string]::IsNullOrEmpty($Owner)) {
        $PREFIX = 'io.github.' + ($Owner.ToLowerInvariant() -replace '[^a-z0-9]', '')
    }
    else {
        Die "Error: provide -Owner (to derive io.github.<owner>.$JOINED) or -Package."
    }
    if (-not [regex]::IsMatch($PREFIX, '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$')) {
        Die "Error: package prefix '$PREFIX' is invalid."
    }
    $PACKAGE = "$PREFIX.$JOINED"
}
$PACKAGE_PATH = $PACKAGE -replace '\.', '/'
if ([string]::IsNullOrEmpty($Repo)) { $Repo = $KEBAB }
$URL_OWNER = $Owner

# -- Confirm ------------------------------------------------------------------
$ownerDisplay = if ([string]::IsNullOrEmpty($URL_OWNER)) { '<unchanged: no -Owner>' } else { $URL_OWNER }
@"

This will rewrite the template in place:
  display name     $DISPLAY
  fabric mod id    $KEBAB
  neoforge mod id  $SNAKE
  base package     $PACKAGE
  main class base  $PASCAL
  repo (for URLs)  $ownerDisplay/$Repo

"@ | Write-Output

if (-not $Yes) {
    $ans = Read-Host 'Proceed? [y/N]'
    if ($ans -notmatch '^[Yy]') {
        Write-Output 'Aborted.'
        exit 0
    }
}

# -- Setup for filesystem walk ------------------------------------------------
$root = (Get-Location).ProviderPath
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Test-UnderExcludedDir([string]$fullPath, [string[]]$names) {
    $rel = $fullPath
    if ($fullPath.StartsWith($root)) {
        $rel = $fullPath.Substring($root.Length)
    }
    $segs = $rel -split '[\\/]'
    foreach ($s in $segs) {
        if ($s -ne '' -and ($names -contains $s)) { return $true }
    }
    return $false
}

# -- Text replacements (ordered: most specific first) -------------------------
$textExts = @(
    '.java', '.kt', '.kts', '.gradle', '.json', '.json5', '.toml', '.properties',
    '.yml', '.yaml', '.xml', '.md', '.mcmeta', '.accesswidener', '.cfg', '.txt'
)
$textExcludeDirs = @('.git', 'build', '.gradle', '.idea', '.kotlin', 'versions')

Write-Output 'Rewriting file contents ...'
Get-ChildItem -LiteralPath $root -Recurse -File -Force | Where-Object {
    ($textExts -contains $_.Extension.ToLowerInvariant()) -and
    ($_.Name -ne 'adjust-names.sh') -and ($_.Name -ne 'adjust-names.ps1') -and
    (-not (Test-UnderExcludedDir $_.FullName $textExcludeDirs))
} | ForEach-Object {
    $content = [System.IO.File]::ReadAllText($_.FullName)
    $new = $content
    $new = $new.Replace($PH_PACKAGE, $PACKAGE)
    $new = $new.Replace($PH_PACKAGE_PATH, $PACKAGE_PATH)
    $new = $new.Replace($PH_CLASS, $PASCAL)
    $new = $new.Replace($PH_DISPLAY, $DISPLAY)
    $new = $new.Replace($PH_SNAKE, $SNAKE)
    $new = $new.Replace($PH_KEBAB, $KEBAB)
    if (-not [string]::IsNullOrEmpty($URL_OWNER)) {
        $new = $new.Replace($PH_OWNER, $URL_OWNER)
    }
    if ($new -ne $content) {
        [System.IO.File]::WriteAllText($_.FullName, $new, $utf8NoBom)
    }
}

# -- Rename package directories (com/example/examplemod -> new path) ----------
Write-Output 'Restructuring packages ...'
$pkgExcludeDirs = @('build', 'versions', '.gradle')
$phSuffix = '/' + $PH_PACKAGE_PATH
$comTopSegment = ($PH_PACKAGE_PATH -split '/')[0]   # com

Get-ChildItem -LiteralPath $root -Recurse -Directory -Force | Where-Object {
    (($_.FullName -replace '\\', '/').EndsWith($phSuffix)) -and
    (-not (Test-UnderExcludedDir $_.FullName $pkgExcludeDirs))
} | Sort-Object { $_.FullName } -Descending | ForEach-Object {
    $oldDir = $_.FullName
    $oldNorm = $oldDir -replace '\\', '/'
    $parent = $oldNorm.Substring(0, $oldNorm.Length - $phSuffix.Length)
    $newDir = "$parent/$PACKAGE_PATH"
    New-Item -ItemType Directory -Force -Path $newDir | Out-Null
    $srcGlob = Join-Path $oldDir '*'
    if (Test-Path -LiteralPath $oldDir) {
        Copy-Item -Path $srcGlob -Destination $newDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    $comTop = "$parent/$comTopSegment"
    if (Test-Path -LiteralPath $comTop) {
        Remove-Item -LiteralPath $comTop -Recurse -Force
    }
}

# -- Rename files: mixin json, assets dir, ExampleMod* classes ----------------
Write-Output 'Renaming files ...'
$renameExcludeDirs = @('build', 'versions', '.git', '.gradle')

Get-ChildItem -LiteralPath $root -Recurse -Force | Where-Object {
    ($_.Name.StartsWith($PH_KEBAB, [System.StringComparison]::Ordinal) -or
     $_.Name.StartsWith($PH_CLASS, [System.StringComparison]::Ordinal)) -and
    (-not (Test-UnderExcludedDir $_.FullName $renameExcludeDirs))
} | Sort-Object { $_.FullName } -Descending | ForEach-Object {
    $base = $_.Name
    $newBase = $base
    if ($base.StartsWith($PH_KEBAB, [System.StringComparison]::Ordinal)) {
        $newBase = $KEBAB + $base.Substring($PH_KEBAB.Length)
    }
    elseif ($base.StartsWith($PH_CLASS, [System.StringComparison]::Ordinal)) {
        $newBase = $PASCAL + $base.Substring($PH_CLASS.Length)
    }
    if ($base -ne $newBase) {
        Rename-Item -LiteralPath $_.FullName -NewName $newBase
    }
}

# -- Remove template tooling (unless asked to keep) ---------------------------
if (-not $KeepTooling) {
    Write-Output 'Removing template tooling ...'
    foreach ($p in @('./adjust-names.sh', './adjust-names.ps1', '.github/workflows/bootstrap.yml')) {
        if (Test-Path -LiteralPath $p) {
            Remove-Item -LiteralPath $p -Force
        }
    }
}

@"

Done. '$DISPLAY' is ready.

Next steps:
  1. Review the changes (git diff / git status).
  2. Build:  ./gradlew build
  3. If you do NOT want the Paper plugin, run:  ./remove-paper.sh
  4. Configure publishing later by setting repo secrets/vars
     (MODRINTH_TOKEN + MODRINTH_PROJECT_ID, and/or CURSEFORGE_*).
"@ | Write-Output
