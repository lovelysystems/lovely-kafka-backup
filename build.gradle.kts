import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm")
    id("com.lovelysystems.gradle")
    id("com.github.johnrengelman.shadow")
    id("io.gitlab.arturbosch.detekt") apply false
}

lovely {
    gitProject()
    dockerProject("ghcr.io/lovelysystems/lovely-kafka-backup") {
        from("docker")
        from(project("confluent-connect").tasks["shadowJar"]) {
            into("confluent-connect-libs")
        }
        from(project("cli").tasks["shadowJar"]) {
            into("backup-cli-libs")
            rename {
                "cli.jar"
            }
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
            languageVersion.set(JavaLanguageVersion.of(11))
        }
    }
    // Moving the test result to one directory to be able to store all of them on CircleCI
    tasks.register("moveTestResults", Copy::class) {
        from(layout.buildDirectory.dir("test-results/test"))
        into("${rootProject.projectDir}/build/test-results/${project.name}")
    }
    tasks.withType<Test> {
        finalizedBy("moveTestResults")
    }
}

kotlin {
    jvmToolchain(11)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

fun getFormattedProjectName(project: Project): String = ":${project.name}"

/**
 *  Groups together all the known Detekt tasks & adds the non-default ones to the given subproject's "check" task
 */
val detektAll by tasks.registering(Detekt::class) {
    subprojects.forEach { project ->
        project.getAllTasks(true).values.flatten().forEach { task ->
            if (task.name == "detekt") {
                val projectName = getFormattedProjectName(task.project)
                dependsOn("$projectName:${task.name}")

                /**
                 * Normal :detekt tasks don't find all code-style issues but :detektMain and :detektTest do.
                 * Adding dependency on those task so :detekt finds the correct issues and can be run for just one subproject.
                 */
                task.dependsOn += "$projectName:detektMain"
                task.dependsOn += "$projectName:detektTest"
            }
        }
    }
}
