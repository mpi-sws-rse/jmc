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

    // Pin patched versions of Checkstyle's vulnerable transitive dependencies,
    // pulled in by com.puppycrawl.tools:checkstyle 10.19.0:
    //  - plexus-utils 3.3.0: Directory Traversal in Expand.extractFile (< 3.6.1)
    //  - commons-beanutils 1.9.4: Improper Access Control / RCE via enum
    //    declaredClass (<= 1.10.1, patched in 1.11.0)
    //  - commons-lang3 3.8.1: Uncontrolled Recursion / StackOverflowError in
    //    ClassUtils.getClass on long inputs (< 3.18.0)
    // Applies only to modules that use Checkstyle. Remove if/when the Checkstyle
    // toolVersion is bumped to a release that no longer ships these vulnerable versions.
    plugins.withId("checkstyle") {
        configurations.named("checkstyle") {
            resolutionStrategy {
                force("org.codehaus.plexus:plexus-utils:3.6.1")
                force("commons-beanutils:commons-beanutils:1.11.0")
                force("org.apache.commons:commons-lang3:3.18.0")
            }
        }
    }
}