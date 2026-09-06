#!/usr/bin/env bash
#
# remove-paper.sh - cleanly remove the optional PaperMC (Bukkit) plugin subproject from a
# project generated from this template. One-shot: it deletes itself (and remove-paper.ps1) when done.
#
# Bash 3.2 compatible (the default /bin/bash on macOS). Safe to re-run: every step is idempotent,
# so a partial run followed by a second run finishes the job.
set -euo pipefail

# Resolve the repo root as the directory containing this script, so the script works no matter
# where it is invoked from.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Portable in-place sed: BSD sed (Darwin) needs an explicit empty backup suffix, GNU sed must not
# get one.
if [ "$(uname)" = "Darwin" ]; then
    sedi() { sed -i '' "$@"; }
else
    sedi() { sed -i "$@"; }
fi

echo "Removing the Paper plugin subproject from $SCRIPT_DIR ..."

# 1. Delete the paper/ subproject directory.
if [ -d "paper" ]; then
    rm -rf "paper"
    echo "  - deleted paper/"
else
    echo "  - paper/ already gone, skipping"
fi

# 2. Remove the include(":paper") line and the comment block that introduces it from
#    settings.gradle.kts.
SETTINGS="settings.gradle.kts"
if [ -f "$SETTINGS" ]; then
    if grep -q '^include(":paper")' "$SETTINGS"; then
        # Drop the include line itself. Anchored at column 0 so a commented-out example line
        # (e.g. `//   include(":paper")` in the SUBPROJECTS marker documentation) is left alone -
        # the defensive scan below will surface it instead of this script editing template docs.
        sedi '/^include(":paper")/d' "$SETTINGS"
        # Drop the paper-specific comment block added alongside the include.
        sedi '/^\/\/ PaperMC\/Bukkit server-side plugin\./d' "$SETTINGS"
        sedi '/^\/\/ only stable Bukkit API, so one jar covers every supported game version\. Remove it with$/d' "$SETTINGS"
        sedi "/^\/\/ \`remove-paper.sh\` \/ \`remove-paper.ps1\` if the template's mod has no server component.$/d" "$SETTINGS"
        echo "  - removed include(\":paper\") from $SETTINGS"
    else
        echo "  - $SETTINGS has no :paper include, skipping"
    fi
else
    echo "  - $SETTINGS not found, skipping"
fi

# 3. Defensive scan: warn (do not fail) about any lingering 'paper' references so the user can
#    check README / CI themselves. Exclude this script, the PowerShell twin, and build output.
echo ""
echo "Scanning for remaining 'paper' references ..."
REMAINING="$(grep -rIln -e 'paper' -e 'Paper' -e 'bukkit' -e 'Bukkit' . \
    --exclude-dir=.git \
    --exclude-dir=build \
    --exclude-dir=.gradle \
    --exclude-dir=.idea \
    --exclude=remove-paper.sh \
    --exclude=remove-paper.ps1 2>/dev/null || true)"
if [ -n "$REMAINING" ]; then
    echo "  WARNING: 'paper' still appears in the files below. Review them (e.g. README, CI workflows)"
    echo "  and remove any references by hand if they are Paper-plugin specific:"
    echo "$REMAINING" | sed 's/^/    /'
else
    echo "  none found."
fi

# 4. Self-delete: this is a one-shot script. Remove both remove-paper scripts.
echo ""
echo "Paper plugin removed. Deleting the one-shot removal scripts (remove-paper.sh, remove-paper.ps1) ..."
rm -f "remove-paper.ps1"
rm -f "remove-paper.sh"

echo "Done."
