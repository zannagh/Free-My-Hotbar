# Free My Hotbar

[![Latest](https://img.shields.io/github/v/release/zannagh/Free-My-Hotbar?logo=github&label=Latest%20Release&color=green)](https://github.com/zannagh/Free-My-Hotbar/releases)
[![LatestPre](https://img.shields.io/github/v/release/zannagh/Free-My-Hotbar?include_prereleases&label=Latest%20(Pre)Release&logo=github)](https://github.com/zannagh/Free-My-Hotbar/releases)
[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/AMwbYqdmQb)

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/free-my-hotbar?logo=modrinth&label=Modrinth)](https://modrinth.com/mod/free-my-hotbar)
[![Curseforge Downloads](https://img.shields.io/curseforge/dt/1695040?logo=curseforge&style=flat&label=CurseForge)](https://www.curseforge.com/minecraft/mc-mods/free-my-hotbar)

![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/zannagh/Free-My-Hotbar/build.yml?branch=main&label=Build)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/zannagh/Free-My-Hotbar/smoke.yml?branch=main&label=Smoke)

<p align="center">
A mod that lets you lock individual hotbar slots so items stop landing in them - keep your sword, pickaxe and food where you put them and let the cobblestone find its own place. Available for Fabric and Forge. Minecraft decides item pickup on the server, so with the mod installed server-side your locked slots are truly skipped; installed on the client alone it falls back to moving items straight back out again.
</p>

> [!IMPORTANT]
> **Free My Hotbar requires [eunomia](https://modrinth.com/mod/eunomia) (0.3.14 or newer).**
> It is a hard dependency on both Fabric and Forge, declared in the loader manifests, so the game
> will refuse to start with Free My Hotbar installed on its own. Install eunomia for your loader
> alongside it. Nothing else is needed.
>
> On the **server** eunomia is only needed if the server runs Free My Hotbar as well; a server
> without either still works, your client just falls back to moving items back out (see
> *Servers without the mod*).

### Features

Free My Hotbar keeps the slots you care about under your control. Pickup is decided by the server, so the mod syncs your locked slots when the server has it installed - and in singleplayer, where your own game *is* the server, it works on its own.

* **Per-slot locking** for all nine hotbar slots - a locked slot is skipped when items are picked up, including partial stacking into a slot that already holds the same item
* **In-game lock screen** with a 3x3 grid of toggles, opened by a keybind (default `H`)
* **Items stay on the ground** when every unlocked slot is full, instead of being crammed into a locked one - in singleplayer and on servers running the mod
* **Works on servers without the mod too**, by picking the item up and immediately moving it back out of the locked slot (see *Servers without the mod* below)
* **Protection against your own clicks**, on by default, so you don't drop something into a locked slot by hand - switchable in the lock screen
* **Multiplayer sync** - your locked slots are sent to servers running the mod, and re-sent on every login
* **Optional on the server side** - a server without the mod simply ignores the channel and won't reject your client
* **Persistent config** stored in `config/free-my-hotbar.json`, with automatic migration of older config versions
* **Fabric and Forge** from one codebase - the only thing to install alongside it is [eunomia](https://modrinth.com/mod/eunomia), which supplies the networking and config framework

#### Servers without the mod

Minecraft decides item pickup entirely on the server, and a client has no say in it. When the server runs Free My Hotbar, locked slots are genuinely skipped and the item stays on the ground. When it doesn't, the mod can only react: the item lands in your locked slot and is moved straight back out into your main inventory.

That fallback is on by default and honest about its limits:

* You may briefly see the item in the locked slot before it moves.
* Servers running anti-cheat can reject inventory changes sent while you're moving, so the mod waits for a moment when you're standing still. There's a **Move items out immediately** option if your server doesn't mind - it's off by default.
* If your main inventory is completely full there's nowhere to move the item, so it stays put. An opt-in **drop it on the ground** mode handles that case instead, at the cost of the item being lootable by anyone nearby.
* The lock screen tells you which mode you're in on the server you're currently connected to.

#### Scope

Locking is about **automatic item pickup**, but it does not stop there.

**Block my own clicks** is **on** by default: placing an item into a locked slot with a plain click, a drag, a number-key swap or the offhand key is refused while it is on. Taking items back *out* always stays possible - locking a slot never holds its contents hostage - and you can turn the setting off in the lock screen if you'd rather your locked slots accept your own placements.

**Shift-clicking is the one case neither setting controls.** When you shift-click a stack in a chest, the *server* decides where it goes and may well pick a locked hotbar slot; your client has no say in that and blocking the click would only stop you from moving your own items. On a server running the mod the locked slot is skipped outright. On a server without it, the client-side fallback treats the stack like any other arrival and moves it back out for you - the same handling automatic pickup gets. Items you placed in a locked slot deliberately, with the setting off, are left exactly where you put them.

*If you run into a conflict with another mod, datapack or plugin, please open an issue on GitHub or drop a message on the Discord server.*

[![OpenBugs](https://img.shields.io/github/issues-search?query=repo%3Azannagh%2FFree-My-Hotbar%20is%3Aopen%20label%3Abug&logo=github&label=Open%20Bugs&color=red
)](https://github.com/zannagh/Free-My-Hotbar/issues)
[![OpenFRs](https://img.shields.io/github/issues-search?query=repo%3Azannagh%2FFree-My-Hotbar%20is%3Aopen%20label%3Aenhancement&logo=github&label=Open%20Feature%20Requests&color=green
)](https://github.com/zannagh/Free-My-Hotbar/issues)
[![ClosedIssues](https://img.shields.io/github/issues-closed/zannagh/Free-My-Hotbar?label=Closed%20Issues&color=green&logo=github)](https://github.com/zannagh/Free-My-Hotbar/issues)

I track issues and requests via [GitHub](https://github.com/zannagh/Free-My-Hotbar/issues) and do my best to close out any bugs timely. If you don't have an account, feel free to join the Discord server and let me know there.

If you like my work and would like to support me, you can do so here:

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/K3K41VR5H1)

---

## Supported versions

| Minecraft | Loaders |
| --- | --- |
| 1.20.1 | Fabric, Forge |

More game versions are planned - the project is set up for multi-version builds, so adding one is a configuration change rather than a fork.

## Community

Join the [Discord server](https://discord.gg/AMwbYqdmQb) for support, discussion, and feature requests.

## Issues and Feature Requests

As mentioned before, feel free to create an issue on the [GitHub repository](https://github.com/zannagh/Free-My-Hotbar/issues) or reach out on [Discord](https://discord.gg/AMwbYqdmQb) to make me aware of problems or ideas that could make this mod better.

## Building

```bash
./gradlew build       # Build every active loader variant
./gradlew smokeTest   # Run the plain-JVM JUnit smoke suite
```

Java 17 is required. Loader jars land under `fabric/versions/**/build/libs/` and `forge/versions/**/build/libs/`.

## Versioning & Releases

All Minecraft versions are built from the `main` branch using [Stonecutter](https://stonecutter.kikugie.dev/) for multi-version support. [GitVersion](https://gitversion.net/) handles semantic versioning automatically. On CI, the version property is passed to the gradle build.

**Release flow:**

* **Releases and prereleases** are created via GitHub Releases; publishing runs on a published release
* All versions are published to [Modrinth](https://modrinth.com/mod/free-my-hotbar) and [CurseForge](https://www.curseforge.com/minecraft/mc-mods/free-my-hotbar) automatically once the release is published
* The publish matrix is derived from the staged artifacts, so a new game version or loader needs no workflow edit

**Version format:**

* Releases: `0.1.0`
* Prereleases: `0.1.1-pre.1`, `0.1.1-pre.2`, etc.

## License

[MIT](LICENSE)
