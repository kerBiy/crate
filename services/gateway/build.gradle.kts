plugins {
    id("crate.spring-service-conventions")
}

dependencies {
    implementation(libs.spring.cloud.starter.gateway.server.webflux)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(libs.spring.boot.starter.webflux.test)
}
