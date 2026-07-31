package org.mpi_sws.jmc.agent.visitors;

import org.mpi_sws.jmc.checker.exceptions.JmcUnsupportedFeatureException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

/**
 * Orchestrator of the JMC instrumentation pipeline.
 *
 * <p>{@link org.mpi_sws.jmc.agent.PremainInstrumentor#transform} delegates the actual bytecode rewriting to this class's
 * {@link #transform} method, which drives the class bytes through every JMC visitor in the correct
 * order. It is the single place that defines how the individual visitors are composed.
 *
 * <p>The pipeline has three phases: a pre-scan that gathers information the later passes need, a set
 * of early opt-outs that abort instrumentation for classes JMC must not rewrite, and the main chain
 * of chained {@link org.objectweb.asm.ClassVisitor}s that performs the transformation.
 */
public class JmcVisitor {

    /**
     * Applies the whole JMC instrumentation pipeline to a single class.
     *
     * <p>Phases, in order:
     *
     * <ol>
     *   <li><b>Pre-scan.</b> {@link JmcSyncScanVisitor} records into a {@link JmcSyncScanData} whether
     *       the class has synchronized instance methods, synchronized static methods, and/or
     *       synchronized blocks. {@link JmcSyncMethodVisitor} needs this before it starts rewriting.
     *   <li><b>Early opt-outs.</b> If {@link JmcIgnoreEnumVisitor} reports the class is an {@code enum},
     *       or {@link JmcIgnoreFinalizerVisitor} reports it declares a {@code protected void
     *       finalize()}, the original {@code classFileBuffer} is returned unchanged.
     *   <li><b>Transformation chain.</b> A single {@link org.objectweb.asm.ClassReader#accept} drives
     *       the bytes through the chained visitors (outermost first): {@link JmcWaitNotifyVisitor} →
     *       {@link JmcStaticMethodVisitor} → {@link JmcSyncMethodVisitor} → {@code
     *       JmcScheduledExecutorClassVisitor} → {@code JmcFutureTaskClassVisitor} → {@code
     *       JmcExecutorsClassVisitor} → {@link JmcAtomicVisitor} → {@link JmcReentrantLockVisitor} →
     *       {@code ThreadClassVisitor} → {@code ThreadCallReplacerClassVisitor} → {@link
     *       JmcNativeMethodVisitor} → {@code ReadWriteClassVisitor} → the {@link
     *       org.objectweb.asm.ClassWriter} that emits the result.
     * </ol>
     *
     * <p>A {@link JmcUnsupportedFeatureException} thrown during the chain is propagated unchanged; any
     * other exception is wrapped in a {@link RuntimeException}.
     *
     * @param classFileBuffer the input class file as a byte array
     * @return the transformed class file as a byte array, or the original bytes if the class is an
     *     enum or declares a finalizer
     */
    public static byte[] transform(byte[] classFileBuffer) {
        // Phase 1: pre-scan for synchronized methods/blocks (consumed by JmcSyncMethodVisitor).
        ClassReader syncCr = new ClassReader(classFileBuffer);
        ClassWriter syncCw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        JmcSyncScanData syncScanData = new JmcSyncScanData();
        JmcSyncScanVisitor syncScanVisitor = new JmcSyncScanVisitor(syncCw, syncScanData);
        syncCr.accept(syncScanVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);


        // Phase 2: early opt-outs. Enums and classes with finalizers are returned unchanged.
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


        // Phase 3: the main transformation chain (outermost visitor listed first).
        ClassReader cr = new ClassReader(classFileBuffer);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
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
                                                                                                        new JmcReadWriteVisitor
                                                                                                                .ReadWriteClassVisitor(
                                                                                                                cw))))))))),
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
