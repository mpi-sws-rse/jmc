package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Detects the opt-out annotation that excludes a class from JMC instrumentation.
 *
 * <p>{@link org.mpi_sws.jmc.agent.PremainInstrumentor#transform} runs this visitor first (with code, debug, and frames
 * skipped, since only annotations matter) and calls {@link #hasIgnoreAnnotation()}. When a class is
 * annotated with {@code @org.mpi_sws.jmc.annotations.JmcIgnoreInstrumentation}, the transformer returns
 * the original bytes unchanged, letting user code opt specific classes out of instrumentation.
 */
public class JmcIgnoreVisitor extends ClassVisitor {

    /** Type descriptor of the {@code @JmcIgnoreInstrumentation} annotation this visitor looks for. */
    private static final String IGNORE_ANNOTATION_DESCRIPTOR =
            "Lorg/mpi_sws/jmc/annotations/JmcIgnoreInstrumentation;";
    /** Set to {@code true} once the ignore annotation is found on the visited class. */
    private boolean hasIgnoreAnnotation = false;

    /**
     * @param classVisitor the downstream {@link ClassVisitor} to delegate to
     */
    public JmcIgnoreVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
    }

    /**
     * Flags the class when it carries the {@code @JmcIgnoreInstrumentation} annotation, then forwards
     * the annotation unchanged.
     *
     * @param descriptor the annotation's type descriptor
     * @param visible whether the annotation is visible at runtime
     * @return the delegate's {@link AnnotationVisitor}
     */
    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (descriptor.equals(IGNORE_ANNOTATION_DESCRIPTOR)) {
            hasIgnoreAnnotation = true;
        }
        return super.visitAnnotation(descriptor, visible);
    }

    /**
     * Checks if the class has the JmcIgnoreInstrumentation annotation.
     *
     * @return true if the class has the annotation, false otherwise
     */
    public boolean hasIgnoreAnnotation() {
        return hasIgnoreAnnotation;
    }
}
