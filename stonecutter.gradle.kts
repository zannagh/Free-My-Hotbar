plugins {
    id("dev.kikugie.stonecutter")
    id("net.neoforged.moddev") version "2.0.140" apply false
}

stonecutter active "fabric-1.21.11" /* [SC] DO NOT EDIT */

// Runnable entry point for the plain-JVM smoke suite. CI (and contributors) call
// `./gradlew smokeTest`; it delegates to the :smoke subproject's test task.
tasks.register("smokeTest") {
    group = "verification"
    description = "Runs the plain-JVM smoke test suite (:smoke:test)."
    dependsOn(":smoke:test")
}

// Publishing / artifact-staging tasks are added by a later template job.
