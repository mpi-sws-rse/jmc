plugins {
    kotlin("jvm") version "1.9.21"
}

group = "org.mpisws"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-util:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")
    implementation("org.jetbrains.kotlin", "kotlin-compiler", "1.9.22") // 1.9.22
    implementation("commons-cli:commons-cli:1.6.0")
    implementation("org.apache.logging.log4j:log4j-api:2.22.1")
    implementation("org.apache.logging.log4j:log4j-core:2.22.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
