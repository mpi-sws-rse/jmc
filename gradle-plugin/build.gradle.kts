plugins {
    kotlin("jvm") version "2.0.0"
    id("java")
    id("java-gradle-plugin")
}

group = "org.mpisws.jmc.gradle"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
}

tasks.test {
    useJUnitPlatform()
}