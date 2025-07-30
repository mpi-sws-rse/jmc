package org.mpi_sws.jmc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/**
 * Gradle plugin for applying the JMC agent to test tasks.
 */
class JmcPlugin : Plugin<Project> {
    /**
     * Applies the JMC agent to test tasks that contain the @JmcCheck annotation.
     *
     * This plugin adds the JMC agent as a Java agent to test tasks, allowing for runtime
     * instrumentation of the code under test. It also adds the necessary dependencies for
     * JMC to the test implementation configuration.
     */
    override fun apply(target: Project) {
        // Add dependencies of core testImplementation
        val extension = target.extensions.create("jmc", JmcExtension::class.java)
        val agentDependency = "${extension.agentJar}:${extension.version}"
        val libraryDependency = "${extension.libraryJar}:${extension.version}"
        val agentJar = target.configurations.detachedConfiguration(
            target.dependencies.create(agentDependency)
        ).resolve().first()
        val libraryJar = target.configurations.detachedConfiguration(
            target.dependencies.create(libraryDependency)
        ).resolve().first()

        target.dependencies.add("testImplementation", libraryDependency)

        target.tasks.withType(Test::class.java).configureEach { testTask ->
            testTask.doFirst {
                if (shouldApplyAgent(testTask)) {
                    var agentArg = "-javaagent:$agentJar=jmcRuntimeJarPath=$libraryJar"
                    var addArgs = ""
                    if (extension.debug) {
                        addArgs += ",debug,debugSavePath=${extension.debugPath}"
                    }
                    if (extension.instrumentingPackage.isNotEmpty()) {
                        if (addArgs.isNotEmpty()) {
                            addArgs += ","
                        }
                        addArgs += "instrumentingPackages=${extension.instrumentingPackage.joinToString(";")}"
                    }
                    if (extension.excludedPackages.isNotEmpty()) {
                        if (addArgs.isNotEmpty()) {
                            addArgs += ","
                        }
                        addArgs += "excludedPackages=${extension.excludedPackages.joinToString(";")}"
                    }
                    if (addArgs.isNotEmpty()) {
                        agentArg += addArgs
                    }
                    testTask.jvmArgs(agentArg)
                }
            }
        }
    }

    private fun shouldApplyAgent(testTask: Test): Boolean {
        return testTask.testClassesDirs.files.any { classDir ->
            classDir.walkTopDown().any { file ->
                file.extension == "class" && file.readText().contains("@JmcCheck")
            }
        }
    }
}

/**
 * Extension for configuring the JMC plugin.
 */
open class JmcExtension {
    // Default values for the JMC plugin configuration
    /**
     * The version of the JMC agent and library to use.
     * Default is "0.1.0".
     */
    var version: String = "0.1.0"

    /**
     * The Maven coordinates of the JMC agent JAR.
     * Default is "org.mpi_sws.jmc:jmc-agent".
     */
    var agentJar: String = "org.mpi_sws.jmc:jmc-agent"

    /**
     * The Maven coordinates of the JMC library JAR.
     * Default is "org.mpi_sws.jmc:jmc".
     */
    var libraryJar: String = "org.mpi_sws.jmc:jmc"

    /**
     * Whether to enable debug mode for the JMC agent.
     * Default is false.
     */
    var debug: Boolean = false

    /**
     * The path where debug information will be saved.
     * Default is "build/generated/instrumented".
     */
    var debugPath: String = "build/generated/instrumented"

    /**
     * The list of packages to instrument.
     * Default is an empty list.
     */
    var instrumentingPackage: List<String> = ArrayList()

    /**
     * The list of packages to exclude from instrumentation.
     * Default is an empty list.
     */
    var excludedPackages: List<String> = ArrayList()
}