plugins {
    id("java")
    kotlin("jvm")
    id("com.gradle.plugin-publish") version "1.3.1"
    signing
}

group = "org.mpi_sws.jmc.gradle"
version = "0.1.0"

repositories {
    mavenCentral()
    mavenLocal()
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    website.set("https://jmc.mpi-sws.org")
    vcsUrl.set("https://github.com/mpi-sws-rse/jmc.git")
    plugins {
        create("jmc") {
            id = "org.mpi_sws.jmc.gradle"
            implementationClass = "org.mpi_sws.jmc.gradle.JmcPlugin"
            displayName = "JMC Plugin"
            description = "A plugin for the JMC model checker - jmc.mpi-sws.org"
            tags.set(listOf("jmc", "model-checker", "java"))
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}
