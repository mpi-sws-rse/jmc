buildscript {
    // Pin patched versions of vulnerable dependencies pulled transitively onto the
    // Shadow plugin classpath by com.gradleup.shadow:9.0.0-beta9:
    //  - plexus-utils 4.0.2: Directory Traversal in Expand.extractFile (< 4.0.3)
    //  - log4j-core 2.24.3: log injection / TLS hostname verification issues (< 2.25.4)
    // Remove each once a Shadow release bundles the patched version.
    configurations.classpath {
        resolutionStrategy {
            force("org.codehaus.plexus:plexus-utils:4.0.3")
            force("org.apache.logging.log4j:log4j-core:2.25.4")
            force("org.apache.logging.log4j:log4j-api:2.25.4")
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
    implementation("org.apache.logging.log4j:log4j-api:2.25.4")
    implementation("org.apache.logging.log4j:log4j-core:2.25.4")
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