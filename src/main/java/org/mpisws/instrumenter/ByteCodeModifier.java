package org.mpisws.instrumenter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.symbolic.SymbolicBoolean;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * The ByteCodeModifier class is responsible for modifying the bytecode of the user's program to
 * integrate with the {@link org.mpisws.runtime.RuntimeEnvironment}. It maintains a map of class
 * names to their bytecode, which is used to modify classes. The class provides functionality to
 * modify various aspects of the user's program, including thread creation, thread joining, thread
 * starting, read/write operations, assert statements, and monitor instructions. The class uses the
 * ASM library to analyze and modify the bytecode of the user's program. The class requires a map of
 * class names to their bytecode and the name of the main class upon construction. It also includes
 * functionality for identifying classes that are castable to Thread and checking if a given type is
 * a primitive type. The ByteCodeModifier class is designed to modify the user's program to enable
 * the {@link org.mpisws.runtime.RuntimeEnvironment} to manage and schedule threads during
 * execution.
 */
public class ByteCodeModifier {

  /**
   * @property {@link #nextVarIndex} is used to store the index of the next local variable. It is
   *     used whenever a new local variable is needed inside a method
   */
  private int nextVarIndex;
  private static final Logger LOGGER = LogManager.getLogger(ByteCodeModifier.class);


  /**
   * @property {@link #allByteCode} is used to store the bytecode of the compiled classes. The key
   *     of the map is the name of the class and the value is the bytecode of the class These
   *     classes are the user program classes that are going to be modified
   */
  public Map<String, byte[]> allByteCode;

  /**
   * @property {@link #mainClassName} is used to store the name of the main class of the user
   *     program. The main class is the class that contains the main method
   */
  private final String mainClassName;

  /**
   * @property {@link #threadClassCandidate} is used in iterative analysis to find all the classes
   *     that contains methods which create threads
   */
  private final List<String> threadClassCandidate = new ArrayList<>();

  /**
   * @property {@link #threadStartCandidate} is used in iterative analysis to find all the classes
   *     that contains methods which start threads.
   */
  private final List<String> threadStartCandidate = new ArrayList<>();

  /**
   * @property {@link #threadJoinCandidate} is used in iterative analysis to find all the classes
   *     that contains methods which join threads.
   */
  private final List<String> threadJoinCandidate = new ArrayList<>();

  /**
   * Following constructor is used to initialize the {@link #allByteCode} and {@link #mainClassName}
   * variables
   */
  public ByteCodeModifier(Map<String, byte[]> allByteCode, String mainClassName) {
    this.mainClassName = mainClassName;
    this.allByteCode = allByteCode;
  }

