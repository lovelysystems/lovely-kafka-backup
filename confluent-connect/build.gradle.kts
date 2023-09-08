plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow")
    id("io.gitlab.arturbosch.detekt")
}

dependencies {
    compileOnly(libs.kafka.connect.runtime)

    implementation(project(":format"))
    implementation(libs.confluent.connect.s3)

    implementation(libs.kotlin.logging.jvm)

    // Test
    testImplementation(project(":testing"))
    testImplementation(testLibs.kotest.runner.junit5)
    testImplementation(testLibs.kotest.extensions.testcontainers)
    testImplementation(libs.kafka.connect.runtime)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
