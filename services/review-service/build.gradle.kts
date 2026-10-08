plugins {
    id("crate.spring-service-conventions")
}

dependencies {
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    // JPA for the Review entity: @Version gives optimistic locking.
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    // Publishes review.events (SPEC 5.4, 6).
    implementation(libs.spring.boot.starter.kafka)
    implementation(project(":libs:event-contracts"))
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.kafka)
    // Stands in for catalog-service in tests.
    testImplementation(libs.wiremock.standalone)
}
