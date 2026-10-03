// Applied by every module: Java 25 toolchain, compiler flags, JUnit Platform.
plugins {
    java
}

group = "io.github.kerbiy.crate"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    // Keeps parameter names at runtime (Spring uses them for @PathVariable/@RequestParam binding).
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

