package org.mpisws.jmc.agent.visitors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class JmcSynchronizedVisitor extends ClassVisitor {
    /**
     * Constructor.
     *
     * @param cv The underlying ClassVisitor
     */
    public JmcSynchronizedVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    // Additional methods for handling synchronized blocks can be added here
}
