plugins {
    kotlin("jvm") version "2.4.20-RC"
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

    // The Kotlin Gradle Plugin is held at 2.4.20-RC (see the plugins block) because
    // that is the earliest release fixing the unsafe deserialization of Gradle
    // build-cache metadata, which allowed code execution (affects < 2.4.20-Beta1).
    // No stable release carries the fix yet — the latest stable, 2.4.10, is still
    // affected.
    //
    // The KGP version also sets the kotlin-stdlib version recorded in the published
    // gradle-plugin POM, so pin the core libraries to the latest stable release:
    // consumers of the JMC Gradle plugin must not inherit a pre-release stdlib.
    // coreLibrariesVersion is per-project, hence configuring it for every module
    // that applies the Kotlin plugin rather than only the root. Remove this block
    // once 2.4.20 ships as stable and the plugins block is moved onto it.
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            coreLibrariesVersion = "2.4.10"
        }
    }

    // Pin patched versions of Checkstyle's vulnerable transitive dependencies,
    // pulled in by com.puppycrawl.tools:checkstyle 12.3.1 via maven-doxia 1.12.0:
    //  - plexus-utils 3.3.0: Directory Traversal in Expand.extractFile (< 3.6.1)
    //  - commons-lang3 3.8.1: Uncontrolled Recursion / StackOverflowError in
    //    ClassUtils.getClass on long inputs (< 3.18.0)
    // Applies only to modules that use Checkstyle. Remove if/when the Checkstyle
    // toolVersion is bumped to a release that no longer ships these vulnerable versions.
    //
    // Note: Checkstyle is capped at 12.x while this build targets JDK 17 —
    // Checkstyle 13.x is compiled for Java 21 (class file 65) and fails at
    // execution time on a Java 17 runtime.
    plugins.withId("checkstyle") {
        configurations.named("checkstyle") {
            resolutionStrategy {
                force("org.codehaus.plexus:plexus-utils:3.6.1")
                force("org.apache.commons:commons-lang3:3.18.0")
            }
        }
    }
}