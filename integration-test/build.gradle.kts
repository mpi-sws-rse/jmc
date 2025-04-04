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
    implementation("org.apache.logging.log4j:log4j-api:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
    agentDependencies("org.mpisws:jmc:0.1.0")
    agentDependencies("org.mpisws:jmc-agent:0.1.0")
    implementation("org.junit.platform:junit-platform-engine:1.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(project(":core"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
}

tasks.build {
    dependsOn(":agent:publishToMavenLocal")
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
    dependsOn(":agent:publishToMavenLocal")

    systemProperty("net.bytebuddy.nexus.disabled", "true")
    val agentJar = agentDependencies.find { it.name.contains("jmc-agent-0.1.0") }?.absolutePath

    val agentArg = "-javaagent:$agentJar=debug,instrumentingPackages=org.mpisws.jmc.test"
    jvmArgs(agentArg)
}