package org.mpi_sws.jmc.agent.visitors;

import org.mpi_sws.jmc.agent.InstrumentationLevel;
import org.mpi_sws.jmc.checker.exceptions.JmcUnsupportedFeatureException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

/** The encapsulating visitor that applies all the other visitors in the correct order. */
public class JmcVisitor {

    /**
     * The main method that applies all the visitors in the correct order.
     * Uses FULL instrumentation level (default behavior).
     *
     * @param classFileBuffer the input class file as a byte array
     * @return the transformed class file as a byte array
     */
    public static byte[] transform(byte[] classFileBuffer) {
        return transform(classFileBuffer, InstrumentationLevel.FULL);
    }

    /**
     * The main method that applies all the visitors in the correct order,
     * respecting the given instrumentation level.
     *
     * @param classFileBuffer the input class file as a byte array
     * @param level the instrumentation level controlling which visitors are applied
     * @return the transformed class file as a byte array
     */
    public static byte[] transform(byte[] classFileBuffer, InstrumentationLevel level) {
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

        ClassReader finalizerCr = new ClassReader(classFileBuffer);
        ClassWriter finalizerCw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        JmcIgnoreFinalizerVisitor finalizerVisitor = new JmcIgnoreFinalizerVisitor(finalizerCw);
        finalizerCr.accept(finalizerVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        if (finalizerVisitor.hasFinalizer()) {
            return classFileBuffer;
        }


        ClassReader cr = new ClassReader(classFileBuffer);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        // Build the visitor chain. When SYNC_POINTS_ONLY, skip ReadWriteVisitor
        // so that plain field accesses don't get yield points.
        ClassVisitor innermost;
        if (level == InstrumentationLevel.SYNC_POINTS_ONLY) {
            // Skip JmcReadWriteVisitor — no field read/write instrumentation
            innermost = cw;
        } else {
            innermost = new JmcReadWriteVisitor.ReadWriteClassVisitor(cw);
        }

        ClassVisitor cv =
                new JmcWaitNotifyVisitor(
                        new JmcStaticMethodVisitor(
                                new JmcSyncMethodVisitor(
                                        new JmcScheduledExecutorVisitor.JmcScheduledExecutorClassVisitor(
                                        new JmcFutureVisitor.JmcFutureTaskClassVisitor(
                                        new JmcFutureVisitor.JmcExecutorsClassVisitor(
                                                new JmcAtomicVisitor(
                                                        new JmcReentrantLockVisitor(
                                                                        new JmcThreadVisitor
                                                                                .ThreadClassVisitor(
                                                                                new JmcThreadVisitor
                                                                                        .ThreadCallReplacerClassVisitor(
                                                                                                    new JmcNativeMethodVisitor(
                                                                                                        innermost)))))))),
                                        syncScanData)));
        try{
            cr.accept(cv, 0);
            } catch (Exception e){
            if (e instanceof JmcUnsupportedFeatureException) {
                throw (JmcUnsupportedFeatureException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
        return cw.toByteArray();
    }
}
