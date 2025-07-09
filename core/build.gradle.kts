plugins {
    kotlin("jvm") version "1.9.22"
    id("java")
    id("checkstyle")
    id("maven-publish")
    id("java-library")
}

group = "org.mpisws.jmc"
version = "0.1.0"

repositories {
    mavenCentral()
    mavenLocal()
}

checkstyle {
    toolVersion = "10.19.0"
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation("org.sosy-lab:java-smt:5.0.1")
    implementation("org.sosy-lab:javasmt-solver-z3:4.13.3")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlin", "kotlin-compiler", "1.9.22") // 1.9.22
    implementation("commons-cli:commons-cli:1.6.0")
    implementation("org.apache.logging.log4j:log4j-api:2.24.3")
    implementation("org.apache.logging.log4j:log4j-api-kotlin:1.5.0")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
    implementation("org.junit.platform:junit-platform-engine:1.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            pom {
                name = "JMC Model Checker"
                description = "A generic model checker for Java programs"
                url = "github.com/mpi-sws-rse/jmc"
            }
            groupId = "org.mpisws"
            artifactId = "jmc"
            version = "0.1.0"
            from(components["java"])
        }
    }

    repositories {
        mavenLocal()
    }
}