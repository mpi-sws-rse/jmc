package org.mpisws.jmc.agent;

import org.mpisws.jmc.agent.visitors.JmcReadWriteVisitor;
import org.mpisws.jmc.agent.visitors.JmcReentrantLockVisitor;
import org.mpisws.jmc.agent.visitors.JmcThreadVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.security.ProtectionDomain;
import java.util.Arrays;

public class PremainInstrumentor implements ClassFileTransformer {
    private final AgentArgs agentArgs;
    private final JmcMatcher matcher;

    public PremainInstrumentor(AgentArgs agentArgs) {
        this.agentArgs = agentArgs;
        this.matcher = new JmcMatcher(agentArgs.getInstrumentingPackages());
    }

    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classFileBuffer) {
        String finalClassName = className.replace("/", ".");
        byte[] copiedClassBuffer = Arrays.copyOf(classFileBuffer, classFileBuffer.length);
        if (!this.matcher.matches(finalClassName, loader)) {
            return copiedClassBuffer;
        }
        ClassReader cr = new ClassReader(copiedClassBuffer);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        ClassVisitor cv =
                new JmcReentrantLockVisitor(
                        new JmcThreadVisitor.ThreadClassVisitor(
                                new JmcThreadVisitor.ThreadCallReplacerClassVisitor(
                                        new JmcReadWriteVisitor.ReadWriteClassVisitor(cw))));
        cr.accept(cv, 0);
        if (this.agentArgs.isDebug()) {
            byte[] transformed = cw.toByteArray();
            record(className, transformed);
        }
        return cw.toByteArray();
    }

    public void record(String className, byte[] classFileBuffer) {
        String outputDir = this.agentArgs.getDebugSavePath();
        File outFile = new File(outputDir + "/" + className + ".class");
        try {
            outFile.getParentFile().mkdirs();
            Files.write(outFile.toPath(), classFileBuffer);
        } catch (Exception e) {
            System.out.println("Error writing to file: " + outFile.getAbsolutePath() + " " + e);
        }
    }
}
