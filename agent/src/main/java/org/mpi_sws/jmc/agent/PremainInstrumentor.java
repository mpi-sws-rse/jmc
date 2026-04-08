package org.mpi_sws.jmc.agent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.agent.visitors.*;
import org.mpi_sws.jmc.checker.exceptions.JmcUnsupportedFeatureException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.nio.file.Files;
import java.security.ProtectionDomain;
import java.util.Arrays;

/**
 * The PremainInstrumentor class is responsible for transforming classes during the premain phase of
 * the Java agent lifecycle. It applies various instrumentation visitors to classes that match the
 * specified criteria.
 */
public class PremainInstrumentor implements ClassFileTransformer {
    private static final Logger LOGGER = LogManager.getLogger(PremainInstrumentor.class);

    private final AgentArgs agentArgs;
    private final JmcMatcher matcher;

    /**
     * Constructs a new PremainInstrumentor with the specified agent arguments.
     *
     * @param agentArgs the agent arguments containing configuration for instrumentation
     */
    public PremainInstrumentor(AgentArgs agentArgs) {
        this.agentArgs = agentArgs;
        this.matcher =
                new JmcMatcher(
                        agentArgs.getInstrumentingPackages(), agentArgs.getExcludedPackages());
    }

    /**
     * Transforms the class file buffer of a class being loaded or redefined.
     *
     * <p>Specifically, if the class matches the arguments provided to the agent, it applies the
     * following visitors in order:
     *
     * <ul>
     *   <li>JmcIgnoreVisitor: Checks if the class has the JmcIgnoreInstrumentation annotation.
     *   <li>JmcSyncScanVisitor: Scans the class for synchronized methods and collects data.
     *   <li>JmcSyncMethodVisitor: Instruments synchronized methods based on the collected data.
     *   <li>JmcFutureVisitor: Instruments classes related to futures and executors.
     *   <li>JmcAtomicVisitor: Instruments atomic classes.
     *   <li>JmcReentrantLockVisitor: Instruments reentrant locks.
     *   <li>JmcThreadVisitor: Instruments thread-related classes.
     *   <li>JmcReadWriteVisitor: Instruments read-write calls throughout.
     * </ul>
     *
     * @param loader the defining loader of the class to be transformed, may be {@code null} if the
     *     bootstrap loader
     * @param className the name of the class in the internal form of fully qualified class and
     *     interface names as defined in <i>The Java Virtual Machine Specification</i>. For example,
     *     <code>"java/util/List"</code>.
     * @param classBeingRedefined if this is triggered by a redefine or retransform, the class being
     *     redefined or retransformed; if this is a class load, {@code null}
     * @param protectionDomain the protection domain of the class being defined or redefined
     * @param classFileBuffer the input byte buffer in class file format - must not be modified
     * @return the transformed class file buffer, or the original
     */
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classFileBuffer)
            throws IllegalClassFormatException, JmcUnsupportedFeatureException {
        String finalClassName = className.replace("/", ".");
        byte[] copiedClassBuffer = Arrays.copyOf(classFileBuffer, classFileBuffer.length);

        if (!this.matcher.matches(finalClassName, loader)) {
            return copiedClassBuffer;
        }

        try {
            ClassReader tempCr = new ClassReader(copiedClassBuffer);
            ClassWriter tempCw =
                    new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

            JmcIgnoreVisitor ignoreVisitor = new JmcIgnoreVisitor(tempCw);
            tempCr.accept(
                    ignoreVisitor,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            if (ignoreVisitor.hasIgnoreAnnotation()) {
                return copiedClassBuffer; // Skip instrumentation if the class has
                // JmcIgnoreInstrumentation annotation
            }

            LOGGER.info("Instrumenting class: {}", finalClassName);
            byte[] transformed = JmcVisitor.transform(copiedClassBuffer);
            if (this.agentArgs.isDebug()) {
                record(className, transformed);
            }
            return transformed;
        } catch (Exception e) {
            if (e instanceof JmcUnsupportedFeatureException) {
                throw new JmcUnsupportedFeatureException(e.getMessage());
            } else {
            LOGGER.info("Error transforming class: {} {}", finalClassName, e);
            throw new IllegalClassFormatException(
                    "Error instrumenting class: " + finalClassName + " Error: " + e);
            }
        }
    }

    public void record(String className, byte[] classFileBuffer) {
        String outputDir = this.agentArgs.getDebugSavePath();
        File outFile = new File(outputDir + "/" + className + ".class");
        try {
            LOGGER.debug("Recording instrumented class: {}", className);
            outFile.getParentFile().mkdirs();
            Files.write(outFile.toPath(), classFileBuffer);
        } catch (Exception e) {
            LOGGER.error("Error writing to file: {} {}", outFile.getAbsolutePath(), e);
        }
    }
}
