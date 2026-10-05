plugins {
    id("crate.spring-service-conventions")
}

dependencies {
    implementation(libs.spring.cloud.starter.gateway.server.webflux)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)

    testImplementation(libs.spring.boot.starter.webflux.test)
    testImplementation(libs.wiremock.standalone)
}
