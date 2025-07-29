package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * JmcIgnoreVisitor is a ClassVisitor that checks for the presence of the JmcIgnoreInstrumentation
 * annotation on a class. If the annotation is present, it indicates that the class should not be
 * instrumented by JMC.
 */
public class JmcIgnoreVisitor extends ClassVisitor {

    private static final String IGNORE_ANNOTATION_DESCRIPTOR =
            "Lorg/mpisws/jmc/annotations/JmcIgnoreInstrumentation;";
    private boolean hasIgnoreAnnotation = false;

    public JmcIgnoreVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
    }

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