  /**
   * Integrates the {@link org.mpisws.runtime.RuntimeEnvironment} into the main class of the user's
   * program.
   *
   * <p>This method modifies the bytecode of the main method in the main class to include calls to
   * the RuntimeEnvironment at the start and end of execution. At the start of the main method, it
   * adds the following instructions: <br>
   * 1. Initializes the {@link org.mpisws.runtime.RuntimeEnvironment} with the current thread. <br>
   * 2. Creates a new instance of the {@link org.mpisws.runtime.SchedulerThread}.<br>
   * 3. Sets the name of the SchedulerThread to "SchedulerThread".<br>
   * 4. Initializes the SchedulerThread in the RuntimeEnvironment with the current thread and the
   * newly created SchedulerThread.<br>
   * At the end of the main method, it adds the following instruction:<br>
   * 5. Calls the {@link org.mpisws.runtime.RuntimeEnvironment#finishThreadRequest} method of the
   * RuntimeEnvironment with the current thread. <br>
   * These modifications allow the RuntimeEnvironment to manage and schedule threads during the
   * execution of the user's program.
   */
  public void addRuntimeEnvironment() {
    byte[] byteCode = allByteCode.get(mainClassName);
    byte[] modifiedByteCode;
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    nextVarIndex = getNextVarIndex(byteCode, "main", "([Ljava/lang/String;)V");
    ClassVisitor classVisitor =
        new ClassVisitor(Opcodes.ASM9, cw) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor =
                cv.visitMethod(access, name, descriptor, signature, exceptions);
            if (name.equals("main") && descriptor.equals("([Ljava/lang/String;)V")) {
              methodVisitor =
                  new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                    @Override
                    public void visitCode() {
                      mv.visitMethodInsn(
                          Opcodes.INVOKESTATIC,
                          "java/lang/Thread",
                          "currentThread",
                          "()Ljava/lang/Thread;",
                          false);
                      mv.visitMethodInsn(
                          Opcodes.INVOKESTATIC,
                          "org/mpisws/runtime/RuntimeEnvironment",
                          "init",
                          "(Ljava/lang/Thread;)V",
                          false);
                      mv.visitTypeInsn(Opcodes.NEW, "org/mpisws/runtime/SchedulerThread");
                      mv.visitInsn(Opcodes.DUP);
                      mv.visitMethodInsn(
                          Opcodes.INVOKESPECIAL,
                          "org/mpisws/runtime/SchedulerThread",
                          "<init>",
                          "()V",
                          false);
                      mv.visitVarInsn(Opcodes.ASTORE, nextVarIndex);
                      mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);
                      mv.visitLdcInsn("SchedulerThread");
                      mv.visitMethodInsn(
                          Opcodes.INVOKEVIRTUAL,
                          "org/mpisws/runtime/SchedulerThread",
                          "setName",
                          "(Ljava/lang/String;)V",
                          false);
                      mv.visitMethodInsn(
                          Opcodes.INVOKESTATIC,
                          "java/lang/Thread",
                          "currentThread",
                          "()Ljava/lang/Thread;",
                          false);
                      mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);
                      mv.visitMethodInsn(
                          Opcodes.INVOKESTATIC,
                          "org/mpisws/runtime/RuntimeEnvironment",
                          "initSchedulerThread",
                          "(Ljava/lang/Thread;Ljava/lang/Thread;)V",
                          false);
                      mv.visitMethodInsn(
                          Opcodes.INVOKESTATIC,
                          "java/lang/Thread",
                          "currentThread",
                          "()Ljava/lang/Thread;",
                          false);
                      mv.visitMethodInsn(
                          Opcodes.INVOKESTATIC,
                          "org/mpisws/runtime/RuntimeEnvironment",
                          "mainThreadStart",
                          "(Ljava/lang/Thread;)V",
                          false);
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
                            false);
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpisws/runtime/RuntimeEnvironment",
                            "finishThreadRequest",
                            "(Ljava/lang/Thread;)V",
                            false);
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
    allByteCode.put(mainClassName, modifiedByteCode);
  }

  /**
   * Retrieves the index of the next available local variable in a specified method.
   *
   * <p>This method analyzes the bytecode of a compiled class and identifies the maximum number of
   * local variables used in a specific method. It then returns the index of the next available
   * local variable frame in the method's local variable table. This is useful when modifying the
   * bytecode of a method to add new local variables, ensuring that the new variables do not
   * overwrite existing ones.
   *
   * @param byteCode The bytecode of the compiled class.
   * @param methodName The name of the method in which to find the next available local variable
   *     index.
   * @param methodDescriptor The descriptor of the method (includes return type and parameters).
   * @return The index of the next available local variable in the specified method.
   */
  private int getNextVarIndex(byte[] byteCode, String methodName, String methodDescriptor) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    ClassVisitor classVisitor =
        new ClassVisitor(Opcodes.ASM9, cw) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor =
                cv.visitMethod(access, name, descriptor, signature, exceptions);
            if (name.equals(methodName) && descriptor.equals(methodDescriptor)) {
              methodVisitor =
                  new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                    @Override
                    public void visitMaxs(int maxStack, int maxLocals) {
                      nextVarIndex = maxLocals;
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
   *
   * <p>This method iteratively analyzes the bytecode of the user's program, starting with the main
   * class, and identifies points where new threads are created. When such a point is found, it
   * modifies the bytecode to include a call to the {@link
   * org.mpisws.runtime.RuntimeEnvironment#addThread(Thread)} method immediately after the thread
   * creation. This allows the {@link org.mpisws.runtime.SchedulerThread} to keep track of all
   * threads created during the execution of the user's program. The method uses an iterative
   * analysis approach, starting with the main class and then examining all classes that have a
   * method which is called by one of the methods of the previously analyzed class. This ensures
   * that all thread creation points in the user's program are covered. The modifications made by
   * this method enable the {@link org.mpisws.runtime.SchedulerThread} to manage and schedule
   * threads during the execution of the user's program.
   */
  public void modifyThreadCreation() {
    threadClassCandidate.add(mainClassName);
    while (!threadClassCandidate.isEmpty()) {
      String newClassName = threadClassCandidate.remove(threadClassCandidate.size() - 1);
      byte[] byteCode = allByteCode.get(newClassName);
      byte[] modifiedByteCode;
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
      ClassVisitor classVisitor =
          new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              MethodVisitor methodVisitor =
                  cv.visitMethod(access, name, descriptor, signature, exceptions);
              methodVisitor =
                  new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                    private boolean isNew = false;

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                      if (opcode == Opcodes.NEW && isCastableToThread(type)) {
                        resetFlags();
                        isNew = true;
                        super.visitTypeInsn(opcode, type);
                        mv.visitInsn(Opcodes.DUP);
                      } else {
                        resetFlags();
                        super.visitTypeInsn(opcode, type);
                      }
                    }

                    @Override
                    public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String name,
                        String descriptor,
                        boolean isInterface) {
                      if (opcode == Opcodes.INVOKESPECIAL && name.equals("<init>") && isNew) {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpisws/runtime/RuntimeEnvironment",
                            "addThread",
                            "(Ljava/lang/Thread;)V",
                            false);
                      } else {
                        resetFlags();
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                      }
                      String ownerClassName = owner.replace("/", ".");
                      if (!ownerClassName.equals(newClassName)
                          && !threadClassCandidate.contains(ownerClassName)
                          && allByteCode.containsKey(ownerClassName)) {
                        threadClassCandidate.add(ownerClassName);
                      }
                    }

                    private void resetFlags() {
                      isNew = false;
                    }
                  };
              return methodVisitor;
            }
          };
      ClassReader cr = new ClassReader(byteCode);
      cr.accept(classVisitor, 0);
      modifiedByteCode = cw.toByteArray();
      allByteCode.put(newClassName, modifiedByteCode);
    }
  }

  /**
   * Identifies and modifies points in the user's program where threads are joined.
   *
   * <p>This method iteratively analyzes the bytecode of the user's program, starting with the main
   * class, and identifies points where threads are joined. When such a point is found, it modifies
   * the bytecode to include a call to the {@link
   * org.mpisws.runtime.RuntimeEnvironment#threadJoin(Thread, Thread)} method immediately before the
   * thread join operation. This allows the {@link org.mpisws.runtime.SchedulerThread} to keep track
   * of all threads being joined during the execution of the user's program. <br>
   * The method uses an iterative analysis approach, starting with the main class and then examining
   * all classes that have a method which is called by one of the methods of the previously analyzed
   * class. This ensures that all thread join points in the user's program are covered. <br>
   * The modifications made by this method enable the {@link org.mpisws.runtime.SchedulerThread} to
   * manage and schedule threads during the execution of the user's program.
   */
  public void modifyThreadJoin() {
    threadJoinCandidate.add(mainClassName);
    while (!threadJoinCandidate.isEmpty()) {
      String newClassName = threadJoinCandidate.remove(threadJoinCandidate.size() - 1);
      byte[] byteCode = allByteCode.get(newClassName);
      byte[] modifiedByteCode;
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
      ClassVisitor classVisitor =
          new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              MethodVisitor methodVisitor =
                  cv.visitMethod(access, name, descriptor, signature, exceptions);
              methodVisitor =
                  new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                    @Override
                    public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String name,
                        String descriptor,
                        boolean isInterface) {
                      if (opcode == Opcodes.INVOKEVIRTUAL
                          && name.equals("join")
                          && descriptor.equals("()V")
                          && isCastableToThread(owner)) {
                        mv.visitInsn(Opcodes.DUP);
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "java/lang/Thread",
                            "currentThread",
                            "()Ljava/lang/Thread;",
                            false);
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpisws/runtime/RuntimeEnvironment",
                            "threadJoin",
                            "(Ljava/lang/Thread;Ljava/lang/Thread;)V",
                            false);
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "java/lang/Thread",
                            "currentThread",
                            "()Ljava/lang/Thread;",
                            false);
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpisws/runtime/RuntimeEnvironment",
                            "waitRequest",
                            "(Ljava/lang/Thread;)V",
                            false);
                      } else {
                        String ownerClassName = owner.replace("/", ".");
                        if (!ownerClassName.equals(newClassName)
                            && !threadJoinCandidate.contains(ownerClassName)
                            && allByteCode.containsKey(ownerClassName)) {
                          threadJoinCandidate.add(ownerClassName);
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                      }
                    }
                  };
              return methodVisitor;
            }
          };
      ClassReader cr = new ClassReader(byteCode);
      cr.accept(classVisitor, 0);
      modifiedByteCode = cw.toByteArray();
      allByteCode.put(newClassName, modifiedByteCode);
    }
  }

  /**
   * Identifies and modifies points in the user's program where threads are started.
   *
   * <p>This method iteratively analyzes the bytecode of the user's program, starting with the main
   * class, and identifies points where threads are started. When such a point is found, it modifies
   * the bytecode to include a call to the {@link
   * org.mpisws.runtime.RuntimeEnvironment#threadStart(Thread, Thread)} method immediately before
   * the thread start operation. This allows the {@link org.mpisws.runtime.SchedulerThread} to keep
   * track of all threads being started during the execution of the user's program. <br>
   * The method uses an iterative analysis approach, starting with the main class and then examining
   * all classes that have a method which is called by one of the methods of the previously analyzed
   * class. This ensures that all thread start points in the user's program are covered. <br>
   * The modifications made by this method enable the {@link org.mpisws.runtime.SchedulerThread} to
   * manage and schedule threads during the execution of the user's program.
   */
  public void modifyThreadStart() {
    threadStartCandidate.add(mainClassName);
    while (!threadStartCandidate.isEmpty()) {
      String newClassName = threadStartCandidate.remove(threadStartCandidate.size() - 1);
      byte[] byteCode = allByteCode.get(newClassName);
      byte[] modifiedByteCode;
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
      ClassVisitor classVisitor =
          new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              MethodVisitor methodVisitor =
                  cv.visitMethod(access, name, descriptor, signature, exceptions);
              methodVisitor =
                  new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                    @Override
                    public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String name,
                        String descriptor,
                        boolean isInterface) {
                      if (opcode == Opcodes.INVOKEVIRTUAL
                          && name.equals("start")
                          && descriptor.equals("()V")
                          && isCastableToThread(owner)) {
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "java/lang/Thread",
                            "currentThread",
                            "()Ljava/lang/Thread;",
                            false);
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpisws/runtime/RuntimeEnvironment",
                            "threadStart",
                            "(Ljava/lang/Thread;Ljava/lang/Thread;)V",
                            false);
                      } else {
                        String ownerClassName = owner.replace("/", ".");
                        if (!ownerClassName.equals(newClassName)
                            && !threadStartCandidate.contains(ownerClassName)
                            && allByteCode.containsKey(ownerClassName)) {
                          threadStartCandidate.add(ownerClassName);
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                      }
                    }
                  };
              return methodVisitor;
            }
          };
      ClassReader cr = new ClassReader(byteCode);
      cr.accept(classVisitor, 0);
      modifiedByteCode = cw.toByteArray();
      allByteCode.put(newClassName, modifiedByteCode);
    }
  }

  /**
   * Identifies and modifies points in the user's program where fields are read and written.
   *
   * <p>This method iteratively analyzes the bytecode of the user's program, identifying points
   * where {@code GETFIELD} and {@code PUTFIELD} instructions are used to read and write fields,
   * respectively. When such a point is found, it modifies the bytecode to include calls to the
   * {@link org.mpisws.runtime.RuntimeEnvironment#readOperation(Object, Thread, String, String,
   * String)} and {@link org.mpisws.runtime.RuntimeEnvironment#writeOperation(Object, Object,
   * Thread, String, String, String)} methods, respectively. These calls are inserted immediately
   * before the {@code GETFIELD} and {@code PUTFIELD}. <br>
   * For {@code GETFIELD} operations, the method adds the following instructions: {@link
   * org.mpisws.runtime.RuntimeEnvironment#readOperation(Object, Thread, String, String, String)}
   * <br>
   * For {@code PUTFIELD} operations, the method adds the following instructions: {@link
   * org.mpisws.runtime.RuntimeEnvironment#writeOperation(Object, Object, Thread, String, String,
   * String)} <br>
   * The method analyzes all methods of all classes in the {@link #allByteCode} map and adds the
   * instructions to the corresponding methods. <br>
   * The modifications made by this method enable the {@link org.mpisws.runtime.RuntimeEnvironment}
   * to track all field read and write operations during the execution of the user's program. <br>
   * In the case of a field write operation, if the field is of type long or double, the method
   * duplicates the top operand stack and puts the duplicated value before the second operand stack
   * value. By using {@code DUP2_X1}, the operand stack from : ..., value2, value1 -> ..., value1,
   * value2, value1 is achieved. After wrapping the primitive value into its corresponding wrapper
   * class, the operand stack is as follows: ..., value1, value2, wrapperValue1. The method then
   * swaps the top two operand stack using {@code SWAP} to get the operand stack as follows: ...,
   * value1, wrapperValue1, value2. The method then duplicates the top operand stack value using
   * {@code DUP_X1} to get the operand stack as follows: ..., value1, value2, wrapperValue1, value2.
   * The method then swaps the top two operand stack values using {@code SWAP} to get the operand
   * stack as follows: ..., value1, value2, value2, wrapperValue1. The method then invokes the
   * {@link org.mpisws.runtime.RuntimeEnvironment#writeOperation(Object, Object, Thread, String,
   * String, String)} method. Now the operand stack is as follows: ..., value1, value2. The method
   * then duplicates the top operand stack value using {@code DUP_X2} to get the operand stack as
   * follows: ..., value2, value1, value2. The method then pops the top operand stack value using
   * {@code POP} to get the operand stack as follows: ..., value2, value1. Now the operand stack is
   * in consistent state. This procedure is necessary to handle the long and double types as they
   * occupy two frames in the operand stack.
   */
  public void modifyReadWriteOperation() {
    for (String newClass : allByteCode.keySet()) {
      byte[] byteCode = allByteCode.get(newClass);
      byte[] modifiedByteCode;
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
      ClassVisitor classVisitor =
          new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              MethodVisitor methodVisitor =
                  cv.visitMethod(access, name, descriptor, signature, exceptions);
              methodVisitor =
                  new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                    @Override
                    public void visitFieldInsn(
                        int opcode, String owner, String name, String descriptor) {
                      if (isPrimitiveType(descriptor, name)
                          && (opcode == Opcodes.GETFIELD
                              || opcode == Opcodes.PUTFIELD
                              || opcode == Opcodes.GETSTATIC
                              || opcode == Opcodes.PUTSTATIC)
                          && isOwnerAllowed(owner)) {
                        if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
                          // Duplicate the top operand stack value which should be the value of the
                          // field
                          // if it is a GETFIELD operation. Otherwise, push null onto the operand
                          // stack.
                          if (opcode == Opcodes.GETFIELD) {
                            mv.visitInsn(Opcodes.DUP);
                          } else {
                            mv.visitInsn(Opcodes.ACONST_NULL);
                          }
                          // mv.visitInsn(Opcodes.DUP);
                          // Load the current thread onto the operand stack
                          mv.visitMethodInsn(
                              Opcodes.INVOKESTATIC,
                              "java/lang/Thread",
                              "currentThread",
                              "()Ljava/lang/Thread;",
                              false);
                          // Load the owner of the field onto the operand stack
                          mv.visitLdcInsn(owner.replace("/", "."));
                          // Load the name of the field onto the operand stack
                          mv.visitLdcInsn(name);
                          // Load the descriptor of the field onto the operand stack
                          mv.visitLdcInsn(descriptor);
                          // Invoke the RuntimeEnvironment.newReadOperation method
                          mv.visitMethodInsn(
                              Opcodes.INVOKESTATIC,
                              "org/mpisws/runtime/RuntimeEnvironment",
                              "readOperation",
                              "(Ljava/lang/Object;Ljava/lang/Thread;Ljava/lang/String;"
                                  + "Ljava/lang/String;Ljava/lang/String;)V",
                              false);
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
                                  false);
                              break;
                            case "J":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Long",
                                  "valueOf",
                                  "(J)Ljava/lang/Long;",
                                  false);
                              break;
                            case "F":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Float",
                                  "valueOf",
                                  "(F)Ljava/lang/Float;",
                                  false);
                              break;
                            case "D":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Double",
                                  "valueOf",
                                  "(D)Ljava/lang/Double;",
                                  false);
                              break;
                            case "Z":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Boolean",
                                  "valueOf",
                                  "(Z)Ljava/lang/Boolean;",
                                  false);
                              break;
                            case "C":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Character",
                                  "valueOf",
                                  "(C)Ljava/lang/Character;",
                                  false);
                              break;
                            case "B":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Byte",
                                  "valueOf",
                                  "(B)Ljava/lang/Byte;",
                                  false);
                              break;
                            case "S":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Short",
                                  "valueOf",
                                  "(S)Ljava/lang/Short;",
                                  false);
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
                              false);
                          // Load the owner of the field onto the operand stack
                          mv.visitLdcInsn(owner.replace("/", "."));
                          // Load the name of the field onto the operand stack
                          mv.visitLdcInsn(name);
                          // Load the descriptor of the field onto the operand stack
                          mv.visitLdcInsn(descriptor);
                          // Invoke the RuntimeEnvironment.newReadOperation method
                          mv.visitMethodInsn(
                              Opcodes.INVOKESTATIC,
                              "org/mpisws/runtime/RuntimeEnvironment",
                              "writeOperation",
                              "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Thread;"
                                  + "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                              false);
                          if (descriptor.equals("J") || descriptor.equals("D")) {
                            mv.visitInsn(Opcodes.DUP_X2);
                            mv.visitInsn(Opcodes.POP);
                          }
                        } else if (opcode == Opcodes.PUTSTATIC) {
                          if (descriptor.equals("J") || descriptor.equals("D")) {
                            mv.visitInsn(Opcodes.DUP2);
                          } else {
                            mv.visitInsn(Opcodes.DUP);
                          }
                          switch (descriptor) {
                            case "I":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Integer",
                                  "valueOf",
                                  "(I)Ljava/lang/Integer;",
                                  false);
                              break;
                            case "J":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Long",
                                  "valueOf",
                                  "(J)Ljava/lang/Long;",
                                  false);
                              break;
                            case "F":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Float",
                                  "valueOf",
                                  "(F)Ljava/lang/Float;",
                                  false);
                              break;
                            case "D":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Double",
                                  "valueOf",
                                  "(D)Ljava/lang/Double;",
                                  false);
                              break;
                            case "Z":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Boolean",
                                  "valueOf",
                                  "(Z)Ljava/lang/Boolean;",
                                  false);
                              break;
                            case "C":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Character",
                                  "valueOf",
                                  "(C)Ljava/lang/Character;",
                                  false);
                              break;
                            case "B":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Byte",
                                  "valueOf",
                                  "(B)Ljava/lang/Byte;",
                                  false);
                              break;
                            case "S":
                              mv.visitMethodInsn(
                                  Opcodes.INVOKESTATIC,
                                  "java/lang/Short",
                                  "valueOf",
                                  "(S)Ljava/lang/Short;",
                                  false);
                              break;
                          }
                          mv.visitInsn(Opcodes.ACONST_NULL);
                          mv.visitInsn(Opcodes.SWAP);
                          // Load the current thread onto the operand stack
                          mv.visitMethodInsn(
                              Opcodes.INVOKESTATIC,
                              "java/lang/Thread",
                              "currentThread",
                              "()Ljava/lang/Thread;",
                              false);
                          // Load the owner of the field onto the operand stack
                          mv.visitLdcInsn(owner.replace("/", "."));
                          // Load the name of the field onto the operand stack
                          mv.visitLdcInsn(name);
                          // Load the descriptor of the field onto the operand stack
                          mv.visitLdcInsn(descriptor);
                          // Invoke the RuntimeEnvironment.newReadOperation method
                          mv.visitMethodInsn(
                              Opcodes.INVOKESTATIC,
                              "org/mpisws/runtime/RuntimeEnvironment",
                              "writeOperation",
                              "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Thread;"
                                  + "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                              false);
                        }
                        super.visitFieldInsn(opcode, owner, name, descriptor);
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "java/lang/Thread",
                            "currentThread",
                            "()Ljava/lang/Thread;",
                            false);
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpisws/runtime/RuntimeEnvironment",
                            "waitRequest",
                            "(Ljava/lang/Thread;)V",
                            false);
                        mv.visitInsn(Opcodes.NOP);
                      } else {
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
      allByteCode.put(newClass, modifiedByteCode);
    }
  }

  private boolean isOwnerAllowed(String owner) {
    return !owner.startsWith("java/") && !owner.startsWith("sun/") && !owner.startsWith("jdk/");
  }

  /**
   * Checks if the given type is a primitive type.
   *
   * @param type The type to check.
   * @return {@code true} if the type is a primitive type, {@code false} otherwise.
   */
  public boolean isPrimitiveType(String type, String name) {
    // Check if the name starts with $ to avoid adding read and write operations for synthetic
    // fields
    // Like : $assertionsDisabled
    return /*(type.equals("I") || type.equals("J") || type.equals("F") || type.equals("D") || type.equals("Z") ||
           type.equals("C") || type.equals("B") || type.equals("S")) &&*/ !name
        .startsWith("$");
  }

  /**
   * Identifies and modifies points in the user's program where the `run` method of a thread is
   * overridden.
   *
   * <p>This method iteratively analyzes the bytecode of the user's program, identifying classes
   * that extend the `Thread` class and override the `run` method. When such a class is found, it
   * modifies the bytecode to include calls to the {@link
   * org.mpisws.runtime.RuntimeEnvironment#waitRequest(Thread)} method at the start of the `run`
   * method and the {@link org.mpisws.runtime.RuntimeEnvironment#finishThreadRequest(Thread)} method
   * at the end of the `run` method. <br>
   * The modifications made by this method enable the {@link org.mpisws.runtime.SchedulerThread} to
   * manage and schedule threads during the execution of the user's program, specifically handling
   * the lifecycle of threads that have a custom `run` method. <br>
   * The method starts by checking all classes in the {@link #allByteCode} map to see if they are
   * castable to `Thread`. If a class is castable to `Thread`, it is assumed that it may override
   * the `run` method, and its bytecode is modified accordingly.
   */
  public void modifyThreadRun() {
    for (String className : allByteCode.keySet()) {
      if (isCastableToThread(className)) {
        byte[] byteCode = allByteCode.get(className);
        byte[] modifiedByteCode;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ClassVisitor classVisitor =
            new ClassVisitor(Opcodes.ASM9, cw) {
              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                MethodVisitor methodVisitor =
                    cv.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("run") && descriptor.equals("()V")) {
                  methodVisitor =
                      new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                        @Override
                        public void visitCode() {
                          mv.visitMethodInsn(
                              Opcodes.INVOKESTATIC,
                              "java/lang/Thread",
                              "currentThread",
                              "()Ljava/lang/Thread;",
                              false);
                          mv.visitMethodInsn(
                              Opcodes.INVOKESTATIC,
                              "org/mpisws/runtime/RuntimeEnvironment",
                              "waitRequest",
                              "(Ljava/lang/Thread;)V",
                              false);
                          super.visitCode();
                        }

                        @Override
                        public void visitInsn(int opcode) {
                          if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN)) {
                            mv.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "java/lang/Thread",
                                "currentThread",
                                "()Ljava/lang/Thread;",
                                false);
                            mv.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "org/mpisws/runtime/RuntimeEnvironment",
                                "finishThreadRequest",
                                "(Ljava/lang/Thread;)V",
                                false);
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
        allByteCode.put(className, modifiedByteCode);
      }
    }
  }

  /**
   * Identifies and modifies points in the user's program where the `park` and `unpark` methods from
   * the `java.util.concurrent.locks.LockSupport` class are called.
   *
   * <p>This method iteratively analyzes the bytecode of the user's program, identifying points
   * where the `park` and `unpark` methods from the `java.util.concurrent.locks.LockSupport` class
   * are called. When such a point is found, it modifies the bytecode to include calls to the
   * `org.mpisws.util.concurrent.LockSupport` class, which provides the same functionality as the
   * `java.util.concurrent.locks.LockSupport` class but with additional support for thread
   * management by the {@link org.mpisws.runtime.RuntimeEnvironment}. <br>
   * The modifications made by this method enable the {@link org.mpisws.runtime.RuntimeEnvironment}
   * to manage and schedule threads during the execution of the user's program, specifically
   * handling the `park` and `unpark` operations.
   */
  public void modifyParkAndUnpark() {
    for (String className : allByteCode.keySet()) {
      byte[] byteCode = allByteCode.get(className);
      byte[] modifiedByteCode;
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
      ClassVisitor classVisitor =
          new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              MethodVisitor methodVisitor =
                  cv.visitMethod(access, name, descriptor, signature, exceptions);
              methodVisitor =
                  new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                    @Override
                    public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String name,
                        String descriptor,
                        boolean isInterface) {
                      if (opcode == Opcodes.INVOKESTATIC
                          && owner.equals("java/util/concurrent/locks/LockSupport")
                          && name.equals("park")
                          && descriptor.equals("()V")) {
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpisws/util/concurrent/LockSupport",
                            "park",
                            "()V",
                            false);
                      } else if (opcode == Opcodes.INVOKESTATIC
                          && owner.equals("java/util/concurrent/locks/LockSupport")
                          && name.equals("unpark")
                          && descriptor.equals("(Ljava/lang/Thread;)V")) {
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpisws/util/concurrent/LockSupport",
                            "unpark",
                            "(Ljava/lang/Thread;)V",
                            false);
                      } else {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                      }
                    }
                  };
              return methodVisitor;
            }
          };
      ClassReader cr = new ClassReader(byteCode);
      cr.accept(classVisitor, 0);
      modifiedByteCode = cw.toByteArray();
      allByteCode.put(className, modifiedByteCode);
    }
  }

  public void modifyExecutors() {
    for (String className : allByteCode.keySet()) {
      byte[] byteCode = allByteCode.get(className);
      byte[] modifiedByteCode;
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
      ClassVisitor classVisitor =
          new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              MethodVisitor methodVisitor =
                  cv.visitMethod(access, name, descriptor, signature, exceptions);
              methodVisitor =
                  new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                    @Override
                    public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String name,
                        String descriptor,
                        boolean isInterface) {
                      if (opcode == Opcodes.INVOKESTATIC
                          && owner.equals("java/util/concurrent/Executors")
                          && name.equals("newFixedThreadPool")
                          && descriptor.equals(
                              "(ILjava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;")) {
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpisws/util/concurrent/Executors",
                            "newFixedThreadPool",
                            "(ILjava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;",
                            false);
                      } else if (opcode == Opcodes.INVOKESTATIC
                          && owner.equals("java/util/concurrent/Executors")
                          && name.equals("newFixedThreadPool")
                          && descriptor.equals("(I)Ljava/util/concurrent/ExecutorService;")) {
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpisws/util/concurrent/Executors",
                            "newFixedThreadPool",
                            "(I)Ljava/util/concurrent/ExecutorService;",
                            false);
                      } else {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                      }
                    }
                  };
              return methodVisitor;
            }
          };
      ClassReader cr = new ClassReader(byteCode);
      cr.accept(classVisitor, 0);
      modifiedByteCode = cw.toByteArray();
      allByteCode.put(className, modifiedByteCode);
    }
  }

  /**
   * Modifies the bytecode of the synchronized methods in the given class.
   *
   * <p>This method iteratively analyzes the bytecode of the methods in the given class and
   * identifies the synchronized methods. When a synchronized method is found, the method removes
   * the synchronized modifier and the method body. It then creates a new synchronized statement
   * that wraps a method call to the rewritten synchronized method ( methodName$SYNCHRONIZED ). For
   * this purpose, it needs to push all the required arguments onto the operand stack before calling
   * the rewritten synchronized method. Finally, it adds the proper return statement to the method.
   * <br>
   * Every synchronized method is modified to have the following structure: <br>
   * mv.visitTryCatchBlock(label0, label1, label2, null);<br>
   * mv.visitTryCatchBlock(label2, label3, label2, null);<br>
   * Get the monitor object and push it onto the operand stack<br>
   * mv.visitInsn(Opcodes.DUP);<br>
   * mv.visitVarInsn(Opcodes.ASTORE, nextVarIndex);<br>
   * mv.visitInsn(Opcodes.MONITORENTER);<br>
   * mv.visitLabel(label0);<br>
   * METHOD$SYNCHRONIZED<br>
   * mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null); No Need<br>
   * mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);<br>
   * mv.visitInsn(Opcodes.MONITOREXIT);<br>
   * mv.visitLabel(label1);<br>
   * mv.visitInsn(Opcodes.XRETURN); X is the return type of the method<br>
   * mv.visitLabel(label2);<br>
   * mv.visitVarInsn(Opcodes.ASTORE, nextVarIndex + 1);<br>
   * mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex);<br>
   * mv.visitInsn(Opcodes.MONITOREXIT);<br>
   * mv.visitLabel(label3);<br>
   * mv.visitVarInsn(Opcodes.ALOAD, nextVarIndex + 1);<br>
   * mv.visitInsn(Opcodes.ATHROW);<br>
   */
  public byte[] rewriteSyncMethod(byte[] byteCode) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    ClassVisitor classVisitor =
        new ClassVisitor(Opcodes.ASM9, cw) {

          String className;
          Boolean isInterface;
          int monitorIndex;

          @Override
          public void visit(
              int version,
              int access,
              String name,
              String signature,
              String superName,
              String[] interfaces) {
            className = name;
            isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            cv.visit(version, access, name, signature, superName, interfaces);
          }

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
              Boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
              MethodVisitor methodVisitor =
                  cv.visitMethod(
                      access & ~Opcodes.ACC_SYNCHRONIZED, name, descriptor, signature, exceptions);
              methodVisitor =
                  new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                    final Label label0 = new Label();
                    final Label label1 = new Label();
                    final Label label2 = new Label();
                    final Label label3 = new Label();
                    final Label label4 = new Label();
                    final Label label5 = new Label();
                    final Type[] argumentTypes = Type.getArgumentTypes(descriptor);
                    final int nextVarIndex = calculateLastIndexOfArguments(argumentTypes, isStatic);

                    @Override
                    public void visitCode() {
                      mv.visitTryCatchBlock(label0, label1, label2, null);
                      mv.visitTryCatchBlock(label2, label3, label2, null);
                      mv.visitLabel(label4);
                      if (isStatic) {
                        LOGGER.debug(
                            "Static Method : " + className + "." + name + " : " + descriptor);
                        mv.visitLdcInsn(className);
                        mv.visitLdcInsn(name);
                        mv.visitLdcInsn(descriptor);
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpisws/runtime/RuntimeEnvironment",
                            "getStaticMethodMonitor",
                            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lorg/mpisws/util/concurrent/StaticMethodMonitor;",
                            false);
                        mv.visitInsn(Opcodes.DUP);
                      } else {
                        LOGGER.debug(
                            "Instance Method : " + className + "." + name + " : " + descriptor);
                        mv.visitIntInsn(Opcodes.ALOAD, 0);
                        mv.visitLdcInsn(name);
                        mv.visitLdcInsn(descriptor);
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpisws/runtime/RuntimeEnvironment",
                            "getInstanceMethodMonitor",
                            "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Lorg/mpisws/util/concurrent/InstanceMethodMonitor;",
                            false);
                        mv.visitInsn(Opcodes.DUP);
                      }

                      mv.visitVarInsn(Opcodes.ASTORE, nextVarIndex);
                      monitorIndex = nextVarIndex;
                      mv.visitInsn(Opcodes.MONITORENTER);
                      mv.visitLabel(label0);

                      int localVariableIndex;
                      if (isStatic) {
                        localVariableIndex = 0;
                      } else {
                        mv.visitIntInsn(Opcodes.ALOAD, 0);
                        localVariableIndex = 1;
                      }
                      for (Type type : argumentTypes) {
                        switch (type.getSort()) {
                          case Type.INT:
                          case Type.BOOLEAN:
                          case Type.CHAR:
                          case Type.BYTE:
                          case Type.SHORT:
                            mv.visitVarInsn(Opcodes.ILOAD, localVariableIndex);
                            localVariableIndex += 1;
                            break;
                          case Type.LONG:
                            mv.visitVarInsn(Opcodes.LLOAD, localVariableIndex);
                            localVariableIndex += 2;
                            break;
                          case Type.FLOAT:
                            mv.visitVarInsn(Opcodes.FLOAD, localVariableIndex);
                            localVariableIndex += 1;
                            break;
                          case Type.DOUBLE:
                            mv.visitVarInsn(Opcodes.DLOAD, localVariableIndex);
                            localVariableIndex += 2;
                            break;
                          default:
                            mv.visitVarInsn(Opcodes.ALOAD, localVariableIndex);
                            localVariableIndex += 1;
                            break;
                        }
                      }

                      if (isStatic) {
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            className,
                            name + "$synchronized",
                            descriptor,
                            isInterface);
                      } else {
                        mv.visitMethodInsn(
                            Opcodes.INVOKEVIRTUAL,
                            className,
                            name + "$synchronized",
                            descriptor,
                            isInterface);
                      }
                      mv.visitIntInsn(Opcodes.ALOAD, monitorIndex);
                      mv.visitInsn(Opcodes.MONITOREXIT);
                      mv.visitLabel(label1);

                      // Find the return type of the method and add the corresponding return
                      // instruction
                      String returnType = descriptor.substring(descriptor.lastIndexOf(')') + 1);
                      switch (returnType) {
                        case "V":
                          mv.visitInsn(Opcodes.RETURN); // return for void methods
                          break;
                        case "D":
                          mv.visitInsn(Opcodes.DRETURN); // return for double
                          break;
                        case "F":
                          mv.visitInsn(Opcodes.FRETURN); // return for float
                          break;
                        case "J":
                          mv.visitInsn(Opcodes.LRETURN); // return for long
                          break;
                        case "I":
                        case "B":
                        case "C":
                        case "S":
                        case "Z":
                          mv.visitInsn(
                              Opcodes.IRETURN); // return for int, byte, char, short, boolean
                          break;
                        default:
                          mv.visitInsn(Opcodes.ARETURN); // return for object references
                          break;
                      }
                      mv.visitLabel(label2);

                      // TODO() : Remove the following code related to calling visitFrame
                      //                            Object[] localVariables = new
                      // Object[nextVarIndex];
                      //                            // Add the method owner as the first local
                      // variable
                      //                            localVariables[0] = className;
                      //                            localVariableIndex = (access &
                      // Opcodes.ACC_STATIC) == 0 ? 1 : 0; // Adjust for 'this' reference in
                      // non-static methods
                      //                            for (Type type : argumentTypes) {
                      //                                localVariables[localVariableIndex++] =
                      // type.getInternalName();
                      //                            }
                      //                            localVariables[nextVarIndex - 1] =
                      // "java/lang/Object";
                      //                            Object[] stack = new
                      // Object[]{"java/lang/Throwable"};
                      //                            mv.visitFrame(Opcodes.F_FULL,
                      // localVariables.length, localVariables, stack.length, stack);

                      mv.visitIntInsn(Opcodes.ASTORE, nextVarIndex + 1);
                      mv.visitIntInsn(Opcodes.ALOAD, monitorIndex);
                      mv.visitInsn(Opcodes.MONITOREXIT);
                      mv.visitLabel(label3);
                      mv.visitIntInsn(Opcodes.ALOAD, nextVarIndex + 1);
                      mv.visitInsn(Opcodes.ATHROW);
                      mv.visitLabel(label5);
                    }

                    @Override
                    public void visitTryCatchBlock(
                        Label start, Label end, Label handler, String type) {
                      // DO NOTHING
                    }

                    @Override
                    public void visitLocalVariable(
                        String name,
                        String descriptor,
                        String signature,
                        Label start,
                        Label end,
                        int index) {
                      // DO NOTHING
                    }
                  };
              return methodVisitor;
            } else {
              MethodVisitor methodVisitor =
                  cv.visitMethod(access, name, descriptor, signature, exceptions);
              return methodVisitor;
            }
          }
        };
    ClassReader cr = new ClassReader(byteCode);
    cr.accept(classVisitor, 0);
    return cw.toByteArray();
  }

  /**
   * Calculates the last index of the arguments in the local variable list.
   *
   * <p>This method calculates the last index of the arguments in the local variable list based on
   * the given argument types and whether the method is static or not. The method iterates over the
   * argument types and increments the index based on the type of the argument. If the argument type
   * is long or double, the index is incremented by 2, otherwise it is incremented by 1. If the
   * method is static, the index is initialized to 0, otherwise it is initialized to 1.
   *
   * @param argumentTypes The types of the arguments.
   * @param isStatic {@code true} if the method is static, {@code false} otherwise.
   * @return The last index of the arguments in the local variable list.
   */
  public int calculateLastIndexOfArguments(Type[] argumentTypes, boolean isStatic) {
    int nextVarIndex = 1;

    if (isStatic) {
      nextVarIndex = 0;
    }

    for (Type type : argumentTypes) {
      if (type.getSort() == Type.LONG || type.getSort() == Type.DOUBLE) {
        nextVarIndex += 2;
      } else {
        nextVarIndex++;
      }
    }
    return nextVarIndex;
  }

  /**
   * Identifies and modifies synchronized methods in the user's program.
   *
   * <p>This method iteratively analyzes the bytecode of the user's program, identifying methods
   * that are synchronized. When such a method is found, it visits the method and collects all the
   * instructions in the method. The method then creates a new method with the new name
   * (name$synchronized) and descriptor as the original method for each synchronized method. The new
   * method is then visited and the collected instructions are added to the new method. Finally, it
   * calls the {@link #rewriteSyncMethod(byte[])} method to rewrite the synchronized method itself.
   * <br>
   * The modifications made by this method enable the {@link org.mpisws.runtime.RuntimeEnvironment}
   * to manage and schedule synchronized methods during the execution of the user's program.
   */
  public void modifySyncMethod() {
    for (String className : allByteCode.keySet()) {
      byte[] byteCode = allByteCode.get(className);
      byte[] modifiedByteCode;
      ClassReader cr = new ClassReader(byteCode);
      ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);

      final Map<MethodInfo, ArrayList<LocalVariableNode>> localVariables = new HashMap<>();
      final List<MethodInfo> synchronizedMethods = new ArrayList<>();

      cr.accept(
          new ClassVisitor(Opcodes.ASM9, cw) {

            final Map<MethodInfo, ArrayList<AbstractInsnNode>> instructions = new HashMap<>();
            final Map<MethodInfo, ArrayList<AnnotationNode>> insnAnnotations = new HashMap<>();
            final Map<MethodInfo, ArrayList<TryCatchBlockNode>> tryCatchBlocks = new HashMap<>();
            final Map<MethodInfo, ArrayList<LineNumberNode>> lineNumbers = new HashMap<>();

            boolean isSyncMethod = false;

            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

              if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
                isSyncMethod = true;
                MethodInfo methodInfo =
                    new MethodInfo(access, name, descriptor, signature, exceptions);
                synchronizedMethods.add(methodInfo);
                instructions.put(methodInfo, new ArrayList<>());
                insnAnnotations.put(methodInfo, new ArrayList<>());
                tryCatchBlocks.put(methodInfo, new ArrayList<>());
                lineNumbers.put(methodInfo, new ArrayList<>());
                localVariables.put(methodInfo, new ArrayList<>());

                return new MethodVisitor(Opcodes.ASM9, mv) {

                  @Override
                  public void visitInsn(int opcode) {
                    instructions.get(methodInfo).add(new InsnNode(opcode));
                    super.visitInsn(opcode);
                  }

                  @Override
                  public void visitIntInsn(int opcode, int operand) {
                    instructions.get(methodInfo).add(new IntInsnNode(opcode, operand));
                    super.visitIntInsn(opcode, operand);
                  }

                  @Override
                  public void visitVarInsn(int opcode, int var) {
                    instructions.get(methodInfo).add(new VarInsnNode(opcode, var));
                    super.visitVarInsn(opcode, var);
                  }

                  @Override
                  public void visitTypeInsn(int opcode, String type) {
                    instructions.get(methodInfo).add(new TypeInsnNode(opcode, type));
                    super.visitTypeInsn(opcode, type);
                  }

                  @Override
                  public void visitFieldInsn(
                      int opcode, String owner, String name2, String descriptor) {
                    instructions
                        .get(methodInfo)
                        .add(new FieldInsnNode(opcode, owner, name2, descriptor));
                    super.visitFieldInsn(opcode, owner, name2, descriptor);
                  }

                  @Override
                  public void visitMethodInsn(
                      int opcode,
                      String owner,
                      String name2,
                      String descriptor,
                      boolean isInterface) {
                    instructions
                        .get(methodInfo)
                        .add(new MethodInsnNode(opcode, owner, name2, descriptor, isInterface));
                    super.visitMethodInsn(opcode, owner, name2, descriptor, isInterface);
                  }

                  @Override
                  public void visitInvokeDynamicInsn(
                      String name2,
                      String descriptor,
                      Handle bootstrapMethodHandle,
                      Object... bootstrapMethodArguments) {
                    instructions
                        .get(methodInfo)
                        .add(
                            new InvokeDynamicInsnNode(
                                name2,
                                descriptor,
                                bootstrapMethodHandle,
                                bootstrapMethodArguments));
                    super.visitInvokeDynamicInsn(
                        name2, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                  }

                  @Override
                  public void visitJumpInsn(int opcode, Label label) {
                    LabelNode labelNode = new LabelNode(label);
                    instructions.get(methodInfo).add(new JumpInsnNode(opcode, labelNode));
                    super.visitJumpInsn(opcode, label);
                  }

                  @Override
                  public void visitLabel(Label label) {
                    LabelNode labelNode = new LabelNode(label);
                    instructions.get(methodInfo).add(labelNode);
                    super.visitLabel(label);
                  }

                  @Override
                  public void visitLdcInsn(Object value) {
                    instructions.get(methodInfo).add(new LdcInsnNode(value));
                    super.visitLdcInsn(value);
                  }

                  @Override
                  public void visitIincInsn(int var, int increment) {
                    instructions.get(methodInfo).add(new IincInsnNode(var, increment));
                    super.visitIincInsn(var, increment);
                  }

                  @Override
                  public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                    LabelNode dfltNode = new LabelNode(dflt);
                    LabelNode[] labelNodes =
                        Arrays.stream(labels).map(LabelNode::new).toArray(LabelNode[]::new);
                    instructions
                        .get(methodInfo)
                        .add(new TableSwitchInsnNode(min, max, dfltNode, labelNodes));
                    super.visitTableSwitchInsn(min, max, dflt, labels);
                  }

                  @Override
                  public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                    LabelNode dfltNode = new LabelNode(dflt);
                    LabelNode[] labelNodes =
                        Arrays.stream(labels).map(LabelNode::new).toArray(LabelNode[]::new);
                    instructions
                        .get(methodInfo)
                        .add(new LookupSwitchInsnNode(dfltNode, keys, labelNodes));
                    super.visitLookupSwitchInsn(dflt, keys, labels);
                  }

                  @Override
                  public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                    instructions
                        .get(methodInfo)
                        .add(new MultiANewArrayInsnNode(descriptor, numDimensions));
                    super.visitMultiANewArrayInsn(descriptor, numDimensions);
                  }

                  @Override
                  public AnnotationVisitor visitInsnAnnotation(
                      int typeRef, TypePath typePath, String descriptor, boolean visible) {
                    insnAnnotations.get(methodInfo).add(new AnnotationNode(descriptor));
                    return super.visitInsnAnnotation(typeRef, typePath, descriptor, visible);
                  }

                  @Override
                  public void visitTryCatchBlock(
                      Label start, Label end, Label handler, String type) {
                    tryCatchBlocks
                        .get(methodInfo)
                        .add(
                            new TryCatchBlockNode(
                                new LabelNode(start),
                                new LabelNode(end),
                                new LabelNode(handler),
                                type));
                    super.visitTryCatchBlock(start, end, handler, type);
                  }

                  @Override
                  public AnnotationVisitor visitTryCatchAnnotation(
                      int typeRef, TypePath typePath, String descriptor, boolean visible) {
                    insnAnnotations.get(methodInfo).add(new AnnotationNode(descriptor));
                    return super.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible);
                  }

                  @Override
                  public void visitLocalVariable(
                      String name,
                      String descriptor,
                      String signature,
                      Label start,
                      Label end,
                      int index) {
                    localVariables
                        .get(methodInfo)
                        .add(
                            new LocalVariableNode(
                                name,
                                descriptor,
                                signature,
                                new LabelNode(start),
                                new LabelNode(end),
                                index));
                    super.visitLocalVariable(name, descriptor, signature, start, end, index);
                  }

                  @Override
                  public AnnotationVisitor visitLocalVariableAnnotation(
                      int typeRef,
                      TypePath typePath,
                      Label[] start,
                      Label[] end,
                      int[] index,
                      String descriptor,
                      boolean visible) {
                    insnAnnotations.get(methodInfo).add(new AnnotationNode(descriptor));
                    return super.visitLocalVariableAnnotation(
                        typeRef, typePath, start, end, index, descriptor, visible);
                  }

                  @Override
                  public void visitLineNumber(int line, Label start) {
                    lineNumbers.get(methodInfo).add(new LineNumberNode(line, new LabelNode(start)));
                    super.visitLineNumber(line, start);
                  }

                  // TODO() : Override other visit methods to capture different types of
                  // instructions
                };
              }
              return mv;
            }

            @Override
            public void visitEnd() {
              if (isSyncMethod) {
                isSyncMethod = false;
                for (MethodInfo methodInfo : synchronizedMethods) {
                  MethodVisitor newMv =
                      cv.visitMethod(
                          methodInfo.getNonSyncAccess(),
                          methodInfo.getSyncName(),
                          methodInfo.descriptor,
                          methodInfo.signature,
                          methodInfo.exceptions);
                  newMv.visitCode();

                  InsnList insnList = new InsnList();

                  for (AbstractInsnNode insn : instructions.get(methodInfo)) {
                    insnList.add(insn);
                  }

                  for (TryCatchBlockNode tryCatchBlock : tryCatchBlocks.get(methodInfo)) {
                    newMv.visitTryCatchBlock(
                        tryCatchBlock.start.getLabel(),
                        tryCatchBlock.end.getLabel(),
                        tryCatchBlock.handler.getLabel(),
                        tryCatchBlock.type);
                  }

                  for (LocalVariableNode localVariable : localVariables.get(methodInfo)) {
                    newMv.visitLocalVariable(
                        localVariable.name,
                        localVariable.desc,
                        localVariable.signature,
                        localVariable.start.getLabel(),
                        localVariable.end.getLabel(),
                        localVariable.index);
                  }

                  for (LineNumberNode lineNumber : lineNumbers.get(methodInfo)) {
                    newMv.visitLineNumber(lineNumber.line, lineNumber.start.getLabel());
                  }

                  insnList.accept(newMv);
                  newMv.visitInsn(Opcodes.RETURN);
                  newMv.visitMaxs(-1, -1); // Auto-compute stack size and locals
                  newMv.visitEnd();
                }
                super.visitEnd();
              } else {
                super.visitEnd();
              }
            }
          },
          0);
      modifiedByteCode = cw.toByteArray();

      modifiedByteCode = rewriteSyncMethod(modifiedByteCode);

      allByteCode.put(className, modifiedByteCode);
    }
  }

  /**
   * Identifies and modifies points in the user's program where assertions are made.
   *
   * <p>This method iteratively analyzes the bytecode of the user's program, identifying points
   * where assert statements are used. When such a point is found, it modifies the bytecode to
   * replace the call to the {@link AssertionError} constructor with a call to the {@link
   * org.mpisws.runtime.RuntimeEnvironment#assertOperation(String)} method. This allows the {@link
   * org.mpisws.runtime.SchedulerThread} to handle assertion failures during the execution of the
   * user's program. <br>
   * Additionally, the method replaces the throw instruction that follows the {@link AssertionError}
   * constructor call with a return instruction. This prevents the {@link AssertionError} from being
   * thrown and allows the program to continue executing. <br>
   * The method analyzes all methods of all classes in the {@link #allByteCode} map and makes the
   * necessary modifications to the corresponding methods. <br>
   * The modifications made by this method enable the {@link org.mpisws.runtime.SchedulerThread} to
   * manage and handle assertion failures during the execution of the user's program.
   */
  public void modifyAssert() {
    for (String className : allByteCode.keySet()) {
      byte[] byteCode = allByteCode.get(className);
      byte[] modifiedByteCode;
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
      ClassVisitor classVisitor =
          new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              MethodVisitor methodVisitor =
                  cv.visitMethod(access, name, descriptor, signature, exceptions);
              methodVisitor =
                  new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                    boolean replaced = false;

                    @Override
                    public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String name,
                        String descriptor,
                        boolean isInterface) {
                      if (opcode == Opcodes.INVOKESPECIAL
                          && owner.equals("java/lang/AssertionError")
                          && name.equals("<init>")) {
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpisws/runtime/RuntimeEnvironment",
                            "assertOperation",
                            "(Ljava/lang/String;)V",
                            false);
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
      allByteCode.put(className, modifiedByteCode);
    }
  }

  /**
   * Identifies and modifies points in the user's program where symbolic operations are evaluated.
   *
   * <p>This method iteratively analyzes the bytecode of the user's program, identifying points
   * where symbolic operations are evaluated using the {@link
   * org.mpisws.symbolic.SymbolicFormula#evaluate(SymbolicBoolean)} method. When such a point is
   * found, it modifies the bytecode to include calls to the {@link
   * org.mpisws.runtime.RuntimeEnvironment#waitRequest(Thread)} method immediately before the {@link
   * org.mpisws.symbolic.SymbolicFormula#evaluate(SymbolicBoolean)} method call. <br>
   * The modifications made by this method enable the {@link org.mpisws.runtime.SchedulerThread} to
   * manage and schedule threads based on the evaluation of symbolic operations during the execution
   * of the user's program.
   */
  public void modifySymbolicEval() {
    for (String className : allByteCode.keySet()) {
      LOGGER.debug(className);
      byte[] byteCode = allByteCode.get(className);
      byte[] modifiedByteCode;
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
      ClassVisitor classVisitor =
          new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              MethodVisitor methodVisitor =
                  cv.visitMethod(access, name, descriptor, signature, exceptions);
              methodVisitor =
                  new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                    // INVOKEVIRTUAL org/mpisws/symbolic/SymbolicFormula.evaluate
                    // (Lorg/mpisws/symbolic/SymbolicOperation;)Z
                    @Override
                    public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String name,
                        String descriptor,
                        boolean isInterface) {
                      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                      if (opcode == Opcodes.INVOKEVIRTUAL
                          && name.equals("evaluate")
                          && descriptor.equals("(Lorg/mpisws/symbolic/SymbolicOperation;)Z")
                          && owner.equals("org/mpisws/symbolic/SymbolicFormula")) {
                        // LOGGER.debug("opcode: " + opcode + ", owner: " + owner + ", name: " +
                        // name + ", descriptor: " + descriptor);
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "java/lang/Thread",
                            "currentThread",
                            "()Ljava/lang/Thread;",
                            false);
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "org/mpisws/runtime/RuntimeEnvironment",
                            "waitRequest",
                            "(Ljava/lang/Thread;)V",
                            false);
                        mv.visitInsn(Opcodes.NOP);
                      }
                    }
                  };
              return methodVisitor;
            }
          };
      ClassReader cr = new ClassReader(byteCode);
      cr.accept(classVisitor, 0);
      modifiedByteCode = cw.toByteArray();
      allByteCode.put(className, modifiedByteCode);
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
   * Identifies and modifies points in the user's program where monitor locks are acquired and
   * released.
   *
   * <p>This method iteratively analyzes the bytecode of the user's program, identifying points
   * where {@code MONITORENTER} and {@code MONITOREXIT} instructions are used to acquire and release
   * monitor locks, respectively. When such a point is found, it modifies the bytecode to include
   * calls to the {@link org.mpisws.runtime.RuntimeEnvironment#enterMonitor(Object, Thread)} and
   * {@link org.mpisws.runtime.RuntimeEnvironment#exitMonitor(Object, Thread)} methods,
   * respectively. These calls are inserted immediately before the {@code MONITORENTER} and {@code
   * MONITOREXIT} instructions. <br>
   * For {@code MONITORENTER} operations, the method adds the following instructions: <br>
   * 1. {@link org.mpisws.runtime.RuntimeEnvironment#enterMonitor(Object, Thread)} <br>
   * 2. {@link org.mpisws.runtime.RuntimeEnvironment#acquiredMonitor(Object, Thread)} <br>
   * For `MONITOREXIT` operations, the method adds the following instructions: <br>
   * 1. {@link org.mpisws.runtime.RuntimeEnvironment#exitMonitor(Object, Thread)} <br>
   * 2. {@link org.mpisws.runtime.RuntimeEnvironment#releasedMonitor(Object, Thread)} <br>
   * The method analyzes all methods of all classes in the {@link #allByteCode} map and adds the
   * instructions to the corresponding methods. <br>
   * The modifications made by this method enable the {@link org.mpisws.runtime.SchedulerThread} to
   * track all monitor lock acquisitions and releases during the execution of the user's program,
   * providing a mechanism for managing and scheduling threads based on their lock states.
   */
  public void modifyMonitorInstructions() {
    for (Map.Entry<String, byte[]> entry : allByteCode.entrySet()) {
      byte[] byteCode = entry.getValue();
      byte[] modifiedByteCode;

      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
      ClassReader cr = new ClassReader(byteCode);
      ClassVisitor cv =
          new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
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
                  if (opcode == Opcodes.MONITORENTER) {
                    // mv.visitVarInsn(Opcodes.ALOAD, var);
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Thread",
                        "currentThread",
                        "()Ljava/lang/Thread;",
                        false);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpisws/runtime/RuntimeEnvironment",
                        "enterMonitor",
                        "(Ljava/lang/Object;Ljava/lang/Thread;)V",
                        false);
                    super.visitInsn(opcode);
                    isASTORE = false;
                    foundMonitorEnter = true;
                  } else if (opcode == Opcodes.MONITOREXIT) {
                    // mv.visitVarInsn(Opcodes.ALOAD, var);
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Thread",
                        "currentThread",
                        "()Ljava/lang/Thread;",
                        false);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpisws/runtime/RuntimeEnvironment",
                        "exitMonitor",
                        "(Ljava/lang/Object;Ljava/lang/Thread;)V",
                        false);
                    super.visitInsn(opcode);
                    isALOAD = false;
                    foundMonitorExit = true;
                    oldvar = var;
                  } else {
                    super.visitInsn(opcode);
                  }
                }

                @Override
                public void visitLabel(Label label) {
                  super.visitLabel(label);
                  if (foundMonitorEnter) {
                    foundMonitorEnter = false;
                    // mv.visitVarInsn(Opcodes.ALOAD, var);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Thread",
                        "currentThread",
                        "()Ljava/lang/Thread;",
                        false);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpisws/runtime/RuntimeEnvironment",
                        "acquiredMonitor",
                        "(Ljava/lang/Object;Ljava/lang/Thread;)V",
                        false);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Thread",
                        "currentThread",
                        "()Ljava/lang/Thread;",
                        false);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpisws/runtime/RuntimeEnvironment",
                        "waitRequest",
                        "(Ljava/lang/Thread;)V",
                        false);
                  } else if (foundMonitorExit) {
                    foundMonitorExit = false;
                    // mv.visitVarInsn(Opcodes.ALOAD, oldvar);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Thread",
                        "currentThread",
                        "()Ljava/lang/Thread;",
                        false);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpisws/runtime/RuntimeEnvironment",
                        "releasedMonitor",
                        "(Ljava/lang/Object;Ljava/lang/Thread;)V",
                        false);
                  }
                }
                // TODO() : The following code is an alternative implementation of the visitLabel
                // method
                //                        @Override
                //                        public void visitLineNumber(int line, Label start) {
                //                            super.visitLineNumber(line, start);
                //                            if (foundMonitorEnter) {
                //                                foundMonitorEnter = false;
                //                                //mv.visitVarInsn(Opcodes.ALOAD, var);
                //                                mv.visitMethodInsn(
                //                                        Opcodes.INVOKESTATIC,
                //                                        "java/lang/Thread",
                //                                        "currentThread",
                //                                        "()Ljava/lang/Thread;",
                //                                        false
                //                                );
                //                                mv.visitMethodInsn(
                //                                        Opcodes.INVOKESTATIC,
                //                                        "org/mpisws/runtime/RuntimeEnvironment",
                //                                        "acquiredLock",
                //                                        "(Ljava/lang/Object;Ljava/lang/Thread;)V",
                //                                        false
                //                                );
                //                                mv.visitMethodInsn(
                //                                        Opcodes.INVOKESTATIC,
                //                                        "java/lang/Thread",
                //                                        "currentThread",
                //                                        "()Ljava/lang/Thread;",
                //                                        false
                //                                );
                //                                mv.visitMethodInsn(
                //                                        Opcodes.INVOKESTATIC,
                //                                        "org/mpisws/runtime/RuntimeEnvironment",
                //                                        "waitRequest",
                //                                        "(Ljava/lang/Thread;)V",
                //                                        false
                //                                );
                //                            } else if (foundMonitorExit) {
                //                                foundMonitorExit = false;
                //                                //mv.visitVarInsn(Opcodes.ALOAD, oldvar);
                //                                mv.visitMethodInsn(
                //                                        Opcodes.INVOKESTATIC,
                //                                        "java/lang/Thread",
                //                                        "currentThread",
                //                                        "()Ljava/lang/Thread;",
                //                                        false
                //                                );
                //                                mv.visitMethodInsn(
                //                                        Opcodes.INVOKESTATIC,
                //                                        "org/mpisws/runtime/RuntimeEnvironment",
                //                                        "releasedLock",
                //                                        "(Ljava/lang/Object;Ljava/lang/Thread;)V",
                //                                        false
                //                                );
                ////                                mv.visitMethodInsn(
                ////                                        Opcodes.INVOKESTATIC,
                ////                                        "java/lang/Thread",
                ////                                        "currentThread",
                ////                                        "()Ljava/lang/Thread;",
                ////                                        false
                ////                                );
                ////                                mv.visitMethodInsn(
                ////                                        Opcodes.INVOKESTATIC,
                ////                                        "org/mpisws/runtime/RuntimeEnvironment",
                ////                                        "waitRequest",
                ////                                        "(Ljava/lang/Thread;)V",
                ////                                        false
                ////                                );
                //                            }
                //                        }
              };
            }
          };
      cr.accept(cv, 0);
      modifiedByteCode = cw.toByteArray();
      allByteCode.put(entry.getKey(), modifiedByteCode);
    }
  }

  public void modifyReentrantLockStatements() {}

  public void modifyMonitorStatements() {
    for (Map.Entry<String, byte[]> entry : allByteCode.entrySet()) {
      byte[] byteCode = entry.getValue();
      byte[] modifiedByteCode;

      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
      ClassReader cr = new ClassReader(byteCode);
      ClassVisitor cv =
          new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
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
                  if (opcode == Opcodes.MONITORENTER) {
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Thread",
                        "currentThread",
                        "()Ljava/lang/Thread;",
                        false);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpisws/runtime/RuntimeEnvironment",
                        "acquireLockReq",
                        "(Ljava/lang/Object;Ljava/lang/Thread;)V",
                        false);
                    super.visitInsn(opcode);
                    isASTORE = false;
                    foundMonitorEnter = true;
                  } else if (opcode == Opcodes.MONITOREXIT) {
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Thread",
                        "currentThread",
                        "()Ljava/lang/Thread;",
                        false);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpisws/runtime/RuntimeEnvironment",
                        "releaseLockReq",
                        "(Ljava/lang/Object;Ljava/lang/Thread;)V",
                        false);
                    super.visitInsn(opcode);
                    isALOAD = false;
                    foundMonitorExit = true;
                    oldvar = var;
                  } else {
                    super.visitInsn(opcode);
                  }
                }

                @Override
                public void visitLabel(Label label) {
                  super.visitLabel(label);
                  if (foundMonitorEnter) {
                    foundMonitorEnter = false;
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Thread",
                        "currentThread",
                        "()Ljava/lang/Thread;",
                        false);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpisws/runtime/RuntimeEnvironment",
                        "acquiredLock",
                        "(Ljava/lang/Object;Ljava/lang/Thread;)V",
                        false);
                    //                                mv.visitMethodInsn(
                    //                                        Opcodes.INVOKESTATIC,
                    //                                        "java/lang/Thread",
                    //                                        "currentThread",
                    //                                        "()Ljava/lang/Thread;",
                    //                                        false
                    //                                );
                    //                                mv.visitMethodInsn(
                    //                                        Opcodes.INVOKESTATIC,
                    //
                    // "org/mpisws/runtime/RuntimeEnvironment",
                    //                                        "waitRequest",
                    //                                        "(Ljava/lang/Thread;)V",
                    //                                        false
                    //                                );
                  } else if (foundMonitorExit) {
                    foundMonitorExit = false;
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Thread",
                        "currentThread",
                        "()Ljava/lang/Thread;",
                        false);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpisws/runtime/RuntimeEnvironment",
                        "releasedLock",
                        "(Ljava/lang/Object;Ljava/lang/Thread;)V",
                        false);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Thread",
                        "currentThread",
                        "()Ljava/lang/Thread;",
                        false);
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/mpisws/runtime/RuntimeEnvironment",
                        "waitRequest",
                        "(Ljava/lang/Thread;)V",
                        false);
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
