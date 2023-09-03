plugins {
    kotlin("jvm")
}

dependencies {
    // Test
    testImplementation(testLibs.kotest.runner.junit5)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
