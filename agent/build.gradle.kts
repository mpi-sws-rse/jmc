import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("checkstyle")
    id("com.gradleup.shadow") version "9.0.0-beta9"
    kotlin("jvm") version "1.9.22"
}

repositories {
    mavenCentral()
    mavenLocal()
}

checkstyle {
    toolVersion = "10.19.0"
}

task("agentJar", ShadowJar::class) {
    archiveVersion.set("")
    archiveClassifier.set("agent")

    configurations.add(project.configurations.getByName("runtimeClasspath"))

    manifest {
        attributes["Premain-Class"] = "org.mpisws.instrumentation.agent.InstrumentationAgent"
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}