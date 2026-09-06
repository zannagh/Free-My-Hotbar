#!/usr/bin/env pwsh
#
# remove-paper.ps1 - cleanly remove the optional PaperMC (Bukkit) plugin subproject from a
# project generated from this template. One-shot: it deletes itself (and remove-paper.sh) when done.
#
# PowerShell equivalent of remove-paper.sh. Safe to re-run: every step is idempotent.

$ErrorActionPreference = 'Stop'

# Resolve the repo root as the directory containing this script.
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "Removing the Paper plugin subproject from $ScriptDir ..."

# 1. Delete the paper/ subproject directory.
if (Test-Path -LiteralPath 'paper') {
    Remove-Item -LiteralPath 'paper' -Recurse -Force
    Write-Host "  - deleted paper/"
} else {
    Write-Host "  - paper/ already gone, skipping"
}

# 2. Remove the include(":paper") line and its introducing comment block from settings.gradle.kts.
$Settings = 'settings.gradle.kts'
if (Test-Path -LiteralPath $Settings) {
    $lines = Get-Content -LiteralPath $Settings
    # Anchored at column 0 so a commented-out example line (e.g. `//   include(":paper")` in the
    # SUBPROJECTS marker documentation) is left alone - the defensive scan surfaces it instead.
    if ($lines -match '^include\(":paper"\)') {
        $dropPrefixes = @(
            '// PaperMC/Bukkit server-side plugin.',
            '// only stable Bukkit API, so one jar covers every supported game version. Remove it with',
            "// ``remove-paper.sh`` / ``remove-paper.ps1`` if the template's mod has no server component."
        )
        $kept = $lines | Where-Object {
            $line = $_
            if ($line -match '^include\(":paper"\)') { return $false }
            foreach ($p in $dropPrefixes) {
                if ($line.Trim() -eq $p) { return $false }
            }
            return $true
        }
        Set-Content -LiteralPath $Settings -Value $kept
        Write-Host "  - removed include("":paper"") from $Settings"
    } else {
        Write-Host "  - $Settings has no :paper include, skipping"
    }
} else {
    Write-Host "  - $Settings not found, skipping"
}

# 3. Defensive scan: warn (do not fail) about any lingering 'paper' references.
Write-Host ""
Write-Host "Scanning for remaining 'paper' references ..."
$excludeDirs = @('.git', 'build', '.gradle', '.idea')
$excludeFiles = @('remove-paper.sh', 'remove-paper.ps1')
$remaining = New-Object System.Collections.Generic.List[string]
Get-ChildItem -Recurse -File | ForEach-Object {
    $file = $_
    $relative = $file.FullName.Substring($ScriptDir.Length).TrimStart('\', '/')
    $parts = $relative -split '[\\/]'
    foreach ($d in $excludeDirs) {
        if ($parts -contains $d) { return }
    }
    if ($excludeFiles -contains $file.Name) { return }
    if (Select-String -LiteralPath $file.FullName -Pattern 'paper', 'Paper', 'bukkit', 'Bukkit' -Quiet) {
        $remaining.Add($relative)
    }
}
if ($remaining.Count -gt 0) {
    Write-Host "  WARNING: 'paper' still appears in the files below. Review them (e.g. README, CI workflows)"
    Write-Host "  and remove any references by hand if they are Paper-plugin specific:"
    foreach ($r in $remaining) { Write-Host "    $r" }
} else {
    Write-Host "  none found."
}

# 4. Self-delete: this is a one-shot script. Remove both remove-paper scripts.
Write-Host ""
Write-Host "Paper plugin removed. Deleting the one-shot removal scripts (remove-paper.sh, remove-paper.ps1) ..."
Remove-Item -LiteralPath 'remove-paper.sh' -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath 'remove-paper.ps1' -Force -ErrorAction SilentlyContinue

Write-Host "Done."
