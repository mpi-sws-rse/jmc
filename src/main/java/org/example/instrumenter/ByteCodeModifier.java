package org.example.instrumenter;

import org.objectweb.asm.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ByteCodeModifier {

    /**
     * @property {@link #nextVarIndex} is used to store the index of the next local variable.
     * It is used whenever a new local variable is needed inside a method
     */
    private int nextVarIndex;

    /**
     * @property {@link #allByteCode} is used to store the bytecode of the compiled classes.
     * The key of the map is the name of the class and the value is the bytecode of the class
     * These classes are the user program classes that are going to be modified
     */
    public Map<String,byte[]> allByteCode;

    /**
     * @property {@link #mainClassName} is used to store the name of the main class of the user program.
     * The main class is the class that contains the main method
     */
    private final String mainClassName;

    /**
     * @property {@link #threadClassCandidate} is used in iterative analysis to find all the classes that contains
     * methods which create threads
     */
    private final List<String> threadClassCandidate = new ArrayList<>();

    /**
     * @property {@link #threadStartCandidate} is used in iterative analysis to find all the classes that contains
     * methods which start threads.
     */
    private final List<String> threadStartCandidate = new ArrayList<>();

    /**
     * @property {@link #threadJoinCandidate} is used in iterative analysis to find all the classes that contains methods
     * which join threads.
     */
    private final List<String> threadJoinCandidate = new ArrayList<>();

    /**
     * Following constructor is used to initialize the {@link #allByteCode} and {@link #mainClassName} variables
     */
    public ByteCodeModifier(Map<String, byte[]> allByteCode, String mainClassName) {
        this.mainClassName = mainClassName;
        this.allByteCode = allByteCode;
    }

    /**
     * Integrates the {@link org.example.runtime.RuntimeEnvironment} into the main class of the user's program.
     * <p>
     * This method modifies the bytecode of the main method in the main class to include calls to the RuntimeEnvironment
     * at the start and end of execution.
     * At the start of the main method, it adds the following instructions: <br>
     * 1. Initializes the {@link org.example.runtime.RuntimeEnvironment} with the current thread. <br>
     * 2. Creates a new instance of the {@link org.example.runtime.SchedulerThread}.<br>
     * 3. Sets the name of the SchedulerThread to "SchedulerThread".<br>
     * 4. Initializes the SchedulerThread in the RuntimeEnvironment with the current thread and the newly created
     * SchedulerThread.<br>
     * At the end of the main method, it adds the following instruction:<br>
     * 5. Calls the {@link org.example.runtime.RuntimeEnvironment#finishThreadRequest} method of the RuntimeEnvironment
     * with the current thread. <br>
     * These modifications allow the RuntimeEnvironment to manage and schedule threads during the execution of the
     * user's program.
     */
    public void addRuntimeEnvironment() {
        byte[] byteCode = allByteCode.get(mainClassName);
        byte[] modifiedByteCode;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        nextVarIndex = getNextVarIndex(byteCode, "main", "([Ljava/lang/String;)V");
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature
                    , String[] exceptions) {
                MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("main") && descriptor.equals("([Ljava/lang/String;)V")) {
                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                        @Override
                        public void visitCode() {
                            mv.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "java/lang/Thread",
                                    "currentThread",
                                    "()Ljava/lang/Thread;",
                                    false
                            );
                            mv.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "org/example/runtime/RuntimeEnvironment",
                                    "init",
                                    "(Ljava/lang/Thread;)V",
                                    false
                            );
                            mv.visitTypeInsn(
                                    Opcodes.NEW,
                                    "org/example/runtime/SchedulerThread"
                            );
                            mv.visitInsn(Opcodes.DUP);
                            mv.visitMethodInsn(
                                    Opcodes.INVOKESPECIAL,
                                    "org/example/runtime/SchedulerThread",
                                    "<init>",
                                    "()V",
                                    false
                            );
                            mv.visitVarInsn(Opcodes.ASTORE, nextVarIndex);
                            mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);
                            mv.visitLdcInsn("SchedulerThread");
                            mv.visitMethodInsn(
                                    Opcodes.INVOKEVIRTUAL,
                                    "org/example/runtime/SchedulerThread",
                                    "setName",
                                    "(Ljava/lang/String;)V",
                                    false
                            );
                            mv.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "java/lang/Thread",
                                    "currentThread",
                                    "()Ljava/lang/Thread;",
                                    false
                            );
                            mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);
                            mv.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "org/example/runtime/RuntimeEnvironment",
                                    "initSchedulerThread",
                                    "(Ljava/lang/Thread;Ljava/lang/Thread;)V",
                                    false
                            );
                            super.visitCode();
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "java/lang/Thread",
                                        "currentThread",
                                        "()Ljava/lang/Thread;",
                                        false
                                );
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/example/runtime/RuntimeEnvironment",
                                        "finishThreadRequest",
                                        "(Ljava/lang/Thread;)V",
                                        false
                                );
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

    /**
     * Retrieves the index of the next available local variable in a specified method.
     * <p>
     * This method analyzes the bytecode of a compiled class and identifies the maximum number of local variables used
     * in a specific method. It then returns the index of the next available local variable frame in the method's local
     * variable table. This is useful when modifying the bytecode of a method to add new local variables, ensuring that
     * the new variables do not overwrite existing ones.
     *
     * @param byteCode The bytecode of the compiled class.
     * @param methodName The name of the method in which to find the next available local variable index.
     * @param methodDescriptor The descriptor of the method (includes return type and parameters).
     * @return The index of the next available local variable in the specified method.
     */
    private int getNextVarIndex(byte[] byteCode, String methodName, String methodDescriptor) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals(methodName) && descriptor.equals(methodDescriptor)) {
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

    /**
     * Identifies and modifies points in the user's program where new threads are created.
     * <p>
     * This method iteratively analyzes the bytecode of the user's program, starting with the main class, and identifies
     * points where new threads are created. When such a point is found, it modifies the bytecode to include a call to
     * the {@link org.example.runtime.RuntimeEnvironment#addThread(Thread)} method immediately after the thread creation.
     * This allows the {@link org.example.runtime.RuntimeEnvironment} to keep track of all threads created during the
     * execution of the user's program. The method uses an iterative analysis approach, starting with the main class and
     * then examining all classes that have a method which is called by one of the methods of the previously analyzed
     * class. This ensures that all thread creation points in the user's program are covered.
     * <p>
     * The modifications made by this method enable the {@link org.example.runtime.RuntimeEnvironment} to manage and
     * schedule threads during the execution of the user's program.
     */
    public void modifyThreadCreation() {
        threadClassCandidate.add(mainClassName);
        while (!threadClassCandidate.isEmpty()) {
            String newClassName = threadClassCandidate.remove(threadClassCandidate.size()-1);
            byte [] byteCode = allByteCode.get(newClassName);
            byte[] modifiedByteCode;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                 String[] exceptions) {
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
                                // Do nothing
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
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                                                    boolean isInterface) {
                            if (opcode == Opcodes.INVOKESPECIAL && name.equals("<init>") && isDup) {
                                isInit = true;
                            } else {
                                resetFlags();
                            }
                            String ownerClassName = owner.replace("/", ".");
                            if (!ownerClassName.equals(newClassName) && !threadClassCandidate.contains(ownerClassName) &&
                                    allByteCode.containsKey(ownerClassName)) {
                                threadClassCandidate.add(ownerClassName);
                            }
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }

                        @Override
                        public void visitVarInsn(int opcode, int var) {
                            if (opcode == Opcodes.ASTORE && isInit) {
                                super.visitVarInsn(opcode, var);
                                mv.visitVarInsn(Opcodes.ALOAD, var);
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/example/runtime/RuntimeEnvironment",
                                        "addThread",
                                        "(Ljava/lang/Thread;)V",
                                        false
                                );
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

    /**
     * Identifies and modifies points in the user's program where threads are joined.
     * <p>
     * This method iteratively analyzes the bytecode of the user's program, starting with the main class, and identifies
     * points where threads are joined. When such a point is found, it modifies the bytecode to include a call to the
     * {@link org.example.runtime.RuntimeEnvironment#threadJoin(Thread, Thread)} method immediately before the thread
     * join operation. This allows the {@link org.example.runtime.RuntimeEnvironment} to keep track of all threads being
     * joined during the execution of the user's program.
     * <br>
     * The method uses an iterative analysis approach, starting with the main class and then examining all classes that
     * have a method which is called by one of the methods of the previously analyzed class. This ensures that all thread
     * join points in the user's program are covered.
     * <br>
     * The modifications made by this method enable the {@link org.example.runtime.RuntimeEnvironment} to manage and
     * schedule threads during the execution of the user's program.
     */
    public void modifyThreadJoin() {
        threadJoinCandidate.add(mainClassName);
        while (!threadJoinCandidate.isEmpty()) {
            String newClassName = threadJoinCandidate.remove(threadJoinCandidate.size() - 1);
            byte[] byteCode = allByteCode.get(newClassName);
            byte[] modifiedByteCode;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                 String[] exceptions) {
                    MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        private boolean isALOAD = false;
                        private int varThread = -1;

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                                                    boolean isInterface) {
                            if (opcode == Opcodes.INVOKEVIRTUAL && name.equals("join") &&
                                    isALOAD && isCastableToThread(owner)) {
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "java/lang/Thread",
                                        "currentThread",
                                        "()Ljava/lang/Thread;",
                                        false
                                );
                                mv.visitVarInsn(Opcodes.ALOAD, varThread);
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/example/runtime/RuntimeEnvironment",
                                        "threadJoin",
                                        "(Ljava/lang/Thread;Ljava/lang/Thread;)V",
                                        false
                                );
                                resetFlags();
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "java/lang/Thread",
                                        "currentThread",
                                        "()Ljava/lang/Thread;",
                                        false
                                );
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/example/runtime/RuntimeEnvironment",
                                        "waitRequest",
                                        "(Ljava/lang/Thread;)V",
                                        false
                                );
                            } else {
                                String ownerClassName = owner.replace("/", ".");
                                if (!ownerClassName.equals(newClassName) && !threadJoinCandidate.contains(ownerClassName) &&
                                        allByteCode.containsKey(ownerClassName)) {
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

    /**
     * Identifies and modifies points in the user's program where threads are started.
     *<p>
     * This method iteratively analyzes the bytecode of the user's program, starting with the main class, and identifies
     * points where threads are started. When such a point is found, it modifies the bytecode to include a call to
     * the {@link org.example.runtime.RuntimeEnvironment#threadStart(Thread, Thread)} method immediately before the thread
     * start operation. This allows the {@link org.example.runtime.RuntimeEnvironment} to keep track of all threads being
     * started during the execution of the user's program.
     * <br>
     * The method uses an iterative analysis approach, starting with the main class and then examining all classes that
     * have a method which is called by one of the methods of the previously analyzed class. This ensures that all thread
     * start points in the user's program are covered.
     * <br>
     * The modifications made by this method enable the {@link org.example.runtime.RuntimeEnvironment} to manage and
     * schedule threads during the execution of the user's program.
     */
    public void modifyThreadStart() {
        threadStartCandidate.add(mainClassName);
        while (!threadStartCandidate.isEmpty()) {
            String newClassName = threadStartCandidate.remove(threadStartCandidate.size() - 1);
            byte[] byteCode = allByteCode.get(newClassName);
            byte[] modifiedByteCode;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                 String[] exceptions) {
                    MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        private boolean isALOAD = false;

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                                                    boolean isInterface) {
                            if (opcode == Opcodes.INVOKEVIRTUAL && name.equals("start") && isALOAD &&
                                    isCastableToThread(owner)) {
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "java/lang/Thread",
                                        "currentThread",
                                        "()Ljava/lang/Thread;",
                                        false
                                );
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/example/runtime/RuntimeEnvironment",
                                        "threadStart",
                                        "(Ljava/lang/Thread;Ljava/lang/Thread;)V",
                                        false
                                );
                                resetFlags();
                            } else {
                                String ownerClassName = owner.replace("/", ".");
                                if (!ownerClassName.equals(newClassName) && !threadStartCandidate.contains(ownerClassName) &&
                                        allByteCode.containsKey(ownerClassName)) {
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
                                super.visitVarInsn(opcode, var);
                            } else {
                                resetFlags();
                                super.visitVarInsn(opcode, var);
                            }
                        }

                        private void resetFlags() {
                            isALOAD = false;
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

    /**
     * Identifies and modifies points in the user's program where fields are read and written.
     * <p>
     * This method iteratively analyzes the bytecode of the user's program, identifying points where {@code GETFIELD} and
     * {@code PUTFIELD} instructions are used to read and write fields, respectively. When such a point is found,
     * it modifies the bytecode to include calls to the
     * {@link org.example.runtime.RuntimeEnvironment#readOperation(Object, Thread, String, String, String)} and
     * {@link org.example.runtime.RuntimeEnvironment#writeOperation(Object, Object, Thread, String, String, String)}
     * methods, respectively. These calls are inserted immediately before the {@code GETFIELD} and {@code PUTFIELD}.
     * <br>
     * For {@code GETFIELD} operations, the method adds the following instructions:
     * {@link org.example.runtime.RuntimeEnvironment#readOperation(Object, Thread, String, String, String)}
     * <br>
     * For {@code PUTFIELD} operations, the method adds the following instructions:
     * {@link org.example.runtime.RuntimeEnvironment#writeOperation(Object, Object, Thread, String, String, String)}
     * <br>
     * The method analyzes all methods of all classes in the {@link #allByteCode} map and adds the instructions to the
     * corresponding methods.
     * <br>
     * The modifications made by this method enable the {@link org.example.runtime.RuntimeEnvironment} to track all
     * field read and write operations during the execution of the user's program.
     */
    public void modifyReadWriteOperation() {
        for (String newClass : allByteCode.keySet()) {
            byte[] byteCode = allByteCode.get(newClass);
            byte[] modifiedByteCode;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                 String[] exceptions) {
                    MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            if (isPrimitiveType(descriptor) && (opcode == Opcodes.GETFIELD ||
                                    opcode == Opcodes.PUTFIELD)) {
                                if (opcode == Opcodes.GETFIELD) {
                                    // Duplicate the top operand stack value which should be the value of the field
                                    mv.visitInsn(Opcodes.DUP);
                                    // Load the current thread onto the operand stack
                                    mv.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "java/lang/Thread",
                                            "currentThread",
                                            "()Ljava/lang/Thread;",
                                            false
                                    );
                                    // Load the owner of the field onto the operand stack
                                    mv.visitLdcInsn(owner.replace("/", "."));
                                    // Load the name of the field onto the operand stack
                                    mv.visitLdcInsn(name);
                                    // Load the descriptor of the field onto the operand stack
                                    mv.visitLdcInsn(descriptor);
                                    // Invoke the RuntimeEnvironment.newReadOperation method
                                    mv.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "org/example/runtime/RuntimeEnvironment",
                                            "readOperation",
                                            "(Ljava/lang/Object;Ljava/lang/Thread;Ljava/lang/String;" +
                                                    "Ljava/lang/String;Ljava/lang/String;)V",
                                            false
                                    );
                                } else if (opcode == Opcodes.PUTFIELD) {
                                    if (descriptor.equals("J") || descriptor.equals("D")) {
                                        mv.visitInsn(Opcodes.DUP2_X1);
                                    } else {
                                        mv.visitInsn(Opcodes.DUP2);
                                    }
                                    switch (descriptor) {
                                        case "I":
                                            mv.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "java/lang/Integer",
                                                    "valueOf",
                                                    "(I)Ljava/lang/Integer;",
                                                    false
                                            );
                                            break;
                                        case "J":
                                            mv.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "java/lang/Long",
                                                    "valueOf",
                                                    "(J)Ljava/lang/Long;",
                                                    false
                                            );
                                            break;
                                        case "F":
                                            mv.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "java/lang/Float",
                                                    "valueOf",
                                                    "(F)Ljava/lang/Float;",
                                                    false
                                            );
                                            break;
                                        case "D":
                                            mv.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "java/lang/Double",
                                                    "valueOf",
                                                    "(D)Ljava/lang/Double;",
                                                    false
                                            );
                                            break;
                                        case "Z":
                                            mv.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "java/lang/Boolean",
                                                    "valueOf",
                                                    "(Z)Ljava/lang/Boolean;",
                                                    false
                                            );
                                            break;
                                        case "C":
                                            mv.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "java/lang/Character",
                                                    "valueOf",
                                                    "(C)Ljava/lang/Character;",
                                                    false
                                            );
                                            break;
                                        case "B":
                                            mv.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "java/lang/Byte",
                                                    "valueOf",
                                                    "(B)Ljava/lang/Byte;",
                                                    false
                                            );
                                            break;
                                        case "S":
                                            mv.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "java/lang/Short",
                                                    "valueOf",
                                                    "(S)Ljava/lang/Short;",
                                                    false
                                            );
                                            break;
                                    }
                                    if (descriptor.equals("J") || descriptor.equals("D")) {
                                        mv.visitInsn(Opcodes.SWAP);
                                        mv.visitInsn(Opcodes.DUP_X1);
                                        mv.visitInsn(Opcodes.SWAP);
                                    }
                                    // Load the current thread onto the operand stack
                                    mv.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "java/lang/Thread",
                                            "currentThread",
                                            "()Ljava/lang/Thread;",
                                            false
                                    );
                                    // Load the owner of the field onto the operand stack
                                    mv.visitLdcInsn(owner.replace("/", "."));
                                    // Load the name of the field onto the operand stack
                                    mv.visitLdcInsn(name);
                                    // Load the descriptor of the field onto the operand stack
                                    mv.visitLdcInsn(descriptor);
                                    // Invoke the RuntimeEnvironment.newReadOperation method
                                    mv.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "org/example/runtime/RuntimeEnvironment",
                                            "writeOperation",
                                            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Thread;" +
                                                    "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                                            false
                                    );
                                    if (descriptor.equals("J") || descriptor.equals("D")) {
                                        mv.visitInsn(Opcodes.DUP_X2);
                                        mv.visitInsn(Opcodes.POP);
                                    }
                                }
                                super.visitFieldInsn(opcode, owner, name, descriptor);
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "java/lang/Thread",
                                        "currentThread",
                                        "()Ljava/lang/Thread;",
                                        false
                                );
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/example/runtime/RuntimeEnvironment",
                                        "waitRequest",
                                        "(Ljava/lang/Thread;)V",
                                        false
                                );
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

    /**
     * Checks if the given type is a primitive type.
     * <p>
     * @param type The type to check.
     * @return {@code true} if the type is a primitive type, {@code false} otherwise.
     */
    public boolean isPrimitiveType(String type) {
        return type.equals("I") || type.equals("J") || type.equals("F") || type.equals("D") || type.equals("Z") ||
                type.equals("C") || type.equals("B") || type.equals("S");
    }

    /**
     * Identifies and modifies points in the user's program where the `run` method of a thread is overridden.
     * <p>
     * This method iteratively analyzes the bytecode of the user's program, identifying classes that extend the `Thread`
     * class and override the `run` method. When such a class is found, it modifies the bytecode to include calls to the
     * {@link org.example.runtime.RuntimeEnvironment#waitRequest(Thread)} method at the start of the `run` method and the
     * {@link org.example.runtime.RuntimeEnvironment#finishThreadRequest(Thread)} method at the end of the `run` method.
     * <br>
     * The modifications made by this method enable the {@link org.example.runtime.RuntimeEnvironment} to manage and
     * schedule threads during the execution of the user's program, specifically handling the lifecycle of threads that
     * have a custom `run` method.
     * <p>
     * The method starts by checking all classes in the {@link #allByteCode} map to see if they are castable to `Thread`.
     * If a class is castable to `Thread`, it is assumed that it may override the `run` method, and its bytecode is
     * modified accordingly.
     */
    public void modifyThreadRun() {
        for (String className : allByteCode.keySet()) {
            if (isCastableToThread(className)) {
                byte[] byteCode = allByteCode.get(className);
                byte[] modifiedByteCode;
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                     String[] exceptions) {
                        MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                        if (name.equals("run") && descriptor.equals("()V")) {
                            methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                                @Override
                                public void visitCode() {
                                    mv.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "java/lang/Thread",
                                            "currentThread",
                                            "()Ljava/lang/Thread;",
                                            false
                                    );
                                    mv.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "org/example/runtime/RuntimeEnvironment",
                                            "waitRequest",
                                            "(Ljava/lang/Thread;)V",
                                            false
                                    );
                                    super.visitCode();
                                }

                                @Override
                                public void visitInsn(int opcode) {
                                    if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) ||
                                            opcode == Opcodes.ATHROW) {
                                        mv.visitMethodInsn(
                                                Opcodes.INVOKESTATIC,
                                                "java/lang/Thread",
                                                "currentThread",
                                                "()Ljava/lang/Thread;",
                                                false
                                        );
                                        mv.visitMethodInsn(
                                                Opcodes.INVOKESTATIC,
                                                "org/example/runtime/RuntimeEnvironment",
                                                "finishThreadRequest",
                                                "(Ljava/lang/Thread;)V",
                                                false
                                        );
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

    /**
     * Identifies and modifies points in the user's program where assertions are made.
     * <p>
     * This method iteratively analyzes the bytecode of the user's program, identifying points where assert statements
     * are used. When such a point is found, it modifies the bytecode to replace the call to the {@link AssertionError}
     * constructor with a call to the {@link org.example.runtime.RuntimeEnvironment#assertOperation(String)} method.
     * This allows the {@link org.example.runtime.RuntimeEnvironment} to handle assertion failures during the execution
     * of the user's program.
     * <br>
     * Additionally, the method replaces the throw instruction that follows the {@link AssertionError} constructor call
     * with a return instruction. This prevents the {@link AssertionError} from being thrown and allows the program to
     * continue executing.
     * <br>
     * The method analyzes all methods of all classes in the {@link #allByteCode} map and makes the necessary
     * modifications to the corresponding methods.
     * <br>
     * The modifications made by this method enable the {@link org.example.runtime.RuntimeEnvironment} to manage and
     * handle assertion failures during the execution of the user's program.
     */
    public void modifyAssert() {
        for (String className : allByteCode.keySet()) {
            byte[] byteCode = allByteCode.get(className);
            byte[] modifiedByteCode;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                 String[] exceptions) {
                    MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    methodVisitor = new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                        boolean replaced = false;
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                                                    boolean isInterface) {
                            if (opcode == Opcodes.INVOKESPECIAL && owner.equals("java/lang/AssertionError") &&
                                    name.equals("<init>")) {
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/example/runtime/RuntimeEnvironment",
                                        "assertOperation",
                                        "(Ljava/lang/String;)V",
                                        false
                                );
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

    /**
     * Checks if the given class is castable to `Thread` class.
     *
     * @param className The name of the class to check.
     * @return {@code true} if the class is castable to `Thread`, {@code false} otherwise.
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

    /**
     * Identifies and modifies points in the user's program where monitor locks are acquired and released.
     * <p>
     * This method iteratively analyzes the bytecode of the user's program, identifying points where {@code MONITORENTER}
     * and {@code MONITOREXIT} instructions are used to acquire and release monitor locks, respectively. When such a point
     * is found, it modifies the bytecode to include calls to the
     * {@link org.example.runtime.RuntimeEnvironment#enterMonitor(Object, Thread)} and
     * {@link org.example.runtime.RuntimeEnvironment#exitMonitor(Object, Thread)} methods, respectively. These calls are
     * inserted immediately before the {@code MONITORENTER} and {@code MONITOREXIT} instructions.
     * <br>
     * For {@code MONITORENTER} operations, the method adds the following instructions: <br>
     * 1. {@link org.example.runtime.RuntimeEnvironment#enterMonitor(Object, Thread)} <br>
     * 2. {@link org.example.runtime.RuntimeEnvironment#acquiredLock(Object, Thread)}
     * <br>
     * For `MONITOREXIT` operations, the method adds the following instructions: <br>
     * 1. {@link org.example.runtime.RuntimeEnvironment#exitMonitor(Object, Thread)} <br>
     * 2. {@link org.example.runtime.RuntimeEnvironment#releasedLock(Object, Thread)}
     * <br>
     * The method analyzes all methods of all classes in the {@link #allByteCode} map and adds the instructions to the
     * corresponding methods.
     * <br>
     * The modifications made by this method enable the {@link org.example.runtime.RuntimeEnvironment} to track all
     * monitor lock acquisitions and releases during the execution of the user's program, providing a mechanism for
     * managing and scheduling threads based on their lock states.
     */
    public void modifyMonitorInstructions() {
        for (Map.Entry<String, byte[]> entry : allByteCode.entrySet()) {
            byte[] byteCode = entry.getValue();
            byte[] modifiedByteCode;

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            ClassReader cr = new ClassReader(byteCode);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                 String[] exceptions) {
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
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "java/lang/Thread",
                                        "currentThread",
                                        "()Ljava/lang/Thread;",
                                        false
                                );
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/example/runtime/RuntimeEnvironment",
                                        "enterMonitor",
                                        "(Ljava/lang/Object;Ljava/lang/Thread;)V",
                                        false
                                );
                                super.visitInsn(opcode);
                                isASTORE = false;
                                foundMonitorEnter = true;
                            } else if (opcode == Opcodes.MONITOREXIT && isALOAD) {
                                mv.visitVarInsn(Opcodes.ALOAD, var);
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "java/lang/Thread",
                                        "currentThread",
                                        "()Ljava/lang/Thread;",
                                        false
                                );
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/example/runtime/RuntimeEnvironment",
                                        "exitMonitor",
                                        "(Ljava/lang/Object;Ljava/lang/Thread;)V",
                                        false
                                );
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
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "java/lang/Thread",
                                        "currentThread",
                                        "()Ljava/lang/Thread;",
                                        false
                                );
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/example/runtime/RuntimeEnvironment",
                                        "acquiredLock",
                                        "(Ljava/lang/Object;Ljava/lang/Thread;)V",
                                        false
                                );
                            } else if (foundMonitorExit) {
                                foundMonitorExit = false;
                                mv.visitVarInsn(Opcodes.ALOAD, oldvar);
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "java/lang/Thread",
                                        "currentThread",
                                        "()Ljava/lang/Thread;",
                                        false
                                );
                                mv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/example/runtime/RuntimeEnvironment",
                                        "releasedLock",
                                        "(Ljava/lang/Object;Ljava/lang/Thread;)V",
                                        false
                                );
                            }
                        }
                    };
                }
            };
            cr.accept(cv, 0);
            modifiedByteCode = cw.toByteArray();
            allByteCode.put(entry.getKey(), modifiedByteCode);
        }
    }
}