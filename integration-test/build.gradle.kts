buildscript {
    // Force patched plexus-utils to fix the Directory Traversal vulnerability in
    // org.codehaus.plexus.util.Expand.extractFile. It is pulled transitively by
    // com.gradleup.shadow:9.0.0-beta9, which bundles the vulnerable 4.0.2.
    // Remove this once a Shadow release bundles plexus-utils >= 4.0.3.
    configurations.classpath {
        resolutionStrategy {
            force("org.codehaus.plexus:plexus-utils:4.0.3")
        }
    }
}

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
    implementation("org.apache.logging.log4j:log4j-api:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
    implementation("org.junit.platform:junit-platform-engine:1.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation(project(":core"))
    implementation("com.google.guava:guava:32.1.2-jre")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    implementation("org.junit.jupiter:junit-jupiter:5.7.1")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(":agent:agentJar")

    val agentJar = project(":agent").projectDir.resolve("build/libs").resolve("agent.jar").absolutePath
    val jmcRuntimeJar = project(":core").projectDir.resolve("build/libs/core-0.1.2.jar").absolutePath

    val agentArg =
        "-javaagent:$agentJar=debug,instrumentingPackages=org.mpi_sws.jmc.test,jmcRuntimeJarPath=$jmcRuntimeJar"
    jvmArgs(agentArg)
}