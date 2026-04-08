package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class JmcIgnoreEnumVisitor extends ClassVisitor {

    private boolean isEnum;
    private String className;

    public JmcIgnoreEnumVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
    }

    public boolean isEnum() {
        return isEnum;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        this.isEnum = (access & Opcodes.ACC_ENUM) != 0;
//        if (this.isEnum) {
//            System.out.println("JmcIgnoreEnumVisitor ignored the class : " + className);
//        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

}
