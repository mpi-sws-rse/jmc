package org.mpisws.jmc.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

import java.io.File;

/** Listener that saves transformed classes to disk. */
public class DebugListener implements AgentBuilder.Listener {

    private final File outputDir;

    /**
     * Creates a new DebugListener.
     *
     * @param outputDir the directory to save transformed classes to
     */
    public DebugListener(String outputDir) {
        this.outputDir = new File(outputDir);
        // Create the output directory if it does not exist.
        this.outputDir.mkdirs();
    }

    @Override
    public void onDiscovery(String s, ClassLoader classLoader, JavaModule javaModule, boolean b) {}

    @Override
    public void onTransformation(
            TypeDescription typeDescription,
            ClassLoader classLoader,
            JavaModule javaModule,
            boolean b,
            DynamicType dynamicType) {
        try {
            dynamicType.saveIn(outputDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onIgnored(
            TypeDescription typeDescription,
            ClassLoader classLoader,
            JavaModule javaModule,
            boolean b) {
    }

    @Override
    public void onError(
            String s,
            ClassLoader classLoader,
            JavaModule javaModule,
            boolean b,
            Throwable throwable) {
        System.out.println("Error transforming: " + s + " " + throwable);
        throwable.printStackTrace();
    }

    @Override
    public void onComplete(String s, ClassLoader classLoader, JavaModule javaModule, boolean b) {
    }
}
