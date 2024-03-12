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

    /*
     * The following method is used to check if @className is castable to Thread
     */
    public boolean isCastableToThread(String className) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className.replace("/", "."));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        while (clazz != null) {
            if (clazz.equals(Thread.class)) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }
}
