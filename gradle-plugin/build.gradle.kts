plugins {
    id("java")
    id("java-gradle-plugin")
    id("maven-publish")
    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "org.mpi_sws.jmc.gradle"
version = "0.1.0"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(project(":agent"))
}

tasks.named("pluginUnderTestMetadata") {
    dependsOn(":agent:agentJar")
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("jmc") {
            id = "org.mpi_sws.jmc"
            implementationClass = "org.mpi_sws.jmc.gradle.JmcPlugin"
            displayName = "JMC Plugin"
            description = "A plugin for the JMC model checker"
            tags = listOf("model-checker", "verification")
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}
