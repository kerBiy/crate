plugins {
    id("crate.spring-service-conventions")
}

dependencies {
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(libs.spring.boot.starter.webmvc.test)
}
