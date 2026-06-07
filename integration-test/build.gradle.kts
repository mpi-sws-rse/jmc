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

// Test task with SYNC_POINTS_ONLY instrumentation level (no field read/write yields).
// Use this to verify that ConcurrentHashMap and CompletableFuture scenarios work
// when field-level instrumentation is disabled.
tasks.register<Test>("testSyncPointsOnly") {
    useJUnitPlatform()
    dependsOn(":agent:agentJar")

    val agentJar = project(":agent").projectDir.resolve("build/libs").resolve("agent.jar").absolutePath
    val jmcRuntimeJar = project(":core").projectDir.resolve("build/libs/core-0.1.2.jar").absolutePath

    val agentArg =
        "-javaagent:$agentJar=debug,instrumentingPackages=org.mpi_sws.jmc.test,instrumentationLevel=sync_points_only,jmcRuntimeJarPath=$jmcRuntimeJar"
    jvmArgs(agentArg)

    // Only run the caffeine reproduction tests
    include("**/caffeine/**")

    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}