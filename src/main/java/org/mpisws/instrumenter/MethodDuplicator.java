package org.mpisws.instrumenter;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MethodDuplicator extends MethodVisitor {

    private final MethodVisitor newMethodVisitor;

    public MethodDuplicator(MethodVisitor mv, MethodVisitor newMv) {
        super(Opcodes.ASM9, mv);
        this.newMethodVisitor = newMv;
    }

    @Override
    public void visitCode() {
        super.visitCode();
        newMethodVisitor.visitCode();
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        newMethodVisitor.visitEnd();
    }
}
