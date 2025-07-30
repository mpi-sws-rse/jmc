plugins {
    kotlin("jvm") version "1.9.22"
    id("maven-publish")
}

kotlin {
    jvmToolchain(17)
}

tasks.assemble {
    dependsOn(":agent:agentJar")
}

tasks.test {
    useJUnitPlatform()

    dependsOn(":agent:agentJar")
}

repositories {
    mavenCentral()
    mavenLocal()
}

allprojects {
    group = "org.mpi-sws.jmc"
    version = "0.1.0"
}