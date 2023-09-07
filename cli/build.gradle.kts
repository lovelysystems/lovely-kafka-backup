plugins {
    kotlin("jvm")
    application
    `java-test-fixtures`
    id("io.gitlab.arturbosch.detekt")
}

application {
    mainClass.set("ls.backup.cli.ApplicationKt")
}

dependencies {
    implementation(project(":format"))
    implementation(libs.kafka.clients)
    implementation(libs.picocli)
    implementation(libs.s3.kotlin.client)
    implementation(libs.apache.commons.compress)

    runtimeOnly(libs.logger.nothing)

    testImplementation(testLibs.kotest.runner.junit5)
    testImplementation(testLibs.kotest.assertions)
    testImplementation(testLibs.kotest.extensions.testcontainers)
    testImplementation(testLibs.testcontainers)
    testImplementation(testFixtures(project(":confluent-connect")))
    testImplementation(testLibs.mockk)

    //so we use the actual sink logic to create data on minio for tests
    testImplementation(project(":confluent-connect"))
    testImplementation(libs.confluent.connect.s3)
    testImplementation(libs.kafka.connect.runtime)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
