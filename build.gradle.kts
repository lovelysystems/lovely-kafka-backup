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
            into("libs")
        }
    }
}

if (JavaVersion.current() != JavaVersion.VERSION_11) {
    // we require Java 11 here, to ensure we are always using the same version as the docker images are using
    error("Java 11 is required for this Project, found ${JavaVersion.current()}")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}
