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
 * The {@link ClassFileTransformer} that JMC registers with the JVM during the premain phase.
 *
 * <p>The JVM calls {@link #transform} for every class as it is loaded. This class is the bridge
 * between the agent's configuration and the instrumentation pipeline: it uses a {@link JmcMatcher}
 * (built from {@link AgentArgs}) to decide whether a class is in scope, skips classes annotated with
 * {@code @JmcIgnoreInstrumentation}, and otherwise delegates the actual bytecode rewriting to {@link
 * JmcVisitor#transform}. When debug mode is enabled it also persists each instrumented class to disk.
 */
public class PremainInstrumentor implements ClassFileTransformer {
    /** Logger for instrumentation progress and errors. */
    private static final Logger LOGGER = LogManager.getLogger(PremainInstrumentor.class);

    /** The parsed agent configuration; supplies the debug flag and save path used by {@link #record}. */
    private final AgentArgs agentArgs;
    /** The scope filter deciding which classes are instrumented (built from {@code agentArgs}). */
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
     * <p>The steps are:
     *
     * <ol>
     *   <li>Copy the input buffer (the JVM requires that {@code classFileBuffer} itself is not
     *       mutated).
     *   <li>Ask {@link #matcher} whether the class is in scope; if not, return the (unchanged) copy.
     *   <li>Run {@link JmcIgnoreVisitor} to check for the {@code @JmcIgnoreInstrumentation} annotation;
     *       if present, return the (unchanged) copy.
     *   <li>Delegate the actual instrumentation to {@link JmcVisitor#transform}, which applies the full
     *       ordered visitor pipeline (sync pre-scan, enum/finalizer opt-outs, then the chained
     *       type-replacement and inline-instrumentation visitors).
     *   <li>If debug mode is enabled, persist the instrumented bytes via {@link #record}.
     * </ol>
     *
     * <p>A {@link JmcUnsupportedFeatureException} raised inside the pipeline is propagated unchanged;
     * any other exception is logged and rethrown as an {@link IllegalClassFormatException} naming the
     * offending class.
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
     * @return the transformed class file buffer, or the original if the class is out of scope or
     *     annotated to be ignored
     * @throws IllegalClassFormatException if instrumentation fails for a reason other than an
     *     unsupported feature
     * @throws JmcUnsupportedFeatureException if the class uses a concurrent feature JMC does not
     *     support
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

    /**
     * Writes an instrumented class to disk for debugging.
     *
     * <p>Called from {@link #transform} only when debug mode is enabled ({@link AgentArgs#isDebug()}).
     * The bytes are written to {@code <debugSavePath>/<className>.class} (see {@link
     * AgentArgs#getDebugSavePath()}), creating parent directories as needed. I/O failures are logged
     * and swallowed so that a debug-dump problem never aborts the run.
     *
     * @param className the internal (slash-separated) name of the class, used to build the output path
     * @param classFileBuffer the instrumented class bytes to write
     */
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
