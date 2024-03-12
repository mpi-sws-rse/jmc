package org.example.instrumenter.adapter;

import org.example.instrumenter.ModificationType;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MethodAdapter extends MethodVisitor {

    public MethodAdapter(MethodVisitor methodVisitor) {
        super(Opcodes.ASM9, methodVisitor);
    }

}
