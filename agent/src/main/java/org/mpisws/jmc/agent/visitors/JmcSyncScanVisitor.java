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
        return super.visitMethod(access, name, desc, signature, exceptions);
    }
}
