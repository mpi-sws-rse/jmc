package org.mpi_sws.jmc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/**
 * Gradle plugin for applying the JMC agent to test tasks.
 *
 * Simple project usage (build.gradle.kts):
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
 *
 * Multi-project usage (root build.gradle / build.gradle.kts):
 * ```
 * plugins {
 *     id("org.mpi_sws.jmc.gradle") version "0.1.2"
 * }
 *
 * jmc {
 *     version = "0.1.2"
 *     target = ":iceberg-core"
 *     instrumentingPackage = listOf("org.apache.iceberg")
 *     excludedPackages = listOf("org.apache.iceberg.relocated")
 * }
 * ```
 *
 * The plugin creates a `jmcTest` task on the target project that runs only JMC tests,
 * leaving the regular `test` task unaffected. Run with:
 *   ./gradlew :iceberg-core:jmcTest --tests org.apache.iceberg.TestInMemoryCatalogJmc
 */
class JmcPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("jmc", JmcExtension::class.java)

        project.afterEvaluate {
            val agentDependency = "${extension.agentJar}:${extension.version}"
            val libraryDependency = "${extension.libraryJar}:${extension.version}"

            // Resolve JARs non-transitively to avoid conflicts with host project's
            // dependency management (e.g. Guava exclusions in Iceberg).
            if (project.repositories.isEmpty()) {
                project.repositories.mavenLocal()
                project.repositories.mavenCentral()
            }

            val agentJarFile = project.configurations.detachedConfiguration(
                project.dependencies.create(agentDependency)
            ).apply { isTransitive = false }.resolve().first()

            val libraryJarFile = project.configurations.detachedConfiguration(
                project.dependencies.create(libraryDependency)
            ).apply { isTransitive = false }.resolve().first()

            // Determine which project to configure
            val targetProject = if (extension.target.isEmpty()) {
                project
            } else {
                project.project(extension.target)
            }

            // Build the agent arg string
            val agentArgs = buildAgentArgs(extension, libraryJarFile)
            val agentArg = "-javaagent:$agentJarFile=${agentArgs.joinToString(",")}"

            val taskName = extension.testTask

            val configureTarget = {
                // Add the JMC library as a test dependency
                targetProject.dependencies.add("testImplementation", libraryDependency)

                if (taskName.isEmpty()) {
                    // No specific task — attach to all Test tasks (simple project mode)
                    targetProject.tasks.withType(Test::class.java).configureEach { testTask ->
                        testTask.doFirst { testTask.jvmArgs(agentArg) }
                    }
                } else {
                    // Create the task if it doesn't exist, then configure it
                    val jmcTask: Test = if (targetProject.tasks.findByName(taskName) == null) {
                        targetProject.tasks.create(taskName, Test::class.java) { t ->
                            t.description = "Runs tests with the JMC model checker agent attached."
                            t.group = "verification"
                            t.useJUnitPlatform()
                            // Inherit test classpath from the existing test source set
                            val testSourceSet = targetProject.extensions
                                .getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
                                .sourceSets.getByName("test")
                            t.testClassesDirs = testSourceSet.output.classesDirs
                            t.classpath = testSourceSet.runtimeClasspath
                        }
                    } else {
                        targetProject.tasks.named(taskName, Test::class.java).get()
                    }
                    jmcTask.doFirst { jmcTask.jvmArgs(agentArg) }
                }
            }

            if (targetProject === project) {
                configureTarget()
            } else {
                targetProject.afterEvaluate { configureTarget() }
            }
        }
    }

    private fun buildAgentArgs(extension: JmcExtension, libraryJarFile: java.io.File): List<String> {
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
        return args
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

    /**
     * Target subproject path for multi-project builds (e.g. ":iceberg-core").
     * When empty (default), the plugin configures the project it is applied to.
     */
    var target: String = ""

    /**
     * Name of the test task to attach the agent to.
     * When set (e.g. "jmcTest"), the plugin creates a dedicated task if it doesn't exist,
     * keeping the regular `test` task unaffected.
     * When empty (default), the agent is attached to all Test tasks.
     */
    var testTask: String = ""

    /** Whether to enable debug mode for the JMC agent (dumps instrumented bytecode). */
    var debug: Boolean = false

    /** The path where debug information will be saved. */
    var debugPath: String = "build/generated/instrumented"

    /** The list of packages to instrument. */
    var instrumentingPackage: List<String> = ArrayList()

    /** The list of packages to exclude from instrumentation. */
    var excludedPackages: List<String> = ArrayList()
}
