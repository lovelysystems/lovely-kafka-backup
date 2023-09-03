import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm")
    id("com.lovelysystems.gradle")
    id("com.github.johnrengelman.shadow")
}

lovely {
    gitProject()
    dockerProject("lovelysystems/lovely-kafka-backup") {
        from("docker")
        from(project("confluent-connect").tasks["shadowJar"]) {
            into("confluent-connect-libs")
        }
    }
}

if (JavaVersion.current() != JavaVersion.VERSION_11) {
    // we require Java 11 here, to ensure we are always using the same version as the docker images are using
    error("Java 11 is required for this Project, found ${JavaVersion.current()}")
}

subprojects {
    version = rootProject.version
    // ensure that java 11 is used in all kotlin projects
    extensions.findByType<KotlinJvmProjectExtension>()?.apply {
        jvmToolchain {
            (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(11))
        }
    }
}

kotlin {
    jvmToolchain(11)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
