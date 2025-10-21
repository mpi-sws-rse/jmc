package org.mpi_sws.jmc.agent.visitors;

import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.objectweb.asm.MethodVisitor;

import java.util.HashSet;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JmcConcurrentDetector  extends MethodVisitor {
    private static final Logger LOGGER = LogManager.getLogger(JmcConcurrentDetector.class);
    private final Set<String> detectedFeatures = new HashSet<>();
    private final String className;
    private final String methodName;

    public JmcConcurrentDetector(int api, MethodVisitor mv, String className, String methodName) {
        super(api, mv);
        this.className = className;
        this.methodName = methodName;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface){
        if (owner.startsWith("java/util/concurrent")) {
            String feature = owner.replace("/", ".") + "." + name;
            detectedFeatures.add(feature);
            //System.out.println("Detected concurrent feature: " + feature);
        }

        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    public Set<String> getDetectedFeatures() {
        //System.out.println("Detected features are "+ detectedFeatures);
        LOGGER.info("Detected features: " + detectedFeatures);
        return detectedFeatures;
    }

}
