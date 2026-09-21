// Repositories for the eunomia artifacts. Applied from multiloader-common, so it reaches
// :common and both loader branches (they apply multiloader-loader, which applies
// multiloader-common). Group-scoped throughout, so none of these ever intercepts a
// Minecraft / Fabric / Forge artifact.

repositories {
    // The eunomia MOD jar dropped into the dev run/mods dir (see EunomiaRuntimeMod.kt).
    // Modrinth's maven facade resolves `maven.modrinth:<slug>:<version id>` keylessly.
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
    // Credential-free local fallback for the compile-time libraries: publish with
    // `./gradlew :core:publishToMavenLocal` in the eunomia repo and a tokenless build
    // still resolves.
    mavenLocal {
        content { includeGroup("de.zannagh.eunomia") }
    }
    // The TOKEN is the only thing GitHub actually validates on a Packages read - the Basic-auth username
    // field is ignored, so it is not worth gating on. Requiring a username here used to make a
    // token-only environment (CI, or a shell with GITHUB_TOKEN but no GITHUB_ACTOR) skip this repo
    // *silently*, and the resulting "Could not find de.zannagh.eunomia:eunomia-core:<v>" listed every
    // searched location EXCEPT this one - which reads as a missing artifact rather than missing config.
    // So: gate on the token alone and send a placeholder username when none was supplied.
    val eunomiaGprToken = providers.gradleProperty("gpr.token")
        .orElse(providers.environmentVariable("GITHUB_TOKEN"))
    val eunomiaGprUser = providers.gradleProperty("gpr.user")
        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
        .orElse("x-access-token")
    if (eunomiaGprToken.isPresent) {
        maven {
            name = "EunomiaGitHubPackages"
            url = uri("https://maven.pkg.github.com/zannagh/eunomia")
            credentials {
                username = eunomiaGprUser.get()
                password = eunomiaGprToken.get()
            }
            content { includeGroup("de.zannagh.eunomia") }
        }
    }
}
