package org.example.instrumenter;

import org.example.instrumenter.adapter.RuntimeEnvironmentAdapter;
import org.example.instrumenter.strategy.ClassStrategy;
import org.example.instrumenter.strategy.MethodClassStrategy;
import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ByteCodeModifier {

    /*
     * Following variable is used to store the index of the next local variable
     * It is used whenever a new local variable is needed inside a method
     */
    private int nextVarIndex;
    /*
     * Following map is used to store the bytecode of the compiled classes
     * The key of the map is the name of the class and the value is the bytecode of the class
     * These classes are the user program classes that are to be modified
     */
    public Map<String,byte[]> allByteCode;
    /*
     * Following variable is used to store the name of the main class of the user program
     * The main class is the class that contains the main method
     */
    private String mainClassName;
    /*
     * Following list is used in iterative analysis to find all the classes that contains methods which create threads
     */
    private List<String> threadClassCandidate = new ArrayList<>();
    /*
     * Following list is used in iterative analysis to find all the classes that contains methods which start threads
     */
    private List<String> threadStartCandidate = new ArrayList<>();
    /*
     * Following list is used iterative analysis to find all the classes that override the run method
     */
    private List<String> threadRunCandidate = new ArrayList<>();
    /*
     * Following list is used iterative analysis to find all the classes that contains methods which join threads
     */
    private List<String> threadJoinCandidate = new ArrayList<>();

    private ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

    /*
     * Following constructor is used to initialize the @allByteCode and @mainClassName variables
     * @allByteCode : contains the bytecode of the compiled classes
     * @mainClassName : contains the name of the main class of the user program
     */
    public ByteCodeModifier(Map<String,byte[]> allByteCode,String mainClassName){
        this.mainClassName = mainClassName;
        this.allByteCode = allByteCode;
    }

    public void applyModifications(ClassStrategy classStrategy, ClassWriter classWriter, byte[] byteCode, String className) {
        ClassReader cr = new ClassReader(byteCode);
        cr.accept(classStrategy, 0);
        byte[] modifiedByteCode = classWriter.toByteArray();
        allByteCode.put(className,modifiedByteCode);
    }

    /*
     * Following method is used to add the runtime environment to the main class
     * The runtime environment is added to the main class by adding the following instructions to the beginning of the main method
     * 1. RuntimeEnvironment.init(Thread.currentThread());
     * 2. SchedulerThread schedulerThread = new SchedulerThread();
     * 3. schedulerThread.setName("SchedulerThread");
     * 4. RuntimeEnvironment.initSchedulerThread(Thread.currentThread(),schedulerThread);
     * The runtime environment is finished by adding the following instructions to the end of the main method
     * 5. RuntimeEnvironment.finishThreadRequest(Thread.currentThread());
     */
    public void addRuntimeEnvironment() {
        byte[] byteCode = allByteCode.get(mainClassName);
        nextVarIndex = getNextVarIndex(byteCode, "main", "([Ljava/lang/String;)V");
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        // Create an instance of MethodClassStrategy and pass the adapter to it
        MethodClassStrategy strategy = new MethodClassStrategy(
                classWriter,
                ModificationType.ADD_RUNTIME_ENVIRONMENT,
                "main",
                "([Ljava/lang/String;)V"
        );

        // Use the strategy to visit the methods of the class and apply the modifications
        applyModifications(strategy, classWriter, byteCode, mainClassName);
    }

    /*
     * Following method is used to get the index of the next free local variable
     * This method finds the maxLocals of the @methodName with @methodDescriptor and returns it
     * @byteCode : contains the bytecode of the compiled class
     * @nextVarIndex : contains the index of the next local variable
     */
    private int getNextVarIndex(byte[] byteCode, String methodName, String methodDescriptor) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals(methodName) && descriptor.equals(methodDescriptor)) {
                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        @Override
                        public void visitMaxs(int maxStack, int maxLocals) {
                            //System.out.println("[Debugging Message] : Max Locals: " + maxLocals);
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

    /*
     * The following method is used to find all the points in the user program where threads are created
     * If such a point is found, the following instruction is added after the creation of the thread to the corresponding method
     * 1. RuntimeEnvironment.addThread(thread);
     * The method first starts by all methods of the main class and then iteratively analyzes all the classes that have a method which is called by one of the methods of the main class
     */

    public void modifyThreadCreation(){
        threadClassCandidate.add(mainClassName);
        while (!threadClassCandidate.isEmpty()){
            String newClassName = threadClassCandidate.remove(threadClassCandidate.size()-1);
            byte [] byteCode = allByteCode.get(newClassName);
            byte[] modifiedByteCode;

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        private boolean isNew = false;
                        private boolean isDup = false;
                        private boolean isInit = false;

                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.DUP && isNew) {
                                isDup = true;
                            } else if (isDup) {
                                // Nothing
                            } else {
                                resetFlags();
                            }
                            super.visitInsn(opcode);
                        }

                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.NEW && isCastableToThread(type)) {
                                resetFlags();
                                isNew = true;
                            } else {
                                resetFlags();
                            }
                            super.visitTypeInsn(opcode, type);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKESPECIAL && name.equals("<init>") && isDup) {
                                isInit = true;
                            } else {
                                resetFlags();
                            }
                            //String[] parts = owner.split("/");
                            String ownerClassName = owner.replace("/", ".");
                            if (!ownerClassName.equals(newClassName) && !threadClassCandidate.contains(ownerClassName) && allByteCode.containsKey(ownerClassName)) {
                                threadClassCandidate.add(ownerClassName);
                            }
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }

                        @Override
                        public void visitVarInsn(int opcode, int var) {
                            if (opcode == Opcodes.ASTORE && isInit) {
                                super.visitVarInsn(opcode, var);
                                mv.visitVarInsn(Opcodes.ALOAD, var);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "addThread", "(Ljava/lang/Thread;)V", false);
                                resetFlags();
                            } else if (isDup) {
                                super.visitVarInsn(opcode, var);
                            } else {
                                resetFlags();
                                super.visitVarInsn(opcode, var);
                            }

                        }

                        private void resetFlags() {
                            isNew = isDup = isInit = false;
                        }
                    };

                    return methodVisitor;
                }
            };
            ClassReader cr = new ClassReader(byteCode);
            cr.accept(classVisitor, 0);
            modifiedByteCode = cw.toByteArray();
            allByteCode.put(newClassName,modifiedByteCode);
        }
    }

    /*
     * The following method is used to find all the points in the user program where threads call the join method
     * If such a point is found, the following instruction is added before the join of the thread to the corresponding method
     * 1. RuntimeEnvironment.threadJoin(Thread.currentThread(),thread);
     * The method first starts by all methods of the main class and then iteratively analyzes all the classes that have a method which is called by one of the methods of the main class
     */
    public void modifyThreadJoin(){
        threadJoinCandidate.add(mainClassName);
        while (!threadJoinCandidate.isEmpty()) {
            String newClassName = threadJoinCandidate.remove(threadJoinCandidate.size() - 1);
            byte[] byteCode = allByteCode.get(newClassName);
            byte[] modifiedByteCode;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        private boolean isALOAD = false;
                        private int varThread = -1;

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKEVIRTUAL && name.equals("join") && isALOAD && isCastableToThread(owner)) {
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                                mv.visitVarInsn(Opcodes.ALOAD, varThread);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "threadJoin", "(Ljava/lang/Thread;Ljava/lang/Thread;)V", false);
                                resetFlags();
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "waitRequest", "(Ljava/lang/Thread;)V", false);
                            } else {
                                String ownerClassName = owner.replace("/", ".");
                                if (!ownerClassName.equals(newClassName) && !threadJoinCandidate.contains(ownerClassName) && allByteCode.containsKey(ownerClassName)) {
                                    threadJoinCandidate.add(ownerClassName);
                                }
                                resetFlags();
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                            }
                        }

                        @Override
                        public void visitVarInsn(int opcode, int var) {
                            if (opcode == Opcodes.ALOAD) {
                                resetFlags();
                                isALOAD = true;
                                varThread = var;
                                super.visitVarInsn(opcode, var);
                            } else {
                                resetFlags();
                                super.visitVarInsn(opcode, var);
                            }

                        }

                        private void resetFlags() {
                            isALOAD = false;
                            varThread = -1;
                        }
                    };
                    return methodVisitor;
                }
            };
            ClassReader cr = new ClassReader(byteCode);
            cr.accept(classVisitor, 0);
            modifiedByteCode = cw.toByteArray();
            allByteCode.put(newClassName,modifiedByteCode);
        }
    }

    /*
     * The following method is used to find all the points in the user program where threads are started
     * If such a point is found, the following instruction is added before the start of the thread to the corresponding method
     * 1. RuntimeEnvironment.threadStart(Thread.currentThread(),thread);
     * The method first starts by all methods of the main class and then iteratively analyzes all the classes that have a method which is called by one of the methods of the main class
     * Moreover, the method also adds the class of the thread that is started to the @threadRunCandidate list
     * It will be used by the @threadRunCandidate method to find all the classes that override the run method
     */
    public void modifyThreadStart(){
        threadStartCandidate.add(mainClassName);
        while (!threadStartCandidate.isEmpty()) {
            String newClassName = threadStartCandidate.remove(threadStartCandidate.size() - 1);
            byte[] byteCode = allByteCode.get(newClassName);
            byte[] modifiedByteCode;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        private boolean isALOAD = false;
                        private int varThread = -1;

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKEVIRTUAL && name.equals("start") && isALOAD && isCastableToThread(owner)) {
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "threadStart", "(Ljava/lang/Thread;Ljava/lang/Thread;)V", false);
                                resetFlags();
                            } else {
                                String ownerClassName = owner.replace("/", ".");
                                if (!ownerClassName.equals(newClassName) && !threadStartCandidate.contains(ownerClassName) && allByteCode.containsKey(ownerClassName)) {
                                    threadStartCandidate.add(ownerClassName);
                                }
                                resetFlags();
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                            }
                        }

                        @Override
                        public void visitVarInsn(int opcode, int var) {
                            if (opcode == Opcodes.ALOAD) {
                                resetFlags();
                                isALOAD = true;
                                varThread = var;
                                super.visitVarInsn(opcode, var);
                            } else {
                                resetFlags();
                                super.visitVarInsn(opcode, var);
                            }

                        }

                        private void resetFlags() {
                            isALOAD = false;
                            varThread = -1;
                        }
                    };
                    return methodVisitor;
                }
            };
            ClassReader cr = new ClassReader(byteCode);
            cr.accept(classVisitor, 0);
            modifiedByteCode = cw.toByteArray();
            allByteCode.put(newClassName,modifiedByteCode);
        }
    }

    /*
     * The following method is used to find all the points in the user program where fields are read and written
     * If such a point is found, the following instruction is added before it to the corresponding method
     * 1. RuntimeEnvironment.newReadOperation(Object,Thread,String,String,String);
     * 2. RuntimeEnvironment.newWriteOperation(Object,Object,Thread,String,String,String);
     * The method first starts by all methods of the main class and then iteratively analyzes all the classes that have a method which is called by one of the methods of the main class
     */
    public void modifyReadWriteOperation(){
        for (String newClass : allByteCode.keySet()) {
            byte[] byteCode = allByteCode.get(newClass);
            byte[] modifiedByteCode;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        /*
                         * The following block of code is used to detect the GETFIELD and PUTFIELD instructions
                         * And to inform the RuntimeEnvironment about the read and write operations
                         */
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            if (isPrimitiveType(descriptor) && (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD)) {
                                // If this instruction is a GETFIELD instruction
                                if (opcode == Opcodes.GETFIELD) {
                                    // Duplicate the top operand stack value which should be the value of the field
                                    mv.visitInsn(Opcodes.DUP);
                                    //mv.visitFieldInsn(opcode, owner, name, descriptor);
                                    // Load the current thread onto the operand stack
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                                    // Load the owner of the field onto the operand stack
                                    mv.visitLdcInsn(owner.replace("/", "."));
                                    // Load the name of the field onto the operand stack
                                    mv.visitLdcInsn(name);
                                    // Load the descriptor of the field onto the operand stack
                                    mv.visitLdcInsn(descriptor);
                                    System.out.println("Owner: " + owner + " Name: " + name + " Descriptor: " + descriptor);
                                    // Invoke the RuntimeEnvironment.newReadOperation method
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "readOperation", "(Ljava/lang/Object;Ljava/lang/Thread;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
                                } else if (opcode == Opcodes.PUTFIELD) {
                                    if (descriptor.equals("J") || descriptor.equals("D")) {
                                        mv.visitInsn(Opcodes.DUP2_X1);
                                    } else {
                                        mv.visitInsn(Opcodes.DUP2);
                                    }
                                    switch (descriptor) {
                                        case "I":
                                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                                            break;
                                        case "J":
                                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                                            break;
                                        case "F":
                                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                                            break;
                                        case "D":
                                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                                            break;
                                        case "Z":
                                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                                            break;
                                        case "C":
                                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                                            break;
                                        case "B":
                                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                                            break;
                                        case "S":
                                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                                            break;
                                    }
                                    if (descriptor.equals("J") || descriptor.equals("D")) {
                                        mv.visitInsn(Opcodes.SWAP);
                                        mv.visitInsn(Opcodes.DUP_X1);
                                        mv.visitInsn(Opcodes.SWAP);
                                    }
                                    // Load the current thread onto the operand stack
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                                    // Load the owner of the field onto the operand stack
                                    mv.visitLdcInsn(owner.replace("/", "."));
                                    // Load the name of the field onto the operand stack
                                    mv.visitLdcInsn(name);
                                    // Load the descriptor of the field onto the operand stack
                                    mv.visitLdcInsn(descriptor);
                                    // Invoke the RuntimeEnvironment.newReadOperation method
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "writeOperation", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Thread;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
                                    if (descriptor.equals("J") || descriptor.equals("D")) {
                                        mv.visitInsn(Opcodes.DUP_X2);
                                        mv.visitInsn(Opcodes.POP);
                                    }
                                }
                                super.visitFieldInsn(opcode, owner, name, descriptor);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "waitRequest", "(Ljava/lang/Thread;)V", false);
                                mv.visitInsn(Opcodes.NOP);
                            }else {
                                super.visitFieldInsn(opcode, owner, name, descriptor);
                            }
                        }
                    };
                    return methodVisitor;
                }
            };
            ClassReader cr = new ClassReader(byteCode);
            cr.accept(classVisitor, 0);
            modifiedByteCode = cw.toByteArray();
            allByteCode.put(newClass,modifiedByteCode);
        }
    }

    public boolean isPrimitiveType(String type) {
        return type.equals("I") || type.equals("J") || type.equals("F") || type.equals("D") || type.equals("Z") || type.equals("C") || type.equals("B") || type.equals("S");
    }

    /*
     * The following method is used to find all the overridden run methods in the user program
     * If such a method is found, the following instruction is added to the beginning of the method
     * 1. RuntimeEnvironment.waitRequest(Thread.currentThread());
     * Moreover, the following instruction is added to the end of the method
     * 1. RuntimeEnvironment.finishThreadRequest(Thread.currentThread());
     * The method first starts by the @threadRunCandidate list. For each class in the list, it finds the run method and adds the instructions to the beginning and the end of the method
     * If the run method is not found, the method adds the super class of the class to the @threadRunCandidate list
     */

    public void modifyThreadRun(){
        for (String className : allByteCode.keySet()) {
            if (isCastableToThread(className)){
                byte[] byteCode = allByteCode.get(className);
                byte[] modifiedByteCode;
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);

                        // If this class is a subclass of Thread and this method is the run method
                        if (name.equals("run") && descriptor.equals("()V")) {

                            // Return a new MethodVisitor that analyzes the bytecode
                            methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                                @Override
                                public void visitCode() {
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "waitRequest", "(Ljava/lang/Thread;)V", false);
                                    super.visitCode();
                                }

                                @Override
                                public void visitInsn(int opcode) {
                                    if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) || opcode == Opcodes.ATHROW) {
                                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "finishThreadRequest", "(Ljava/lang/Thread;)V", false);
                                    }
                                    super.visitInsn(opcode);
                                }
                            };
                        }
                        return methodVisitor;
                    }
                };
                ClassReader cr = new ClassReader(byteCode);
                cr.accept(classVisitor, 0);
                modifiedByteCode = cw.toByteArray();
                allByteCode.put(className,modifiedByteCode);
            }
        }
    }

    /*
     * The following method is used to find all the points in the user program where represent throwing an exception due to an assert statement failure.
     * If such a point is found, the following instruction is added instead of the call to the AssertionError constructor.
     * 1. RuntimeEnvironment.assertOperation(String);
     * Moreover, the following instruction is replaced with the throw instruction
     * 2. RETURN
     * The method first starts by all methods of the main class and then iteratively analyzes all the classes that have a method which is called by one of the methods of the main class
     */
    public void modifyAssert(){
        for (String className : allByteCode.keySet()) {
            byte[] byteCode = allByteCode.get(className);
            byte[] modifiedByteCode;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                        boolean replaced = false;
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKESPECIAL && owner.equals("java/lang/AssertionError") && name.equals("<init>")) {
                                // Replace the assert statement
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "assertOperation", "(Ljava/lang/String;)V", false);
                                replaced = true;
                            } else {
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                            }
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.ATHROW && replaced) {
                                replaced = false;
                                mv.visitInsn(Opcodes.RETURN);
                            } else {
                                super.visitInsn(opcode);
                            }
                        }

                    };

                    return methodVisitor;
                }
            };
            ClassReader cr = new ClassReader(byteCode);
            cr.accept(classVisitor, 0);
            modifiedByteCode = cw.toByteArray();
            allByteCode.put(className,modifiedByteCode);
        }
    }

    /*
     * The following method is used to check if @className is castable to Thread
     */
    private boolean isCastableToThread(String className) {
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

    /*
     * The following method is used to find all the points in the user program where monitors are entered and exited
     * If a monitor enter point is found, the following instruction is added before it to the corresponding method
     * 1. RuntimeEnvironment.enterMonitor(Object,Thread);
     * Also, the following instruction is added after it to the corresponding method
     * 2. RuntimeEnvironment.acquiredLock(Object,Thread);
     * If a monitor exit point is found, the following instruction is added before it to the corresponding method
     * 3. RuntimeEnvironment.exitMonitor(Object,Thread);
     * Also, the following instruction is added after it to the corresponding method
     * 4. RuntimeEnvironment.releasedLock(Object,Thread);
     * The method checks all methods of all classes in the @allByteCode map and adds the instructions to the corresponding methods
     */
    public void modifyMonitorInstructions() {
        for (Map.Entry<String, byte[]> entry : allByteCode.entrySet()) {
            byte[] byteCode = entry.getValue();
            byte[] modifiedByteCode;

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            ClassReader cr = new ClassReader(byteCode);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        private boolean isASTORE = false;
                        private boolean isALOAD = false;
                        private boolean foundMonitorEnter = false;
                        private boolean foundMonitorExit = false;
                        private int var;

                        private int oldvar;
                        @Override
                        public void visitVarInsn(int opcode, int var) {
                            if (opcode == Opcodes.ASTORE) {
                                isASTORE = true;
                                this.var = var;
                            } else if (opcode == Opcodes.ALOAD) {
                                isALOAD = true;
                                this.var = var;
                            }
                            super.visitVarInsn(opcode, var);
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.MONITORENTER && isASTORE) {
                                mv.visitVarInsn(Opcodes.ALOAD, var);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "enterMonitor", "(Ljava/lang/Object;Ljava/lang/Thread;)V", false);
                                super.visitInsn(opcode);
                                isASTORE = false;
                                foundMonitorEnter = true;
                            } else if (opcode == Opcodes.MONITOREXIT && isALOAD) {
                                mv.visitVarInsn(Opcodes.ALOAD, var);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "exitMonitor", "(Ljava/lang/Object;Ljava/lang/Thread;)V", false);
                                super.visitInsn(opcode);
                                isALOAD = false;
                                foundMonitorExit = true;
                                oldvar = var;
                            } else {
                                super.visitInsn(opcode);
                            }
                        }

                        @Override
                        public void visitLineNumber(int line, Label start) {
                            super.visitLineNumber(line, start);
                            if (foundMonitorEnter) {
                                foundMonitorEnter = false;
                                mv.visitVarInsn(Opcodes.ALOAD, var);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "acquiredLock", "(Ljava/lang/Object;Ljava/lang/Thread;)V", false);
                            } else if (foundMonitorExit) {
                                foundMonitorExit = false;
                                mv.visitVarInsn(Opcodes.ALOAD, oldvar);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/RuntimeEnvironment", "releasedLock", "(Ljava/lang/Object;Ljava/lang/Thread;)V", false);
                            }
                        }
                    };
                    //
                }
            };
            cr.accept(cv, 0);
            modifiedByteCode = cw.toByteArray();
            allByteCode.put(entry.getKey(), modifiedByteCode);
        }
    }
}