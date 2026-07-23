package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class visitor that surveys a class's {@code java.util.concurrent} usage.
 *
 * <p>It attaches a {@link JmcConcurrentDetector} to each method to collect the concurrency features
 * used, then aggregates them and can report which ones fall outside a supplied "supported" set.
 *
 * <p><strong>Note:</strong> this visitor is auxiliary and is <em>not</em> part of the active {@link
 * JmcVisitor#transform} pipeline.
 */
public class JmcConcurrentVisitor extends ClassVisitor {
    /** Logger used to report unsupported features. */
    private static final Logger LOGGER = LogManager.getLogger(JmcConcurrentVisitor.class);
    /** Dotted name of the class being visited (captured in {@link #visit}). */
    private String className;
    /** One detector per visited method, aggregated by {@link #getAllDetectedFeatures}. */
    private final List<JmcConcurrentDetector> methodDetectors = new ArrayList<>();
    /** Unsupported feature keys found by the most recent {@link #usesUnsupportedFeatures} call. */
    private final Set<String> unsupported = new HashSet<>();

    /**
     * @param api the ASM API version
     * @param cv the downstream {@link ClassVisitor} to delegate to
     */
    public JmcConcurrentVisitor(int api, ClassVisitor cv) {
        super(api, cv);
    }

    /**
     * @param classVisitor the downstream {@link ClassVisitor} to delegate to (uses {@code ASM9})
     */
    public JmcConcurrentVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
    }

    /**
     * Captures the class name (converted to dotted form), then forwards the header.
     *
     * @param version the class file version
     * @param access the class access flags
     * @param name the internal name of the class
     * @param signature the generic signature, or {@code null}
     * @param superName the internal name of the superclass
     * @param interfaces the internal names of implemented interfaces
     */
    @Override
    public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces) {
        this.className = name.replace('/', '.');
        super.visit(version, access, name, signature, superName, interfaces);
    }

    /**
     * Attaches a {@link JmcConcurrentDetector} to the method (retaining it for later aggregation) so
     * its concurrency features are collected.
     *
     * @param access the method access flags
     * @param name the method name
     * @param desc the method descriptor
     * @param signature the generic signature, or {@code null}
     * @param exceptions the declared exceptions, or {@code null}
     * @return the detector wrapping the method
     */
    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        JmcConcurrentDetector detector =
                new JmcConcurrentDetector(Opcodes.ASM9, mv, className, name);
        methodDetectors.add(detector);
        return detector;
    }

    /**
     * Aggregates the concurrency feature keys detected across all methods of the class.
     *
     * @return the union of every method detector's feature set
     */
    public Set<String> getAllDetectedFeatures() {
        Set<String> all = new HashSet<>();
        for (JmcConcurrentDetector detector : methodDetectors) {
            all.addAll(detector.getDetectedFeatures());
        }
        return all;
    }

    /**
     * Reports whether the class uses any {@code java.util.concurrent} feature outside the given
     * supported set. Clears and repopulates {@link #unsupported}; returns as soon as the first
     * unsupported feature is found.
     *
     * @param supportedFeatures the set of feature keys considered supported
     * @return {@code true} if an unsupported {@code java.util.concurrent} feature is used
     */
    public boolean usesUnsupportedFeatures(Set<String> supportedFeatures) {
        unsupported.clear();
        for (String feature : getAllDetectedFeatures()) {
            if (feature.startsWith("java.util.concurrent")
                    && !supportedFeatures.contains(feature)) {
                unsupported.add(feature);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the unsupported features recorded by the most recent {@link #usesUnsupportedFeatures}
     * call (also logging them).
     *
     * @return the set of unsupported feature keys
     */
    public Set<String> getUnsupportedFeatures() {
        LOGGER.info("Unsupported feature {}", unsupported);
        return unsupported;
    }
}
