package org.mpisws.jmc.agent;

import org.mpisws.jmc.agent.visitors.JmcReadWriteVisitor;
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
    private AgentArgs agentArgs;
    private JmcMatcher matcher;

    public PremainInstrumentor(AgentArgs agentArgs) {
        this.agentArgs = agentArgs;
        this.matcher = new JmcMatcher(agentArgs.getInstrumentingPackages());
    }

    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        String finalClassName = className.replace("/", ".");
        byte[] copiedClassBuffer = Arrays.copyOf(classfileBuffer, classfileBuffer.length);
        if (!this.matcher.matches(finalClassName, loader)) {
            return copiedClassBuffer;
        }
        System.out.println("Transforming class: " + finalClassName);
        ClassReader cr = new ClassReader(copiedClassBuffer);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv =
                new JmcThreadVisitor.ThreadCallReplacerClassVisitor(
                        new JmcThreadVisitor.ThreadClassVisitor(
                                new JmcReadWriteVisitor.ReadWriteClassVisitor(cw)));
        cr.accept(cv, 0);
        if (this.agentArgs.isDebug()) {
            byte[] transformed = cw.toByteArray();
            record(className, transformed);
        }
        return cw.toByteArray();
    }

    public void record(String className, byte[] classfileBuffer) {
        String outputDir = this.agentArgs.getDebugSavePath();
        File outFile = new File(outputDir + "/" + className + ".class");
        try {
            outFile.getParentFile().mkdirs();
            Files.write(outFile.toPath(), classfileBuffer);
        } catch (Exception e) {
            System.out.println("Error writing to file: " + outFile.getAbsolutePath() + " " + e);
        }
    }
}
