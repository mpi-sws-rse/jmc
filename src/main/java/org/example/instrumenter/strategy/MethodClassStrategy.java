package org.example.instrumenter.strategy;

import org.example.instrumenter.ModificationType;
import org.example.instrumenter.adapter.MethodAdapter;
import org.example.instrumenter.adapter.RuntimeEnvironmentAdapter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

public class MethodClassStrategy extends ClassStrategy{

    public String methodName = "main";
    public String methodDesc = "([Ljava/lang/String;)V";
    public ModificationType modificationType;

    public MethodClassStrategy(ClassWriter cw, ModificationType modificationType) {
        super(cw);
        modificationType = modificationType;
    }

    public MethodClassStrategy(ClassWriter cw, ModificationType modificationType, String methodName, String methodDesc) {
        super(cw);
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.modificationType = modificationType;
    }


    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        if (name.equals(methodName) && desc.equals(methodDesc)) {
            switch (modificationType) {
                case ADD_RUNTIME_ENVIRONMENT:
                    return new RuntimeEnvironmentAdapter(methodVisitor, 0);
            }
        }
        return methodVisitor;
    }



}
