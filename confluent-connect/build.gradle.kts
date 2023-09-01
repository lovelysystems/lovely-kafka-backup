plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow")
}

dependencies {
    compileOnly(libs.kafka.connect.runtime)

    implementation(project(":format"))
    implementation(libs.confluent.connect.s3)

    implementation(libs.kotlin.logging.jvm)
}
