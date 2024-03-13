package org.example.instrumenter.strategy;

import org.example.instrumenter.ModificationType;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public abstract class ClassStrategy extends ClassVisitor {

    public ModificationType modificationType;

    public ClassStrategy(ClassWriter cw) {
        super(Opcodes.ASM9, cw);
    }
}
