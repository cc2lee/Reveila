plugins {
    id("java-conventions")
}

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.jackson.xml)
    implementation(libs.jackson.jsr310)
    implementation(libs.okhttp)
    implementation(libs.slf4j.api)
    implementation(libs.commons.compress)
    testImplementation(libs.junit.bom)
    // testImplementation(libs.junit.jupiter)
}
