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
    implementation("org.ow2.asm:asm:9.8")
    testImplementation("org.ow2.asm:asm-util:9.8")
    implementation(project(":core"))
    implementation("org.junit.platform:junit-platform-engine:1.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
}

task("agentJar", ShadowJar::class) {
    archiveVersion.set("")
    archiveClassifier.set("")
    mergeServiceFiles()
    configurations.add(project.configurations.getByName("runtimeClasspath"))
    from(sourceSets["main"].output)

    manifest {
        attributes["Agent-Class"] = "org.mpisws.jmc.agent.InstrumentationAgent"
        attributes["Premain-Class"] = "org.mpisws.jmc.agent.InstrumentationAgent"
        attributes["Can-Redefine-Classes"] = "true"
        attributes["Can-Retransform-Classes"] = "true"
    }
}

tasks.build {
    dependsOn("agentJar")
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