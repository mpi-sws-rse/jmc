package org.mpisws.jmc.agent;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import org.mpisws.jmc.agent.visitors.JmcThreadVisitor;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.security.ProtectionDomain;

public class Instrumentor implements ClassFileTransformer {
    private AgentArgs agentArgs;
    private JmcMatcher matcher;

    public Instrumentor(AgentArgs agentArgs) {
        this.agentArgs = agentArgs;
        this.matcher = new JmcMatcher(agentArgs.getInstrumentingPackages());
    }

    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        if (!matcher.matches(className, loader, null, classBeingRedefined, protectionDomain)) {
            return classfileBuffer;
        }
        System.out.println("Transforming " + className);
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        ClassVisitor visitor =
                new JmcThreadVisitor.ThreadClassVisitor(
                        new JmcThreadVisitor.ThreadCallReplacerClassVisitor(cw));
        cr.accept(visitor, 0);
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
