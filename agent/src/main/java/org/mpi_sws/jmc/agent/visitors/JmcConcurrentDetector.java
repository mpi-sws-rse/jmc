package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.MethodVisitor;

import java.util.HashSet;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Per-method visitor that collects the {@code java.util.concurrent} calls a method makes.
 *
 * <p>It records each invoked {@code java/util/concurrent} member as a {@code fully.qualified.Owner.name}
 * feature key. It is used by {@link JmcConcurrentVisitor} to survey a class's concurrency usage.
 *
 * <p><strong>Note:</strong> this detector is auxiliary and is <em>not</em> part of the active {@link
 * JmcVisitor#transform} pipeline.
 */
public class JmcConcurrentDetector  extends MethodVisitor {
    /** Logger used to report the detected feature set. */
    private static final Logger LOGGER = LogManager.getLogger(JmcConcurrentDetector.class);
    /** Feature keys ({@code fully.qualified.Owner.name}) collected from the method's calls. */
    private final Set<String> detectedFeatures = new HashSet<>();
    /** Internal name of the enclosing class (recorded via the constructor but not otherwise used). */
    private final String className;
    /** Name of the method being scanned (recorded via the constructor but not otherwise used). */
    private final String methodName;

    /**
     * @param api the ASM API version
     * @param mv the downstream {@link MethodVisitor} to delegate to
     * @param className the internal name of the enclosing class
     * @param methodName the name of the method being scanned
     */
    public JmcConcurrentDetector(int api, MethodVisitor mv, String className, String methodName) {
        super(api, mv);
        this.className = className;
        this.methodName = methodName;
    }

    /**
     * Records any call whose owner is under {@code java/util/concurrent} as a feature key, then
     * forwards the call unchanged.
     *
     * @param opcode the invocation opcode
     * @param owner the internal name of the method's owner
     * @param name the method name
     * @param descriptor the method descriptor
     * @param isInterface whether the owner is an interface
     */
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface){
        if (owner.startsWith("java/util/concurrent")) {
            String feature = owner.replace("/", ".") + "." + name;
            detectedFeatures.add(feature);
        }

        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    /**
     * Returns the feature keys detected in the method (also logging them).
     *
     * @return the set of detected {@code java.util.concurrent} feature keys
     */
    public Set<String> getDetectedFeatures() {
        LOGGER.info("Detected features: " + detectedFeatures);
        return detectedFeatures;
    }

}
