plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow")
    `java-test-fixtures`
}

dependencies {
    compileOnly(libs.kafka.connect.runtime)

    implementation(project(":format"))
    implementation(libs.confluent.connect.s3)

    implementation(libs.kotlin.logging.jvm)

    // Test
    testImplementation(testLibs.kotest.runner.junit5)
    testImplementation(testLibs.kotest.extensions.testcontainers)
    testImplementation(libs.kafka.connect.runtime)
    testFixturesImplementation(testLibs.testcontainers)
    testFixturesImplementation(testLibs.testcontainers.kafka)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
