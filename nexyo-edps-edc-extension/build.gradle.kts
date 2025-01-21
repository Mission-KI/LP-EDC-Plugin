plugins {
    `java-library`
    id("application")
}

dependencies {
    implementation(libs.edc.control.plane.core)
    implementation(libs.edc.api.observability)
}
