plugins {
    id("java")
    id("checkstyle")
    kotlin("jvm") version "1.9.22"
}

group = "org.mpisws"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}


checkstyle {
    toolVersion = "10.19.0"
}


dependencies {
    implementation("org.sosy-lab:java-smt:5.0.1")
    implementation("org.sosy-lab:javasmt-solver-z3:4.13.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("net.bytebuddy:byte-buddy:1.17.1")
    implementation("net.bytebuddy:byte-buddy-agent:1.17.1")
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-util:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
    //implementation("org.jetbrains.kotlin", "kotlin-compiler", "1.9.22") // 1.9.22
    implementation("commons-cli:commons-cli:1.6.0")
    implementation("org.apache.logging.log4j:log4j-api:2.24.3")
    implementation("org.apache.logging.log4j:log4j-api-kotlin:1.5.0")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
    implementation("org.junit.platform:junit-platform-engine:1.11.3")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
