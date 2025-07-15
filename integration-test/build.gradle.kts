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
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
}

tasks.register<Copy>("copyJar") {
    dependsOn(":core:jar")
    from(project(":core").projectDir.resolve("build/libs/core-0.1.0.jar").absolutePath)
    into("src/main/resources/lib")
    rename("jmc-0.1.0.jar", "jmc-0.1.0.jar")
}

tasks.processResources {
    dependsOn("copyJar")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(":agent:agentJar")

    val agentJar = project(":agent").projectDir.resolve("build/libs").resolve("agent.jar").absolutePath

    val agentArg = "-javaagent:$agentJar=debug,instrumentingPackages=org.mpisws.jmc.test"
    jvmArgs(agentArg)
}