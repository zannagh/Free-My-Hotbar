#!/usr/bin/env bash
# ── adjust-names.sh ───────────────────────────────────────────────────────────
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
#   ./adjust-names.sh --name "My Funny Minecraft Mod" --owner my-github-user
#   ./adjust-names.sh --name "My Mod" --package org.example.mymod
#
# Options:
#   --name  <str>      Mod name, free-form. Required.
#   --owner <str>      Your GitHub user/org. Builds the package prefix
#                      io.github.<owner> and fills in repository URLs.
#   --package <pkg>    Full base package override, e.g. org.example.mymod.
#                      Takes precedence over --owner/--group-prefix.
#   --group-prefix <p> Package prefix to use instead of io.github.<owner>.
#   --repo <name>      Repository name for URLs (default: the fabric/kebab id).
#   --keep-tooling     Do NOT delete adjust-names.* / bootstrap workflow after.
#   -y, --yes          Do not prompt for confirmation.
#   -h, --help         Show this help.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Placeholders shipped by the template (do not change) ──────────────────────
PH_PACKAGE="com.example.examplemod"
PH_PACKAGE_PATH="com/example/examplemod"
PH_CLASS="ExampleMod"
PH_DISPLAY="Example Mod"
PH_KEBAB="example-mod"
PH_SNAKE="example_mod"
PH_OWNER="example-owner"

NAME=""; OWNER=""; PACKAGE=""; GROUP_PREFIX=""; REPO=""; KEEP_TOOLING=false; ASSUME_YES=false

usage() { sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-1}"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --name) NAME="${2:-}"; shift 2;;
    --owner) OWNER="${2:-}"; shift 2;;
    --package) PACKAGE="${2:-}"; shift 2;;
    --group-prefix) GROUP_PREFIX="${2:-}"; shift 2;;
    --repo) REPO="${2:-}"; shift 2;;
    --keep-tooling) KEEP_TOOLING=true; shift;;
    -y|--yes) ASSUME_YES=true; shift;;
    -h|--help) usage 0;;
    *) echo "Unknown argument: $1" >&2; usage 1;;
  esac
done

[ -n "$NAME" ] || { echo "Error: --name is required." >&2; usage 1; }

# ── Helpers (bash 3.2 / macOS compatible) ─────────────────────────────────────
JAVA_KEYWORDS=" abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null var record sealed permits yield "

lc() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }
uc() { printf '%s' "$1" | tr '[:lower:]' '[:upper:]'; }
capitalize() {
  local w="$1"; [ -z "$w" ] && { printf ''; return; }
  printf '%s%s' \
    "$(printf '%s' "$w" | cut -c1 | tr '[:lower:]' '[:upper:]')" \
    "$(printf '%s' "$w" | cut -c2- | tr '[:upper:]' '[:lower:]')"
}

if [ "$(uname)" = "Darwin" ]; then sedi() { sed -i '' "$@"; }; else sedi() { sed -i "$@"; }; fi

# ── Normalize the mod name into every form ────────────────────────────────────
if printf '%s' "$NAME" | LC_ALL=C grep -q '[^ -~]'; then
  echo "Warning: non-ASCII characters in --name will be dropped." >&2
fi
SPACED=$(printf '%s' "$NAME" | sed -E 's/([a-z0-9])([A-Z])/\1 \2/g' | LC_ALL=C tr -c '[:alnum:]' ' ')
WORDS=(); set -- $SPACED
for w in "$@"; do WORDS+=("$(lc "$w")"); done
[ ${#WORDS[@]} -gt 0 ] || { echo "Error: --name '$NAME' has no usable characters." >&2; exit 1; }
first=$(printf '%s' "${WORDS[0]}" | sed -E 's/^[0-9]+//')
if [ -z "$first" ]; then
  WORDS=("${WORDS[@]:1}")
  [ ${#WORDS[@]} -gt 0 ] || { echo "Error: --name '$NAME' has no letter-leading word (Java identifiers cannot start with a digit)." >&2; exit 1; }
else
  WORDS[0]="$first"
fi

JOINED=""; KEBAB=""; SNAKE=""; PASCAL=""; DISPLAY=""; i=0
for w in "${WORDS[@]}"; do
  JOINED="${JOINED}${w}"; PASCAL="${PASCAL}$(capitalize "$w")"
  if [ $i -eq 0 ]; then KEBAB="$w"; SNAKE="$w"; DISPLAY="$(capitalize "$w")";
  else KEBAB="${KEBAB}-${w}"; SNAKE="${SNAKE}_${w}"; DISPLAY="${DISPLAY} $(capitalize "$w")"; fi
  i=$((i+1))
done

# ── Validate derived identifiers ──────────────────────────────────────────────
case "$JOINED" in [a-z]*) : ;; *) echo "Error: package segment '$JOINED' must start with a lowercase letter." >&2; exit 1;; esac
case "$JAVA_KEYWORDS" in *" $JOINED "*) echo "Error: package segment '$JOINED' is a Java keyword." >&2; exit 1;; esac
case "$JAVA_KEYWORDS" in *" $(lc "$PASCAL") "*) echo "Error: class name '$PASCAL' collides with a Java keyword." >&2; exit 1;; esac
printf '%s' "$SNAKE" | LC_ALL=C grep -Eq '^[a-z][a-z0-9_]{1,63}$' || { echo "Error: neoforge mod id '$SNAKE' must match ^[a-z][a-z0-9_]{1,63}\$ (2-64 chars, letter first)." >&2; exit 1; }

