package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Read-only pre-scan visitor that detects synchronization constructs in a class.
 *
 * <p>This is the Phase-1 pass of {@link JmcVisitor#transform}. It does not modify the class; it only
 * records, into a shared {@link JmcSyncScanData}, whether the class contains synchronized instance
 * methods, synchronized static methods, and/or synchronized blocks. {@link JmcSyncMethodVisitor}
 * later reads those flags to decide how to instrument synchronization.
 */
public class JmcSyncScanVisitor extends ClassVisitor {
    /** The scan result populated as the class is visited. */
    private final JmcSyncScanData jmcSyncScanData;

    /**
     * Creates a scan visitor writing into the given result object.
     *
     * @param cv the downstream {@link ClassVisitor} to delegate to
     * @param jmcSyncScanData the result object whose flags this visitor sets
     */
    public JmcSyncScanVisitor(ClassVisitor cv, JmcSyncScanData jmcSyncScanData) {
        super(Opcodes.ASM9, cv);
        this.jmcSyncScanData = jmcSyncScanData;
    }

    /**
     * Records synchronized methods and returns a per-method visitor that detects synchronized blocks.
     *
     * <p>If the method's access flags include {@code ACC_SYNCHRONIZED}, the corresponding flag is set
     * on {@link #jmcSyncScanData} — {@code hasSyncStaticMethods} for static methods, otherwise
     * {@code hasSyncMethods}. The returned {@link JmcSyncScanMethodVisitor} then inspects the method
     * body for {@code MONITORENTER}/{@code MONITOREXIT}.
     *
     * @param access the method access flags
     * @param name the method name
     * @param desc the method descriptor
     * @param signature the generic signature, or {@code null}
     * @param exceptions the declared exceptions, or {@code null}
     * @return a {@link MethodVisitor} that scans the body for synchronized blocks
     */
    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            if ((access & Opcodes.ACC_STATIC) != 0) {
                this.jmcSyncScanData.setHasSyncStaticMethods(true);
            } else {
                this.jmcSyncScanData.setHasSyncMethods(true);
            }
        }
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        return new JmcSyncScanMethodVisitor(mv, jmcSyncScanData);
    }

    /**
     * Per-method visitor that detects synchronized blocks by watching for monitor instructions.
     */
    private static class JmcSyncScanMethodVisitor extends MethodVisitor {
        /** The shared scan result whose {@code hasSyncBlocks} flag this visitor may set. */
        private final JmcSyncScanData jmcSyncScanData;

        /**
         * @param mv the downstream {@link MethodVisitor} to delegate to
         * @param jmcSyncScanData the shared scan result to update
         */
        public JmcSyncScanMethodVisitor(MethodVisitor mv, JmcSyncScanData jmcSyncScanData) {
            super(Opcodes.ASM9, mv);
            this.jmcSyncScanData = jmcSyncScanData;
        }

        /**
         * Sets {@code hasSyncBlocks} when a {@code MONITORENTER} or {@code MONITOREXIT} instruction —
         * the bytecode markers of a synchronized block — is seen, then forwards the instruction.
         *
         * @param opcode the instruction opcode
         */
        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.MONITORENTER || opcode == Opcodes.MONITOREXIT) {
                this.jmcSyncScanData.setHasSyncBlocks(true);
            }
            super.visitInsn(opcode);
        }
    }
}
