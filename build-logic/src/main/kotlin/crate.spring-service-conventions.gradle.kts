// Applied by every Spring Boot service: Boot plugin + Spring Boot and Spring Cloud BOMs.
plugins {
    id("crate.java-conventions")
    id("org.springframework.boot")
}

// Precompiled script plugins can't use the type-safe `libs` accessor, so look it up by name.
val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    // A platform (BOM) supplies versions for dependencies declared without one.
    val springBoot = platform(libs.findLibrary("spring-boot-dependencies").get())
    val springCloud = platform(libs.findLibrary("spring-cloud-dependencies").get())
    implementation(springBoot)
    implementation(springCloud)
    testImplementation(springBoot)
    testImplementation(springCloud)
    testRuntimeOnly(springBoot)

    // Gradle 9 no longer adds the JUnit launcher implicitly; its version comes from the Boot BOM.
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // Dev-only JVM flags (SPEC section 9): less memory, faster startup.
    jvmArgs("-Xmx256m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1")
}
