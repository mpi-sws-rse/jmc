package org.mpisws.jmc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

class JmcPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // Add dependencies of core testImplementation
        val extension = project.extensions.create("jmc", JmcExtension::class.java)
        val agentDependency = "${extension.agentJar}:${extension.version}"
        val libraryDependency = "${extension.libraryJar}:${extension.version}"
        val agentJar = project.configurations.detachedConfiguration(
            project.dependencies.create(agentDependency)
        ).resolve().first()

        project.dependencies.add("testImplementation", libraryDependency)

        project.tasks.withType(Test::class.java).configureEach { testTask ->
            testTask.doFirst {
                val jmcVersion = extension.version

                if (shouldApplyAgent(testTask)) {
                    val agentArg = "-javaagent:$agentJar"
                    if (extension.debug) {
                        agentArg += "=debug,debugSavePath=${extension.debugPath}"
                    }
                    jvmArgs(agentArg)
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

open class JmcExtension {
    var version: String = "0.1.0"
    var agentJar: String = "org.mpisws:jmc-agent"
    var libraryJar: String = "org.mpisws:jmc"
    var debug: Boolean = false
    var debugPath: String = "build/generated/instrumented"
}