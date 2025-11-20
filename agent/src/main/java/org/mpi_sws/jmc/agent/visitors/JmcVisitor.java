package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

/** The encapsulating visitor that applies all the other visitors in the correct order. */
public class JmcVisitor {

    /**
     * The main method that applies all the visitors in the correct order.
     *
     * @param classFileBuffer the input class file as a byte array
     * @return the transformed class file as a byte array
     */
    public static byte[] transform(byte[] classFileBuffer) {
        ClassReader syncCr = new ClassReader(classFileBuffer);
        ClassWriter syncCw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        JmcSyncScanData syncScanData = new JmcSyncScanData();
        JmcSyncScanVisitor syncScanVisitor = new JmcSyncScanVisitor(syncCw, syncScanData);
        syncCr.accept(syncScanVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);


        ClassReader enumCr = new ClassReader(classFileBuffer);
        ClassWriter enumCw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);


        JmcIgnoreEnumVisitor enumVisitor = new JmcIgnoreEnumVisitor(enumCw);
        enumCr.accept(enumVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);


        if (enumVisitor.isEnum()) {
            return classFileBuffer;
        }


        ClassReader cr = new ClassReader(classFileBuffer);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        ClassVisitor cv =
                new JmcWaitNotifyVisitor(
                        new JmcStaticMethodVisitor(
                                new JmcSyncMethodVisitor(
                                        //new JmcFutureVisitor.JmcCompletableFutureVisitor(
                                        new JmcFutureVisitor.JmcFutureTaskClassVisitor(
                                        new JmcFutureVisitor.JmcExecutorsClassVisitor(
                                                new JmcAtomicVisitor(
                                                        new JmcReentrantLockVisitor(
                                                                //new JmcWaitNotifyVisitor(
                                                                        new JmcThreadVisitor
                                                                                .ThreadClassVisitor(
                                                                                new JmcThreadVisitor
                                                                                        .ThreadCallReplacerClassVisitor(
                                                                                        new JmcReadWriteVisitor
                                                                                                .ReadWriteClassVisitor(
                                                                                                cw))))))),
                                        syncScanData)));
        try{
            cr.accept(cv, 0);
            } catch (Exception e){
            System.out.println("Exception in JmcVisitor" + e.getMessage());
        }
        return cw.toByteArray();
    }
}
