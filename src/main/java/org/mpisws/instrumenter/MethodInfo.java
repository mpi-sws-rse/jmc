package org.mpisws.instrumenter;

import org.objectweb.asm.Opcodes;

/**
 * The MethodInfo class is used to store information about a method.
 */
public class MethodInfo {

    /**
     * @property {@link #access} is the access flags of the method.
     */
    public int access;

    /**
     * @property {@link #name} is the name of the method.
     */
    public String name;

    /**
     * @property {@link #descriptor} is the descriptor of the method.
     */
    public String descriptor;

    /**
     * @property {@link #signature} is the signature of the method.
     */
    public String signature;

    /**
     * @property {@link #exceptions} is the exceptions of the method.
     */
    public String[] exceptions;

    public MethodInfo(int access, String name, String descriptor, String signature, String[] exceptions) {
        this.access = access;
        this.name = name;
        this.descriptor = descriptor;
        this.signature = signature;
        this.exceptions = exceptions;
    }

    /**
     * Changes the access flags of the method to be non-synchronized.
     */
    public int getNonSyncAccess() {
        return access & ~Opcodes.ACC_SYNCHRONIZED;
    }

    /**
     * Changes the name of the method to have a suffix of "$synchronized".
     */
    public String getSyncName() {
        return name + "$synchronized";
    }
}
