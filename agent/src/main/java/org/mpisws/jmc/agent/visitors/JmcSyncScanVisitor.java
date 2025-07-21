package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class JmcSyncScanVisitor extends ClassVisitor {
    private final JmcSyncScanData jmcSyncScanData;

    public JmcSyncScanVisitor(ClassVisitor cv, JmcSyncScanData jmcSyncScanData) {
        super(Opcodes.ASM9, cv);
        this.jmcSyncScanData = jmcSyncScanData;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
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

    private static class JmcSyncScanMethodVisitor extends MethodVisitor {
        private final JmcSyncScanData jmcSyncScanData;

        public JmcSyncScanMethodVisitor(MethodVisitor mv, JmcSyncScanData jmcSyncScanData) {
            super(Opcodes.ASM9, mv);
            this.jmcSyncScanData = jmcSyncScanData;
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.MONITORENTER || opcode == Opcodes.MONITOREXIT) {
                this.jmcSyncScanData.setHasSyncBlocks(true);
            }
            super.visitInsn(opcode);
        }
    }
}
