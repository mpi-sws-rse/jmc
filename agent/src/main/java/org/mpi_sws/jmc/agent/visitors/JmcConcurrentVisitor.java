package org.mpi_sws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JmcConcurrentVisitor extends ClassVisitor {
    private static final Logger LOGGER = LogManager.getLogger(JmcConcurrentVisitor.class);
    private String className;
    private final List<JmcConcurrentDetector> methodDetectors = new ArrayList<>();
    private final Set<String> unsupported = new HashSet<>();

    public JmcConcurrentVisitor(int api, ClassVisitor cv) {
        super(api, cv);
    }

    public JmcConcurrentVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
    }

    @Override
    public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces) {
        this.className = name.replace('/', '.');
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        JmcConcurrentDetector detector =
                new JmcConcurrentDetector(Opcodes.ASM9, mv, className, name);
        methodDetectors.add(detector);
        return detector;
    }

    public Set<String> getAllDetectedFeatures() {
        Set<String> all = new HashSet<>();
        for (JmcConcurrentDetector detector : methodDetectors) {
            all.addAll(detector.getDetectedFeatures());
        }
        return all;
    }

    public boolean usesUnsupportedFeatures(Set<String> supportedFeatures) {
        unsupported.clear();
        for (String feature : getAllDetectedFeatures()) {
            if (feature.startsWith("java.util.concurrent")
                    && !supportedFeatures.contains(feature)) {
                unsupported.add(feature);
                return true;
            }
        }
        return false;
    }

    public Set<String> getUnsupportedFeatures() {
        LOGGER.info("Unsupported feature {}", unsupported);
        return unsupported;
    }
}
