plugins {
    kotlin("jvm") version "1.9.22"
    id("maven-publish")
}

kotlin {
    jvmToolchain(17)
}

tasks.assemble {
    dependsOn(":agent:agentJar")
}

tasks.test {
    useJUnitPlatform()

    dependsOn(":agent:agentJar")
}

repositories {
    mavenCentral()
    mavenLocal()
}

allprojects {
    group = "org.mpi-sws.jmc"
    version = "0.1.2"

    // Fix Directory Traversal (Expand.extractFile) in plexus-utils, pulled
    // transitively as 3.3.0 by com.puppycrawl.tools:checkstyle 10.19.0.
    // Applies only to modules that use Checkstyle. Remove if/when the Checkstyle
    // toolVersion is bumped to a release that no longer ships plexus-utils < 3.6.1.
    plugins.withId("checkstyle") {
        configurations.named("checkstyle") {
            resolutionStrategy {
                force("org.codehaus.plexus:plexus-utils:3.6.1")
            }
        }
    }
}