# ── Resolve package / owner / repo ────────────────────────────────────────────
if [ -n "$PACKAGE" ]; then
  printf '%s' "$PACKAGE" | LC_ALL=C grep -Eq '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$' || { echo "Error: --package '$PACKAGE' is not a valid lowercase dotted Java package." >&2; exit 1; }
else
  if [ -n "$GROUP_PREFIX" ]; then PREFIX="$GROUP_PREFIX"
  elif [ -n "$OWNER" ]; then PREFIX="io.github.$(lc "$OWNER" | LC_ALL=C tr -cd '[:alnum:]')"
  else echo "Error: provide --owner (to derive io.github.<owner>.$JOINED) or --package." >&2; exit 1; fi
  printf '%s' "$PREFIX" | LC_ALL=C grep -Eq '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$' || { echo "Error: package prefix '$PREFIX' is invalid." >&2; exit 1; }
  PACKAGE="$PREFIX.$JOINED"
fi
PACKAGE_PATH=$(printf '%s' "$PACKAGE" | tr '.' '/')
[ -n "$REPO" ] || REPO="$KEBAB"
URL_OWNER="$OWNER"

# ── Confirm ───────────────────────────────────────────────────────────────────
cat <<EOF

This will rewrite the template in place:
  display name     $DISPLAY
  fabric mod id    $KEBAB
  neoforge mod id  $SNAKE
  base package     $PACKAGE
  main class base  $PASCAL
  repo (for URLs)  ${URL_OWNER:-<unchanged: no --owner>}/$REPO

EOF
if [ "$ASSUME_YES" != true ]; then
  printf 'Proceed? [y/N] '; read -r ans
  case "$ans" in [Yy]*) : ;; *) echo "Aborted."; exit 0;; esac
fi

# ── Text replacements (ordered: most specific first) ──────────────────────────
SELF="$(basename "$0")"
echo "Rewriting file contents ..."
find . \
  -type d \( -name .git -o -name build -o -name .gradle -o -name .idea -o -name .kotlin -o -name versions \) -prune -o \
  -type f \( -name '*.java' -o -name '*.kt' -o -name '*.kts' -o -name '*.gradle' \
    -o -name '*.json' -o -name '*.json5' -o -name '*.toml' -o -name '*.properties' \
    -o -name '*.yml' -o -name '*.yaml' -o -name '*.xml' -o -name '*.md' -o -name '*.mcmeta' \
    -o -name '*.accesswidener' -o -name '*.cfg' -o -name '*.txt' \) -print \
| while IFS= read -r f; do
    case "$f" in */"$SELF"|*/adjust-names.ps1) continue;; esac
    sedi \
      -e "s|${PH_PACKAGE}|${PACKAGE}|g" \
      -e "s|${PH_PACKAGE_PATH}|${PACKAGE_PATH}|g" \
      -e "s|${PH_CLASS}|${PASCAL}|g" \
      -e "s|${PH_DISPLAY}|${DISPLAY}|g" \
      -e "s|${PH_SNAKE}|${SNAKE}|g" \
      -e "s|${PH_KEBAB}|${KEBAB}|g" \
      "$f"
    if [ -n "$URL_OWNER" ]; then sedi -e "s|${PH_OWNER}|${URL_OWNER}|g" "$f"; fi
  done

# ── Rename package directories (com/example/examplemod -> new path) ───────────
echo "Restructuring packages ..."
find . -type d -path "*/${PH_PACKAGE_PATH}" -not -path '*/build/*' -not -path '*/versions/*' -not -path '*/.gradle/*' \
| sort -r | while IFS= read -r old_dir; do
    parent="${old_dir%/${PH_PACKAGE_PATH}}"
    new_dir="${parent}/${PACKAGE_PATH}"
    mkdir -p "$new_dir"
    cp -a "$old_dir"/. "$new_dir"/
    rm -rf "${parent}/${PH_PACKAGE_PATH%%/*}"   # remove leftover top segment (com)
  done

# ── Rename files: mixin json, assets dir, ExampleMod* classes ─────────────────
echo "Renaming files ..."
find . -not -path '*/build/*' -not -path '*/versions/*' -not -path '*/.git/*' -not -path '*/.gradle/*' \
  \( -name "${PH_KEBAB}*" -o -name "${PH_CLASS}*" \) | sort -r | while IFS= read -r old; do
    dir="$(dirname "$old")"; base="$(basename "$old")"
    new_base="$base"
    case "$base" in
      ${PH_KEBAB}*) new_base="${KEBAB}${base#${PH_KEBAB}}";;
      ${PH_CLASS}*) new_base="${PASCAL}${base#${PH_CLASS}}";;
    esac
    [ "$base" != "$new_base" ] && mv "$dir/$base" "$dir/$new_base"
  done

# ── Remove template tooling (unless asked to keep) ────────────────────────────
if [ "$KEEP_TOOLING" != true ]; then
  echo "Removing template tooling ..."
  rm -f "./adjust-names.sh" "./adjust-names.ps1" ".github/workflows/bootstrap.yml"
fi

cat <<EOF

Done. '$DISPLAY' is ready.

Next steps:
  1. Review the changes (git diff / git status).
  2. Build:  ./gradlew build
  3. If you do NOT want the Paper plugin, run:  ./remove-paper.sh
  4. Configure publishing later by setting repo secrets/vars
     (MODRINTH_TOKEN + MODRINTH_PROJECT_ID, and/or CURSEFORGE_*).
EOF
