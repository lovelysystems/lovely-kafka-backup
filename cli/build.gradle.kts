plugins {
    kotlin("jvm")
    application
    `java-test-fixtures`
    id("io.gitlab.arturbosch.detekt")
    id("org.graalvm.buildtools.native")
    kotlin("kapt")
}

application {
    mainClass.set("ls.kafka.backup.cli.ApplicationKt")
}

graalvmNative {

    if (System.getenv("USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM") != "false") {
        error("set USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM to false")
        // this workaround is intended for removal by graal, see https://www.graalvm.org/release-notes/22_2/
    }

    binaries {
        named("main") {
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(11))
                vendor.set(JvmVendorSpec.matching("GraalVm"))
            })
        }
    }
}

kapt {
    arguments {
        arg("project", "${project.group}/${project.name}")
    }
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
