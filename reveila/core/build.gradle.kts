plugins {
    id("reveila.shared-lib")
}

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.jackson.xml)
    implementation(libs.jackson.jsr310)
    implementation(libs.jackson.jdk8)
    implementation(libs.okhttp)
    implementation(libs.slf4j.api)
    implementation(libs.commons.compress)
    
    // AI / Security Runtime dependencies
    implementation(libs.json.schema.validator)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
