# Minecraft Mod Template

A GitHub **template repository** for building a multi-loader Minecraft mod. One codebase compiles for **Fabric** and **Forge** via [Stonecutter](https://stonecutter.kikugie.dev/), with an optional [PaperMC](https://papermc.io/) server plugin, a plain-JVM smoke-test suite, and CI wired for building, code scanning, and (dormant) publishing to Modrinth and CurseForge.

Create your own repository from this one, and a bootstrap step rewrites the placeholder identity into your mod's name. Nothing here carries a personal namespace — the template ships with neutral placeholders (`free-my-hotbar` / `io.github.zannagh.freemyhotbar` / `FreeMyHotbar`) that the rename step replaces.

## What you get

- **Multi-loader from one source**: Fabric + Forge (classic MinecraftForge, not NeoForge — NeoForge only exists for MC 1.20.2+), built with Stonecutter `0.9.1`.
- **Shared `common` module**: loader-agnostic code inherited by each loader.
- **Optional Paper plugin**: a Bukkit server-side plugin subproject you can keep or remove in one command.
- **Smoke tests**: fast plain-JVM JUnit invariants that catch a broken project.
- **CI out of the box**: build validation, smoke tests, CodeQL scanning, and release publishing.
- **Convention plugins in `buildSrc/`**: build logic lives in one place, no copy-paste across loaders.
- **Toolchain**: Java 17, Gradle wrapper `9.7.0`, one Minecraft version shipped (`1.20.1`).

## Getting started

Pick one of two paths.

### a. "Use this template" (recommended)

1. Click **Use this template** on GitHub and create a new repository.
2. On the first push to the default branch, the included **bootstrap** workflow runs. It:
   - derives your mod's names from the **new repository name** and **owner**,
   - sets the base package to `io.github.<owner>.<modname>`,
   - runs the rename in place and **commits the rewritten project**,
   - removes the rename tooling and the bootstrap workflow itself.

> The bootstrap commit is authored by `github-actions[bot]`, and a bot commit does **not** re-trigger CI. Your next push (or opening a PR) is what kicks off the build. The bootstrap only runs while the project is still un-initialized — once the rename tooling is gone, it does nothing.

The repository name is the only "template variable" GitHub gives you, so the derived display name matches the repo name. If you want a display name the repo name can't express, or a custom package/group, use the manual path instead.

### b. Manual / local rename

Clone your new repository (or this one), then run the rename script once and delete it (it offers to remove itself).

```bash
# Bash (macOS / Linux)
./adjust-names.sh --name "My Cool Mod" --owner my-github-user
```

```powershell
# PowerShell (Windows)
.\adjust-names.ps1 -Name "My Cool Mod" -Owner my-github-user
```

#### Flags

| Flag | Description |
| --- | --- |
| `--name <str>` | Mod name, free-form. **Required.** |
| `--owner <str>` | Your GitHub user/org. Builds the package prefix `io.github.<owner>` and fills in repository URLs. |
| `--package <pkg>` | Full base package override, e.g. `org.example.mymod`. Takes precedence over `--owner` / `--group-prefix`. |
| `--group-prefix <p>` | Package prefix to use instead of `io.github.<owner>`. |
| `--repo <name>` | Repository name for URLs (default: the derived kebab-case id). |
| `--keep-tooling` | Do **not** delete `adjust-names.*` / the bootstrap workflow when finished. |
| `-y`, `--yes` | Do not prompt for confirmation. |
| `-h`, `--help` | Show help. |

You must provide either `--owner` (to derive `io.github.<owner>.<modname>`) or an explicit `--package`.

#### How a free-form name maps to identifiers

The script accepts one free-form `--name` and derives every form the project needs:

| Form | Example | Used for |
| --- | --- | --- |
| Display | `My Funny Minecraft Mod` | Human-readable name |
| Fabric id (kebab) | `my-funny-minecraft-mod` | Fabric mod id, archive base name |
| Forge id (snake) | `my_funny_minecraft_mod` | Forge mod id |
| Package segment | `myfunnyminecraftmod` | Last segment of the Java package |
| Class base (Pascal) | `MyFunnyMinecraftMod` | Main class names |

Both a kebab-case and a snake-case id exist because the loaders differ by convention: **Fabric** ids are kebab-case, **Forge** ids are snake_case — and Forge **forbids hyphens** in mod ids. The script validates the snake-case id against Forge's `^[a-z][a-z0-9_]{1,63}$` rule and rejects names that can't form a legal Java identifier.

## Project structure

```
your-mod/
├── buildSrc/                 Convention plugins (multiloader-common, multiloader-loader, loom-*)
├── common/                   Loader-agnostic shared code (main + client source sets)
├── fabric/                   Fabric loader entry points and resources
├── forge/                    Forge loader entry points and resources
├── paper/                    Optional PaperMC/Bukkit server plugin (plain Gradle subproject)
├── smoke/                    Plain-JVM JUnit smoke tests (not a loader variant)
├── settings.gradle.kts       Stonecutter setup + subproject includes (:smoke, :paper)
├── stonecutter.gradle.kts    Active version + the smokeTest task
├── stonecutter.properties.toml   Mod identity + per-version / per-loader properties
├── versions.json5            Stonecutter version/branch matrix
├── gradle.properties         Shared Gradle/build settings
├── adjust-names.sh / .ps1    One-shot rename tooling (removed after bootstrap)
└── remove-paper.sh / .ps1    One-shot Paper-removal tooling
```

Mod identity (`maven_group`, `archives_base_name`), the Fabric `loader_version`, and each version's Java/Minecraft properties live in `stonecutter.properties.toml`, where Stonecutter injects them per variant.

## Removing the Paper plugin

If your mod has no server component, drop the Paper subproject in one command:

```bash
./remove-paper.sh          # Bash (macOS / Linux)
.\remove-paper.ps1         # PowerShell (Windows)
```

It deletes the `paper/` directory, removes the `include(":paper")` wiring from `settings.gradle.kts`, scans for any leftover `paper` references (warning only), and then deletes both removal scripts. It is one-shot, idempotent, and safe to re-run.

## Building & running

```bash
./gradlew build          # Compile + test every active loader variant; produces the loader jars
./gradlew smokeTest      # Run the plain-JVM JUnit smoke suite (:smoke:test)
./gradlew :paper:build   # Build the Paper plugin jar (if you kept it)
```

Loader jars land under `fabric/versions/**/build/libs/` and `forge/versions/**/build/libs/` (sources jars excluded).

### Switching / adding Minecraft versions

The active variant is set in `stonecutter.gradle.kts`:

```kotlin
stonecutter active "fabric-1.20.1" /* [SC] DO NOT EDIT */
```

Stonecutter also generates tasks to switch it, e.g.:

```bash
./gradlew "Set active project to forge-1.20.1"
```

Then re-sync Gradle (e.g. in IntelliJ). To **add** a Minecraft version:

1. Add the new version/branch entries to `versions.json5`.
2. Add the matching `["<version>"]`, `["fabric-<version>"]`, and `["forge-<version>"]` blocks to `stonecutter.properties.toml`.
3. Re-sync Gradle.

## Publishing (dormant by default)

The publish workflows do **nothing** until you configure them. Out of the box, a release build just builds the jars and attaches them to the GitHub Release using the built-in token — nothing is sent off-site.

Enable a platform by adding its secret **and** variable in the repository's **Settings → Secrets and variables → Actions**. Each platform is independent — set up only the ones you use:

| Platform | Required to enable |
| --- | --- |
| Modrinth | secret `MODRINTH_TOKEN` + variable `MODRINTH_PROJECT_ID` |
| CurseForge | secret `CURSEFORGE_TOKEN` + variable `CURSEFORGE_PROJECT_ID` |

Optional variables (with defaults tuned for the shipped `1.20.1` template):

| Variable | Default | Purpose |
| --- | --- | --- |
| `GAME_VERSIONS` | `1.20.1` | Minecraft versions to tag on the published files |
| `DISPLAY_VERSION` | `mc-1.20.1` | The `+<display>` segment in the jar names |
| `ARCHIVES_BASE` | `free-my-hotbar` | Stonecutter `archives_base_name` |

- **`publish.yml`** runs on a **published GitHub Release**, or via manual dispatch (which defaults to a `dry_run` that resolves metadata without uploading). It builds the jars, attaches them to the release, and publishes to any configured platform. With nothing configured it prints exactly which secrets/vars to set and exits cleanly.
- **`publish-existing-release.yml`** is a dispatch-only, admin-restricted job that re-publishes jars already attached to an existing release (chosen by tag) to Modrinth and/or CurseForge **without rebuilding**.

## Continuous integration

The `.github/workflows/` directory ships with:

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `build.yml` | push to `main`, PRs | Compiles every loader variant and uploads the jars |
| `smoke.yml` | push to `main`, PRs | Runs the `smokeTest` suite separately from the build gate |
| `codeql.yml` | push/PR to `main`, weekly | CodeQL security-and-quality scanning (`java-kotlin`) |
| `publish.yml` | published release, dispatch | Builds, attaches to the release, publishes if configured |
| `publish-existing-release.yml` | dispatch | Re-publishes an existing release's jars without rebuilding |
| `bootstrap.yml` | first push after "Use this template" | One-shot rename; self-removes |
