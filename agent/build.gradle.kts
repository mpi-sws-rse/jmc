import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("checkstyle")
    id("com.gradleup.shadow") version "9.0.0-beta9"
    id("maven-publish")
}

repositories {
    mavenCentral()
    mavenLocal()
}

checkstyle {
    toolVersion = "10.19.0"
}

dependencies {
    implementation("net.bytebuddy:byte-buddy:1.17.1")
    implementation("net.bytebuddy:byte-buddy-agent:1.17.1")
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-util:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
}

task("agentJar", ShadowJar::class) {
    archiveVersion.set("")
    archiveClassifier.set("")

    configurations.add(project.configurations.getByName("runtimeClasspath"))

    manifest {
        attributes["Premain-Class"] = "org.mpisws.instrumentation.agent.InstrumentationAgent"
    }
}

tasks.test {
    useJUnitPlatform()
}

// Create a publication for the agent jar
publishing {
    publications {
        create<MavenPublication>("maven") {
            pom {
                name = "JMC Agent"
                description = "A Java agent for instrumenting Java programs"
                url = "github.com/mpi-sws-rse/jmc"
            }
            groupId = "org.mpisws"
            artifactId = "jmc-agent"
            version = "0.1.0"
            artifact(tasks["agentJar"])
        }
    }
    repositories {
        mavenLocal()
    }
}