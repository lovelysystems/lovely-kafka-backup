plugins {
    kotlin("jvm")
    application
    `java-library`
    `java-test-fixtures`
    id("io.gitlab.arturbosch.detekt")
    id("org.graalvm.buildtools.native")
    kotlin("kapt")
}

application {
    mainClass.set("ls.kafka.backup.cli.ApplicationKt")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    implementation(project(":format"))
    implementation(libs.kafka.clients)
    implementation(libs.kafka.storage)
    implementation(libs.picocli)
    implementation(libs.picocli.codegen)
    kapt(libs.picocli.codegen)
    implementation(libs.s3.kotlin.client)
    implementation(libs.apache.commons.compress)

    runtimeOnly(libs.logger.nothing)

    testImplementation(project(":testing"))
    testImplementation(project(":confluent-connect"))
    testImplementation(testLibs.kotest.runner.junit5)
    testImplementation(testLibs.kotest.assertions)
    testImplementation(testLibs.kotest.extensions.testcontainers)
    testImplementation(testLibs.testcontainers)
    testImplementation(testLibs.mockk)

    testImplementation(libs.confluent.connect.s3)
    testImplementation(libs.kafka.connect.runtime)
}

kapt {
    arguments {
        arg("project", "${project.group}/${project.name}")
    }
}

graalvmNative {
    toolchainDetection = true

    binaries {
        named("main") {
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(11))
                vendor.set(JvmVendorSpec.GRAAL_VM)
                version = "11"
            })
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

configurations.all {
    resolutionStrategy.eachDependency {
        // ensure we use the same version of slf4j otherwise we get strange runtime errors from the slf4j logger api
        if (requested.group == "org.slf4j") {
            useVersion(libs.versions.slf4j.get())
        }
    }
}
