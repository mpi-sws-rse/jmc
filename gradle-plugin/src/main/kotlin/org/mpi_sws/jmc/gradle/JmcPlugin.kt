package org.mpi_sws.jmc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/**
 * Gradle plugin for applying the JMC agent to test tasks.
 *
 * Usage in build.gradle.kts:
 * ```
 * plugins {
 *     id("org.mpi_sws.jmc.gradle") version "0.1.2"
 * }
 *
 * jmc {
 *     version = "0.1.2"
 *     instrumentingPackage = listOf("com.example.myapp")
 * }
 * ```
 */
class JmcPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create("jmc", JmcExtension::class.java)

        // Defer all dependency resolution and task configuration until after
        // the user's build script has finished configuring the extension.
        target.afterEvaluate {
            val agentDependency = "${extension.agentJar}:${extension.version}"
            val libraryDependency = "${extension.libraryJar}:${extension.version}"

            val agentJarFile = target.configurations.detachedConfiguration(
                target.dependencies.create(agentDependency)
            ).resolve().first()

            val libraryJarFile = target.configurations.detachedConfiguration(
                target.dependencies.create(libraryDependency)
            ).resolve().first()

            target.dependencies.add("testImplementation", libraryDependency)

            target.tasks.withType(Test::class.java).configureEach { testTask ->
                testTask.doFirst {
                    val args = mutableListOf<String>()

                    args.add("jmcRuntimeJarPath=$libraryJarFile")

                    if (extension.debug) {
                        args.add("debug")
                        args.add("debugSavePath=${extension.debugPath}")
                    }

                    if (extension.instrumentingPackage.isNotEmpty()) {
                        args.add("instrumentingPackages=${extension.instrumentingPackage.joinToString(";")}")
                    }

                    if (extension.excludedPackages.isNotEmpty()) {
                        args.add("excludedPackages=${extension.excludedPackages.joinToString(";")}")
                    }

                    val agentArg = "-javaagent:$agentJarFile=${args.joinToString(",")}"
                    testTask.jvmArgs(agentArg)
                }
            }
        }
    }
}

/**
 * Extension for configuring the JMC plugin.
 */
open class JmcExtension {
    /** The version of the JMC agent and library to use. */
    var version: String = "0.1.2"

    /** The Maven coordinates of the JMC agent JAR. */
    var agentJar: String = "org.mpi-sws.jmc:jmc-agent"

    /** The Maven coordinates of the JMC library JAR. */
    var libraryJar: String = "org.mpi-sws.jmc:jmc"

    /** Whether to enable debug mode for the JMC agent. */
    var debug: Boolean = false

    /** The path where debug information will be saved. */
    var debugPath: String = "build/generated/instrumented"

    /** The list of packages to instrument. */
    var instrumentingPackage: List<String> = ArrayList()

    /** The list of packages to exclude from instrumentation. */
    var excludedPackages: List<String> = ArrayList()
}
