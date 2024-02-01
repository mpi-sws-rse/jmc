package org.example.Instrumentor;

import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ByteCodeModifier {

    /*
     * Following variable is used to store the index of the next local variable
     */
    private int nextVarIndex;

    public Map<String,byte[]> allByteCode;

    private String mainClassName;

    private List<String> findAllThreadsClasses = new ArrayList<>();

    private List<String> findAllThreadStartClasses = new ArrayList<>();

    private List<String> findAllThreadsRun = new ArrayList<>();

    public ByteCodeModifier(Map<String,byte[]> allByteCode,String mainClassName){
        this.mainClassName = mainClassName;
        this.allByteCode = allByteCode;
    }

    public void addScheduler() {
        byte[] byteCode = allByteCode.get(mainClassName);
        byte[] modifiedByteCode;
        //getSchedulerVarIndex(byteCode);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        /*
         * Following section defines an adapterVisitor which inserts proper instructions
         * At the beginning of the main method for initializing the Scheduler class
         */
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("main")) {
                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        @Override
                        public void visitCode() {
                            /*
                             * Following section inserts the instruction "Scheduler scheduler = new Scheduler();" at the beginning of the main method
                             * However, the instruction is commented out because the Scheduler class is static
                             * and the scheduler object is not used anywhere in the program
                             * Thus, the instruction is not needed and should be removed
                             */
                            //mv.visitTypeInsn(Opcodes.NEW, "org/example/runtime/Scheduler");
                            //mv.visitInsn(Opcodes.DUP);
                            //mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/example/runtime/Scheduler", "<init>", "()V", false);
                            //mv.visitVarInsn(Opcodes.ASTORE, nextVarIndex); // Store the object in the new local variable
                            //mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);
                            //mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                            //mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/example/runtime/Scheduler", "addThread", "(Ljava/lang/Thread;)V", false);

                            /*
                             * Insert "Scheduler.init();" at the beginning of the main method
                             */
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/Scheduler", "init", "()V", false);
                            /*
                             * Insert "Scheduler.addThread(Thread.currentThread());" following the instruction "Scheduler.init();"
                             */
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/Scheduler", "addThread", "(Ljava/lang/Thread;)V", false);
                            super.visitCode();
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                // Call the runMC method
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/Scheduler", "runMC", "()V", false);
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
        allByteCode.put(mainClassName,modifiedByteCode);
    }

    /*
     * Following method is used to get the index of the next free local variable
     * This method finds the maxLocals of the main method and returns it
     * @byteCode : contains the bytecode of the compiled class
     * @nextVarIndex : contains the index of the next local variable
     */
    private int getNextVarIndex(byte[] byteCode) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("main")) {
                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        @Override
                        public void visitMaxs(int maxStack, int maxLocals) {
                            System.out.println("[Debugging Message] : Max Locals: " + maxLocals);
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

    public void findAllThreads(){
        byte [] byteCode = allByteCode.get(mainClassName);
        byte[] modifiedByteCode;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

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
                        System.out.println("[Debugging Message-main Class] : the owner:"+ownerClassName+" with opcode"+ opcode +"is added to the findAllThreadStartClasses");
                        System.out.println("[Debugging Message-main Class] : ownerClassName.equals(mainClassName) = "+ownerClassName.equals(mainClassName)+" findAllThreadsClasses.contains(ownerClassName) ="+findAllThreadsClasses.contains(ownerClassName)+" allByteCode.containsKey(ownerClassName) ="+allByteCode.containsKey(ownerClassName));
                        if (!ownerClassName.equals(mainClassName) && !findAllThreadsClasses.contains(ownerClassName) && allByteCode.containsKey(ownerClassName)) {
                            System.out.println("[Debugging Message-main Class] : We are inside the if statement");
                            findAllThreadsClasses.add(ownerClassName);
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }

                    @Override
                    public void visitVarInsn(int opcode, int var) {
                        if (opcode == Opcodes.ASTORE && isInit) {
                            super.visitVarInsn(opcode, var);
                            //mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);
                            //mv.visitVarInsn(Opcodes.ALOAD, var);
                            //mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/example/runtime/Scheduler", "addThread", "(Ljava/lang/Thread;)V", false);
                            mv.visitVarInsn(Opcodes.ALOAD, var);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/Scheduler", "addThread", "(Ljava/lang/Thread;)V", false);
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
        allByteCode.put(mainClassName,modifiedByteCode);
        while (!findAllThreadsClasses.isEmpty()){
            System.out.println("[Debugging Message-main Class] : We are inside the while loop");
            continueFindAllThreads(findAllThreadsClasses.remove(findAllThreadsClasses.size()-1));
        }
    }

    private void continueFindAllThreads(String newClassName){
        System.out.println("[Debugging Message-main Class] : Our newClassName = "+newClassName);
        byte [] byteCode = allByteCode.get(newClassName);
        byte[] modifiedByteCode;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

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
                        if (!ownerClassName.equals(newClassName) && !findAllThreadsClasses.contains(ownerClassName) && allByteCode.containsKey(ownerClassName)) {
                            findAllThreadsClasses.add(ownerClassName);
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }

                    @Override
                    public void visitVarInsn(int opcode, int var) {
                        if (opcode == Opcodes.ASTORE && isInit) {
                            super.visitVarInsn(opcode, var);
                            //mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);
                            //mv.visitVarInsn(Opcodes.ALOAD, var);
                            //mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/example/runtime/Scheduler", "addThread", "(Ljava/lang/Thread;)V", false);
                            mv.visitVarInsn(Opcodes.ALOAD, var);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/Scheduler", "addThread", "(Ljava/lang/Thread;)V", false);
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

    public void findAllStartThread(){
        byte [] byteCode = allByteCode.get(mainClassName);
        byte[] modifiedByteCode;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
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
                            //mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);
                            //mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                            //mv.visitVarInsn(Opcodes.ALOAD, varThread);
                            //mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/example/runtime/Scheduler", "threadStart", "(Ljava/lang/Thread;Ljava/lang/Thread;)V", false);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                            mv.visitVarInsn(Opcodes.ALOAD, varThread);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/Scheduler", "threadStart", "(Ljava/lang/Thread;Ljava/lang/Thread;)V", false);
                            resetFlags();
                            //String[] parts = owner.split("/");
                            String ownerClassName = owner.replace("/", ".");
                            if (!findAllThreadsRun.contains(ownerClassName) && allByteCode.containsKey(ownerClassName)) {
                                findAllThreadsRun.add(ownerClassName);
                            }
                        } else {
                            //String[] parts = owner.split("/");
                            //String ownerClassName = parts[parts.length - 1];
                            String ownerClassName = owner.replace("/", ".");
                            if (!ownerClassName.equals(mainClassName) && !findAllThreadStartClasses.contains(ownerClassName) && allByteCode.containsKey(ownerClassName)) {
                                findAllThreadStartClasses.add(ownerClassName);
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
        allByteCode.put(mainClassName,modifiedByteCode);
        while (!findAllThreadStartClasses.isEmpty()){
            continuefindAllStartThread(findAllThreadStartClasses.remove(findAllThreadStartClasses.size()-1));
        }
    }

    private void continuefindAllStartThread(String newClassName){
        byte [] byteCode = allByteCode.get(newClassName);
        byte[] modifiedByteCode;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
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
                            //mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);
                            //mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                            //mv.visitVarInsn(Opcodes.ALOAD, varThread);
                            //mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/example/runtime/Scheduler", "threadStart", "(Ljava/lang/Thread;Ljava/lang/Thread;)V", false);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                            mv.visitVarInsn(Opcodes.ALOAD, varThread);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/Scheduler", "threadStart", "(Ljava/lang/Thread;Ljava/lang/Thread;)V", false);
                            resetFlags();
                            //String[] parts = owner.split("/");
                            String ownerClassName = owner.replace("/", ".");
                            if (!findAllThreadsRun.contains(ownerClassName) && allByteCode.containsKey(ownerClassName)) {
                                findAllThreadsRun.add(ownerClassName);
                            }
                        } else {
                            //String[] parts = owner.split("/");
                            String ownerClassName = owner.replace("/", ".");
                            if (!ownerClassName.equals(newClassName) && !findAllThreadStartClasses.contains(ownerClassName) && allByteCode.containsKey(ownerClassName)) {
                                findAllThreadStartClasses.add(ownerClassName);
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

    public void preRun(){
        while (!findAllThreadsRun.isEmpty()){
            System.out.println("[Debugging Message] : The runnable class is : "+findAllThreadsRun.remove(findAllThreadsRun.size()-1));
        }
    }

    public void findAllThreadsRun(){
        while (!findAllThreadsRun.isEmpty()){
            String newClass = findAllThreadsRun.remove(findAllThreadsRun.size()-1);
            byte [] byteCode = allByteCode.get(newClass);
            byte[] modifiedByteCode;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {

                private boolean noRunFound = true;
                private String className;
                private String superName;

                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    className = name;
                    this.superName = superName;
                    super.visit(version, access, name, signature, superName, interfaces);
                }
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);

                    // If this class is a subclass of Thread and this method is the run method
                    if (name.equals("run") && descriptor.equals("()V")) {
                        noRunFound = false;
                        // Return a new MethodVisitor that analyzes the bytecode
                        methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                            @Override
                            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                                // If this instruction is a GETFIELD instruction
                                if (opcode == Opcodes.GETFIELD) {
                                    // Duplicate the top operand stack value which should be the value of the field
                                    mv.visitInsn(Opcodes.DUP);
                                    mv.visitFieldInsn(opcode, owner, name, descriptor);
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
                                    // Load the current thread onto the operand stack
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                                    // Load the owner of the field onto the operand stack
                                    mv.visitLdcInsn(owner.replace("/", "."));
                                    // Load the name of the field onto the operand stack
                                    mv.visitLdcInsn(name);
                                    // Load the descriptor of the field onto the operand stack
                                    mv.visitLdcInsn(descriptor);
                                    // Invoke the Scheduler.newReadOperation method
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/Scheduler", "newReadOperation", "(Ljava/lang/Object;Ljava/lang/Thread;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
                                }
                                else if (opcode == Opcodes.PUTFIELD) {
                                    // Log the instruction
                                    System.out.println("[Debugging Message] : "+newClass + "." + name + " " + (opcode == Opcodes.PUTFIELD ? "PUTFIELD" : "GETFIELD"));
                                }
                                super.visitFieldInsn(opcode, owner, name, descriptor);
                            }
                        };
                    }
                    return methodVisitor;
                }

                @Override
                public void visitEnd() {
                    if (noRunFound) {
                        if (superName != null && !superName.equals("java/lang/Object") && allByteCode.containsKey(superName.replace("/", "."))) {
                            findAllThreadsRun.add(superName.replace("/", "."));
                        }
                        super.visitEnd();
                    }else {
                        super.visitEnd();
                    }
                }
            };
            ClassReader cr = new ClassReader(byteCode);
            cr.accept(classVisitor, 0);
            modifiedByteCode = cw.toByteArray();
            allByteCode.put(newClass,modifiedByteCode);
        }
    }

    public void findAllThreadsRun2(){
        while (!findAllThreadsRun.isEmpty()){
            String newClass = findAllThreadsRun.remove(findAllThreadsRun.size()-1);
            byte [] byteCode = allByteCode.get(newClass);
            byte[] modifiedByteCode;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {

                private boolean noRunFound = true;
                private String className;
                private String superName;

                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    className = name;
                    this.superName = superName;
                    super.visit(version, access, name, signature, superName, interfaces);
                }
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);

                    // If this class is a subclass of Thread and this method is the run method
                    if (name.equals("run") && descriptor.equals("()V")) {
                        noRunFound = false;
                        // Return a new MethodVisitor that analyzes the bytecode
                        methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                            @Override
                            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
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
                                    // Invoke the Scheduler.newReadOperation method
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/Scheduler", "oldReadOperation", "(Ljava/lang/Object;Ljava/lang/Thread;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
                                }
                                else if (opcode == Opcodes.PUTFIELD) {
                                    mv.visitInsn(Opcodes.DUP2);
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
                                    // Load the current thread onto the operand stack
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                                    // Load the owner of the field onto the operand stack
                                    mv.visitLdcInsn(owner.replace("/", "."));
                                    // Load the name of the field onto the operand stack
                                    mv.visitLdcInsn(name);
                                    // Load the descriptor of the field onto the operand stack
                                    mv.visitLdcInsn(descriptor);
                                    // Invoke the Scheduler.newReadOperation method
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/Scheduler", "newWriteOperation", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Thread;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
                                }
                                super.visitFieldInsn(opcode, owner, name, descriptor);
                            }
                        };
                    }
                    return methodVisitor;
                }

                @Override
                public void visitEnd() {
                    if (noRunFound) {
                        if (superName != null && !superName.equals("java/lang/Object") && allByteCode.containsKey(superName.replace("/", "."))) {
                            findAllThreadsRun.add(superName.replace("/", "."));
                        }
                        super.visitEnd();
                    }else {
                        super.visitEnd();
                    }
                }
            };
            ClassReader cr = new ClassReader(byteCode);
            cr.accept(classVisitor, 0);
            modifiedByteCode = cw.toByteArray();
            allByteCode.put(newClass,modifiedByteCode);
        }
    }

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
     * TODO(): The following method is deprecated and must be removed
     */
//    public byte[] addScheduler(byte[] byteCode) {
//        byte[] modifiedByteCode;
//        //getSchedulerVarIndex(byteCode);
//        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
//
//        /*
//         * Following section defines an adapterVisitor which inserts proper instructions
//         * At the beginning of the main method for initializing the Scheduler class
//         */
//        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
//            @Override
//            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
//                MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
//                if (name.equals("main")) {
//                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
//                        @Override
//                        public void visitCode() {
//                            /*
//                             * Following section inserts the instruction "Scheduler scheduler = new Scheduler();" at the beginning of the main method
//                             * However, the instruction is commented out because the Scheduler class is static
//                             * and the scheduler object is not used anywhere in the program
//                             * Thus, the instruction is not needed and should be removed
//                             */
//                            //mv.visitTypeInsn(Opcodes.NEW, "org/example/runtime/Scheduler");
//                            //mv.visitInsn(Opcodes.DUP);
//                            //mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/example/runtime/Scheduler", "<init>", "()V", false);
//                            //mv.visitVarInsn(Opcodes.ASTORE, nextVarIndex); // Store the object in the new local variable
//                            //mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);
//                            //mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
//                            //mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/example/runtime/Scheduler", "addThread", "(Ljava/lang/Thread;)V", false);
//
//                            /*
//                             * Insert "Scheduler.init();" at the beginning of the main method
//                             */
//                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/Scheduler", "init", "()V", false);
//                            /*
//                             * Insert "Scheduler.addThread(Thread.currentThread());" following the instruction "Scheduler.init();"
//                             */
//                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
//                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/Scheduler", "addThread", "(Ljava/lang/Thread;)V", false);
//                            super.visitCode();
//                        }
//                    };
//                }
//                return methodVisitor;
//            }
//        };
//        ClassReader cr = new ClassReader(byteCode);
//        cr.accept(classVisitor, 0);
//        modifiedByteCode = cw.toByteArray();
//        return modifiedByteCode;
//    }

        /*
        * TODO(): The following method is deprecated and must be removed
        */
//        public byte [] findAllThreads(byte [] byteCode){
//            byte[] modifiedByteCode;
//            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
//
//            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
//                @Override
//                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
//                    MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
//                    if (name.equals("main")) {
//                        methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
//                            private boolean isNew = false;
//                            private boolean isDup = false;
//                            private boolean isInit = false;
//                            @Override
//                            public void visitInsn(int opcode) {
//                                if (opcode == Opcodes.DUP && isNew) {
//                                    isDup = true;
//                                } else if (isDup) {
//                                    // Nothing
//                                } else {
//                                    resetFlags();
//                                }
//                                super.visitInsn(opcode);
//                            }
//
//                            @Override
//                            public void visitTypeInsn(int opcode, String type) {
//                                if (opcode == Opcodes.NEW && isCastableToThread(type)) {
//                                    resetFlags();
//                                    isNew = true;
//                                } else {
//                                    resetFlags();
//                                }
//                                super.visitTypeInsn(opcode, type);
//                            }
//
//                            @Override
//                            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
//                                if (opcode == Opcodes.INVOKESPECIAL && name.equals("<init>") && isDup) {
//                                    isInit = true;
//                                } else {
//                                    resetFlags();
//                                }
//                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
//                            }
//
//                            @Override
//                            public void visitVarInsn(int opcode, int var) {
//                                if (opcode == Opcodes.ASTORE && isInit) {
//                                    super.visitVarInsn(opcode, var);
//                                    //mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);
//                                    //mv.visitVarInsn(Opcodes.ALOAD, var);
//                                    //mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/example/runtime/Scheduler", "addThread", "(Ljava/lang/Thread;)V", false);
//                                    mv.visitVarInsn(Opcodes.ALOAD, var);
//                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/Scheduler", "addThread", "(Ljava/lang/Thread;)V", false);
//                                    resetFlags();
//                                } else if (isDup){
//                                    super.visitVarInsn(opcode, var);
//                                } else {
//                                    resetFlags();
//                                    super.visitVarInsn(opcode, var);
//                                }
//
//                            }
//
//                            private void resetFlags() {
//                                isNew = isDup = isInit = false;
//                            }
//                        };
//                    }
//                    return methodVisitor;
//                }
//            };
//            ClassReader cr = new ClassReader(byteCode);
//            cr.accept(classVisitor, 0);
//            modifiedByteCode = cw.toByteArray();
//            return modifiedByteCode;
//        }

    /*
     * TODO(): The following method is deprecated and must be removed
     */
//        public byte[] findAllStartThread(byte [] byteCode){
//            byte[] modifiedByteCode;
//            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
//            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
//                @Override
//                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
//                    MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
//                    if (name.equals("main")) {
//                        methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
//                            private boolean isALOAD = false;
//                            private int varThread = -1;
//                            @Override
//                            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
//                                if (opcode == Opcodes.INVOKEVIRTUAL && name.equals("start") && isALOAD && isCastableToThread(owner)) {
//                                    //mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);
//                                    //mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
//                                    //mv.visitVarInsn(Opcodes.ALOAD, varThread);
//                                    //mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/example/runtime/Scheduler", "threadStart", "(Ljava/lang/Thread;Ljava/lang/Thread;)V", false);
//                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
//                                    mv.visitVarInsn(Opcodes.ALOAD, varThread);
//                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/example/runtime/Scheduler", "threadStart", "(Ljava/lang/Thread;Ljava/lang/Thread;)V", false);
//                                    resetFlags();
//                                } else {
//                                    resetFlags();
//                                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
//                                }
//                            }
//
//                            @Override
//                            public void visitVarInsn(int opcode, int var) {
//                                if (opcode == Opcodes.ALOAD) {
//                                    resetFlags();
//                                    isALOAD = true;
//                                    varThread = var;
//                                    super.visitVarInsn(opcode, var);
//                                } else {
//                                    resetFlags();
//                                    super.visitVarInsn(opcode, var);
//                                }
//
//                            }
//
//                            private void resetFlags() {
//                                isALOAD = false;
//                                varThread = -1;
//                            }
//                        };
//                    }
//                    return methodVisitor;
//                }
//            };
//            ClassReader cr = new ClassReader(byteCode);
//            cr.accept(classVisitor, 0);
//            modifiedByteCode = cw.toByteArray();
//            return modifiedByteCode;
//        }

        /*
        * TODO(): The following method is deprecated and must be removed
        */
//        public byte [] findAllRuns(byte [] byteCode){
//            byte[] modifiedByteCode;
//            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
//            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
//                private String className;
//                private boolean isThreadSubclass;
//
//                @Override
//                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
//                    className = name;
//                    isThreadSubclass = isCastableToThread(superName);
//                    super.visit(version, access, name, signature, superName, interfaces);
//                }
//                @Override
//                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
//                    MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
//
//                    // If this class is a subclass of Thread and this method is the run method
//                    if (isThreadSubclass && name.equals("run") && descriptor.equals("()V")) {
//                        // Return a new MethodVisitor that analyzes the bytecode
//                        methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
//                            @Override
//                            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
//                                // If this instruction is a PUTFIELD or GETFIELD instruction
//                                if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.GETFIELD) {
//                                    // Log the instruction
//                                    System.out.println(className + "." + name + " " + (opcode == Opcodes.PUTFIELD ? "PUTFIELD" : "GETFIELD"));
//                                }
//
//                                super.visitFieldInsn(opcode, owner, name, descriptor);
//                            }
//                        };
//                    }
//                    return methodVisitor;
//                }
//            };
//            ClassReader cr = new ClassReader(byteCode);
//            cr.accept(classVisitor, 0);
//            modifiedByteCode = cw.toByteArray();
//            return modifiedByteCode;
//        }
}

