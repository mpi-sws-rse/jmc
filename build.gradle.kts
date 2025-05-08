plugins {
    id("java")
    kotlin("jvm") version "1.9.22"
}

group = "org.mpisws"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
//    ivy {
//        url = uri("https://www.sosy-lab.org/ivy")
//        patternLayout {
//            ivy("[organisation]/[module]/ivy-[revision].xml")
//            artifact("[organisation]/[module]/[artifact]-[revision](-[classifier]).[ext]")
//        }
//    }
}

dependencies {
    implementation("org.sosy-lab:java-smt:5.0.1")
    implementation("org.sosy-lab:javasmt-solver-z3:4.13.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-util:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
    //implementation("org.jetbrains.kotlin", "kotlin-compiler", "1.9.22") // 1.9.22
    implementation("commons-cli:commons-cli:1.6.0")
    implementation("org.apache.logging.log4j:log4j-api:2.22.1")
    implementation("org.apache.logging.log4j:log4j-core:2.22.1")
}

tasks.test {
    useJUnitPlatform()
}

//   to run a test in shell: gradle-profiler --profile async-profiler-all --project-dir . runTrustTestDetLazyListI
/*tasks.register<Test>("runTrustTestDetLazyListI") {
    useJUnitPlatform()
    filter {
        includeTestsMatching("org.mpisws.checker.ModelCheckerTest.trustTestDetLazyListI")
    }
}

tasks.register<Test>("nonDetArray") {
    useJUnitPlatform()
    filter {
        includeTestsMatching("org.mpisws.checker.ModelCheckerTest.trustTestNondetArray")
    }
}

tasks.register<Test>("detArray") {
    useJUnitPlatform()
    filter {
        includeTestsMatching("org.mpisws.checker.ModelCheckerTest.trustTestDetArray")
    }
}*/

kotlin {
    jvmToolchain(17)
}
