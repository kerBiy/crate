pluginManagement {
    // Our own convention plugins (crate.java-conventions, crate.spring-service-conventions).
    includeBuild("build-logic")
}

plugins {
    // Lets the Java toolchain (Java 25) be downloaded automatically when it isn't installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "crate"

include(
    ":services:gateway",
    ":services:user-service",
    ":services:catalog-service",
    ":services:review-service",
    ":libs:event-contracts",
)
