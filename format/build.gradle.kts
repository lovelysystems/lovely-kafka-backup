plugins {
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
}

dependencies {
    // Test
    testImplementation(testLibs.kotest.runner.junit5)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
