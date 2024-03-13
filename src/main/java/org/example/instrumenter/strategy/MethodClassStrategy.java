package org.example.instrumenter.strategy;

import org.example.instrumenter.ModificationType;
import org.example.instrumenter.adapter.MethodAdapter;
import org.example.instrumenter.adapter.RuntimeEnvironmentAdapter;
import org.objectweb.asm.*;

public class MethodClassStrategy extends ClassStrategy{

    public String methodName = "main";
    public String methodDesc = "([Ljava/lang/String;)V";
    public ModificationType modificationType;
    public byte[] byteCode;
    private int nextVarIndex;

    public MethodClassStrategy(ClassWriter cw, ModificationType modificationType) {
        super(cw);
        modificationType = modificationType;
    }

    public MethodClassStrategy(ClassWriter cw, ModificationType modificationType, byte[] byteCode) {
        super(cw);
        modificationType = modificationType;
        byteCode = byteCode;
    }

    public MethodClassStrategy(ClassWriter cw, ModificationType modificationType, String methodName, String methodDesc) {
        super(cw);
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.modificationType = modificationType;
    }

    public MethodClassStrategy(ClassWriter cw, ModificationType modificationType, String methodName, String methodDesc, byte[] byteCode) {
        super(cw);
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.modificationType = modificationType;
        this.byteCode = byteCode;
    }


    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        if (name.equals(methodName) && desc.equals(methodDesc)) {
            switch (modificationType) {
                case ADD_RUNTIME_ENVIRONMENT:
                    return new RuntimeEnvironmentAdapter(methodVisitor, getNextVarIndex());
            }
        }
        return methodVisitor;
    }

    /*
     * Following method is used to get the index of the next free local variable
     * This method finds the maxLocals of the @methodName with @methodDescriptor and returns it
     * @byteCode : contains the bytecode of the compiled class
     * @nextVarIndex : contains the index of the next local variable
     */
    public int getNextVarIndex() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals(methodName) && descriptor.equals(methodDesc)) {
                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        @Override
                        public void visitMaxs(int maxStack, int maxLocals) {
                            nextVarIndex =  maxLocals ;
                        }
                    };
                }
                return methodVisitor;
            }
        };
        ClassReader cr = new ClassReader(byteCode);
        cr.accept(classVisitor, 0);
        return nextVarIndex;
    }



}
