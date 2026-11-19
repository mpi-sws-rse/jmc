plugins {
    id("java")
    id("checkstyle")
    id("maven-publish")
    id("java-library")
//    signing
}

repositories {
    mavenCentral()
    mavenLocal()
}

checkstyle {
    toolVersion = "12.3.1"
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation("org.sosy-lab:java-smt:6.0.0")
    implementation("org.sosy-lab:javasmt-solver-z3:4.16.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlin", "kotlin-compiler", "1.9.22") // 1.9.22
    implementation("commons-cli:commons-cli:1.6.0")
    implementation("org.apache.logging.log4j:log4j-api:2.25.5")
    implementation("org.apache.logging.log4j:log4j-api-kotlin:1.5.0")
    implementation("org.apache.logging.log4j:log4j-core:2.25.5")
    implementation("org.junit.platform:junit-platform-engine:1.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")

}

// The build's identity for witness graph files: the JMC commit, from JMC_COMMIT if set (node trees
// copied without .git), else git (with -dirty for modified tracked files), else "unknown".
fun jmcCommit(): String {
    System.getenv("JMC_COMMIT")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    fun git(vararg a: String): String? = try {
        val p = ProcessBuilder(listOf("git") + a).directory(rootDir).redirectErrorStream(false).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        if (p.waitFor() == 0) out else null
    } catch (e: Exception) {
        null
    }
    // Only JMC's own repository (or worktree) counts: a tree copied into another checkout has no commit.
    val top = git("rev-parse", "--show-toplevel") ?: return "unknown"
    if (File(top).canonicalFile != rootDir.canonicalFile) {
        return "unknown"
    }
    val head = git("rev-parse", "HEAD") ?: return "unknown"
    val dirty = git("status", "--porcelain", "--untracked-files=no") ?: return "unknown"
    return if (dirty.isEmpty()) head else "$head-dirty"
}

val jmcBuildInfo by tasks.registering {
    val out = layout.buildDirectory.dir("generated/jmc-build")
    outputs.dir(out)
    outputs.upToDateWhen { false }
    doLast {
        val f = out.get().file("org/mpi_sws/jmc/jmc-build.properties").asFile
        f.parentFile.mkdirs()
        f.writeText("commit=" + jmcCommit() + "\n")
    }
}

sourceSets.main {
    resources.srcDir(jmcBuildInfo)
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("doctitle", "JMC Model Checker API")
    }
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
                url = "jmc.mpi-sws.org"
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("mpi-sws-rse")
                        name.set("MPI-SWS RSE Team")
                        email.set("rupak@mpi-sws.org")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/mpi-sws-rse/jmc.git")
                    developerConnection.set("scm:git:ssh://github.com/mpi-sws-rse/jmc.git")
                    url.set("https://github.com/mpi-sws-rse/jmc")
                }
            }
            artifactId = "jmc"
            from(components["java"])
        }
    }

    repositories {
        mavenLocal()
//        maven {
//            setUrl(layout.buildDirectory.dir("staging-deploy"))
//        }
    }
}

//signing {
//    sign(publishing.publications["maven"])
//}
