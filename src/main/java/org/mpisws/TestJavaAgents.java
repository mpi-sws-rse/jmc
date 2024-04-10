package org.mpisws;

import org.objectweb.asm.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class TestJavaAgents {

    /*
     * Just forget about this class right now. We will come back to this later.
     */

    public static void main(String []args){
        System.out.println("Section 1");

        // Define the ClassFileTransformer
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                if (className.equals("Test")) {
                    ClassReader cr = new ClassReader(classfileBuffer);
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    ClassVisitor cv = new ClassVisitor(Opcodes.ASM7, cw) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                            if (name.equals("main")) {
                                return new MethodVisitor(Opcodes.ASM7, mv) {
                                    @Override
                                    public void visitInsn(int opcode) {
                                        if (opcode == Opcodes.RETURN) {
                                            // Insert bytecode instructions to print "Section 2" before return
                                            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                                            mv.visitLdcInsn("Section 2");
                                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                                        }
                                        super.visitInsn(opcode);
                                    }
                                };
                            }
                            return mv;
                        }
                    };
                    cr.accept(cv, ClassReader.EXPAND_FRAMES);
                    return cw.toByteArray();
                }
                return classfileBuffer;
            }
        };

        // Apply the ClassFileTransformer using instrumentation
        //TODO() : write the code to apply the ClassFileTransformer using instrumentation
    }
}
