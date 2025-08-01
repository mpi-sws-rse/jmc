import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("checkstyle")
    id("maven-publish")
    id("com.gradleup.shadow") version "9.0.0-beta9"
    id("java-library")
    signing
}

repositories {
    mavenCentral()
    mavenLocal()
}

checkstyle {
    toolVersion = "10.19.0"
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("doctitle", "JMC Model Checker Agent API")
    }
}

dependencies {
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-util:9.8")
    implementation(project(":core"))
    implementation("org.junit.platform:junit-platform-engine:1.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
}

tasks.register("agentJar", ShadowJar::class) {
    archiveVersion.set("")
    archiveClassifier.set("")
    mergeServiceFiles()
    configurations.add(project.configurations.getByName("runtimeClasspath"))
    from(sourceSets["main"].output)

    manifest {
        attributes["Premain-Class"] = "org.mpi_sws.jmc.agent.InstrumentationAgent"
        attributes["Can-Redefine-Classes"] = "true"
        attributes["Can-Retransform-Classes"] = "true"
    }
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("doctitle", "JMC Model Checker Agent API")
    }
}

tasks.register<Jar>("agentJavadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.named("javadoc"))
}

tasks.register<Jar>("agentSourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

tasks.assemble {
    dependsOn("agentJar")
}

tasks.test {
    useJUnitPlatform()

    dependsOn("agentJar")
}

// Create a publication for the agent jar
publishing {
    publications {
        create<MavenPublication>("maven") {
            pom {
                name = "JMC Agent"
                description = "A Java agent for instrumenting Java programs"
                url = "github.com/mpi-sws-rse/jmc"
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("mpi-sws-rse")
                        name.set("MPI-SWS RSE Team")
                        email.set("rupak@mpi-sws.org")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/mpi-sws-rse/jmc.git")
                    developerConnection.set("scm:git:ssh://github.com/mpi-sws-rse/jmc.git")
                    url.set("https://github.com/mpi-sws-rse/jmc")
                }
            }
            artifactId = "jmc-agent"
            artifact(tasks["agentSourcesJar"])
            artifact(tasks["agentJavadocJar"])
            artifact(tasks["agentJar"])
        }
    }
    repositories {
        mavenLocal()
        maven {
            setUrl(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

signing {
    sign(publishing.publications["maven"])
}