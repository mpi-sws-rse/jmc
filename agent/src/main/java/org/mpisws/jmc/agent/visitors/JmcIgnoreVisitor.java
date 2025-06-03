package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class JmcIgnoreVisitor extends ClassVisitor {

    private static final String IGNORE_ANNOTATION_DESCRIPTOR = "Lorg/mpisws/jmc/annotations/JmcIgnoreInstrumentation;";
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

    public boolean hasIgnoreAnnotation() {
        return hasIgnoreAnnotation;
    }
}
