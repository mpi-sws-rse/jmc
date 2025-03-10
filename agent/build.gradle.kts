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

val agentDependencies by configurations.creating

dependencies {
    implementation("net.bytebuddy:byte-buddy:1.17.1")
    implementation("org.mpisws:jmc:0.1.0")
    agentDependencies("org.mpisws:jmc:0.1.0")
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
        attributes["Premain-Class"] = "org.mpisws.jmc.agent.InstrumentationAgent"
        attributes["Can-Redefine-Classes"] = "true"
        attributes["Can-Retransform-Classes"] = "true"
    }
}

tasks.build {
    dependsOn("agentJar")
}

tasks.register<Copy>("copyJar") {
    from(agentDependencies.filter { it.name.contains("jmc-0.1.0") })
    into("src/main/resources/lib")
}

tasks.processResources {
    dependsOn("copyJar")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(":core:publishToMavenLocal")
    dependsOn("agentJar")
    systemProperty("net.bytebuddy.nexus.disabled", "true")
    val agentJar = tasks["agentJar"].outputs.files.singleFile
    val agentArg = "-javaagent:$agentJar=debug,instrumentingPackages=org.mpisws.jmc.agent"
    jvmArgs(agentArg)
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